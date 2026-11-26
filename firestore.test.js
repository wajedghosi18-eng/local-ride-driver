const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DRIVER_A = "driver_alice_101";
const DRIVER_B = "driver_bob_202";
const PASSENGER_1 = "passenger_rita_303";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDriverPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    email: `${uid}@gmail.com`,
    language: "bn",
    vehicleType: "toto",
    district: "Paschim Medinipur",
    village: "Chandra",
    fullName: "Subrata Jana",
    mobileNumber: "9876543210",
    totoColor: "Yellow",
    totoColorHex: "#FBC02D",
    vehicleNumber: "WB-34-T-4421",
    emergencyContact: "9123456789",
    passportPhotoBase64: "dGVzdF9wYXNzcG9ydA==",
    aadhaarFrontBase64: "dGVzdF9hYWRoYWFyX2Zyb250",
    aadhaarBackBase64: "dGVzdF9hYWRoYWFyX2JhY2s=",
    status: "pending_or_active",
    isOnline: false,
    lat: 22.4257,
    lng: 87.3199,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("toto_drivers").doc(DRIVER_A).get());
  await assertFails(
    unauthDb.collection("toto_drivers").doc(DRIVER_A).set(validDriverPayload(DRIVER_A))
  );
  await assertFails(
    unauthDb.collection("toto_rides").where("status", "==", "pending").get()
  );
  await assertFails(
    unauthDb.collection("app_settings").doc("driver_app_version").get()
  );
});

test("Driver profile CRUD, online toggle, and cross-user PII isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(DRIVER_A).firestore();
  const bobDb = testEnv.authenticatedContext(DRIVER_B).firestore();

  // Create own profile succeeds
  await assertSucceeds(
    aliceDb.collection("toto_drivers").doc(DRIVER_A).set(validDriverPayload(DRIVER_A))
  );

  // Read own profile succeeds
  await assertSucceeds(aliceDb.collection("toto_drivers").doc(DRIVER_A).get());

  // Online toggle & GPS update succeeds
  await assertSucceeds(
    aliceDb.collection("toto_drivers").doc(DRIVER_A).update({
      isOnline: true,
      lat: 22.4301,
      lng: 87.3212,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Profile edit succeeds
  await assertSucceeds(
    aliceDb.collection("toto_drivers").doc(DRIVER_A).update({
      village: "Midnapore Town",
      totoColor: "Green",
      totoColorHex: "#2E7D32",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Cross-user read/write/delete denied (PII protection)
  await assertFails(bobDb.collection("toto_drivers").doc(DRIVER_A).get());
  await assertFails(
    bobDb.collection("toto_drivers").doc(DRIVER_A).update({
      isOnline: false,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(bobDb.collection("toto_drivers").doc(DRIVER_A).delete());

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("toto_drivers").doc(DRIVER_A).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject @DocumentId 'id' injection & future timestamps
  await assertFails(
    bobDb.collection("toto_drivers").doc(DRIVER_B).set({
      ...validDriverPayload(DRIVER_B),
      id: DRIVER_B,
    })
  );
  await assertFails(
    bobDb.collection("toto_drivers").doc(DRIVER_B).set(
      validDriverPayload(DRIVER_B, new Date(Date.now() + 60000))
    )
  );

  // Owner can delete own profile
  await assertSucceeds(aliceDb.collection("toto_drivers").doc(DRIVER_A).delete());
});

test("Ride queue queries, accept/reject/complete lifecycle, and terminal lock", async () => {
  const passengerDb = testEnv.authenticatedContext(PASSENGER_1).firestore();
  const aliceDb = testEnv.authenticatedContext(DRIVER_A).firestore();
  const bobDb = testEnv.authenticatedContext(DRIVER_B).firestore();
  const now = new Date(Date.now() - 5000);

  // Passenger creates pending ride (no pricing/fare fields)
  await assertSucceeds(
    passengerDb.collection("toto_rides").doc("ride_101").set({
      passengerId: PASSENGER_1,
      passengerName: "Ananya Roy",
      pickupVillage: "Chandra",
      destination: "Midnapore Station",
      pickupLat: 22.4257,
      pickupLng: 87.3199,
      status: "pending",
      driverId: "",
      vehicleType: "toto",
      district: "Paschim Medinipur",
      rejectedBy: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered pending query succeeds; unfiltered query fails
  await assertSucceeds(
    aliceDb.collection("toto_rides").where("status", "==", "pending").get()
  );
  await assertFails(aliceDb.collection("toto_rides").get());

  // Driver B rejects pending ride (adds own UID to rejectedBy)
  await assertSucceeds(
    bobDb.collection("toto_rides").doc("ride_101").update({
      rejectedBy: [DRIVER_B],
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Driver A accepts pending ride
  await assertSucceeds(
    aliceDb.collection("toto_rides").doc("ride_101").update({
      status: "accepted",
      driverId: DRIVER_A,
      driverName: "Subrata Jana",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Driver A's My Rides query succeeds
  await assertSucceeds(
    aliceDb.collection("toto_rides").where("driverId", "==", DRIVER_A).get()
  );

  // Driver B cannot steal or modify Driver A's accepted ride
  await assertFails(
    bobDb.collection("toto_rides").doc("ride_101").update({
      status: "accepted",
      driverId: DRIVER_B,
      driverName: "Bob",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Driver A completes accepted ride
  await assertSucceeds(
    aliceDb.collection("toto_rides").doc("ride_101").update({
      status: "completed",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Terminal state lock: completed ride cannot be modified again
  await assertFails(
    aliceDb.collection("toto_rides").doc("ride_101").update({
      status: "cancelled",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("App version settings are readable by signed-in drivers and immutable from client", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("app_settings").doc("driver_app_version").set({
      latestVersionCode: 2,
      latestVersionName: "1.1.0",
      apkDownloadUrl: "https://github.com/example/local-ride-driver/releases/latest/download/app-debug.apk",
      forceUpdate: false,
      releaseNotes: "Improved GPS accuracy in Paschim Medinipur",
    });
  });

  const aliceDb = testEnv.authenticatedContext(DRIVER_A).firestore();
  await assertSucceeds(aliceDb.collection("app_settings").doc("driver_app_version").get());
  await assertFails(
    aliceDb.collection("app_settings").doc("driver_app_version").update({ latestVersionCode: 99 })
  );
  await assertFails(aliceDb.collection("app_settings").get());
});
