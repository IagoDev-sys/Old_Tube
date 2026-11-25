const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write profiles, channels, or library", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("library").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("channels").doc("UCKHhA5hN2UohhFDfNXB_cvQ").set({
      userId: ALICE_UID,
      channelId: "UCKHhA5hN2UohhFDfNXB_cvQ",
      name: "Manual do Mundo",
      category: "Ciência",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create and partially update profile; rejects ghost fields and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Iago",
      email: "iago@example.com",
      preferredQuality: "SD_480P",
      preferredEngine: "HYBRID_AUTO",
      classicLightTheme: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredQuality: "ECO_360P",
      classicLightTheme: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Owner can manage channels and library records; cross-user access is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("channels").doc("UCKHhA5hN2UohhFDfNXB_cvQ").set({
      userId: ALICE_UID,
      channelId: "UCKHhA5hN2UohhFDfNXB_cvQ",
      name: "Manual do Mundo",
      category: "Ciência",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("library").doc("dQw4w9WgXcQ").set({
      userId: ALICE_UID,
      videoId: "dQw4w9WgXcQ",
      title: "Video Teste",
      channelName: "Canal Teste",
      channelId: "UC123",
      thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
      durationText: "3:32",
      publishedAt: "25/11/2026",
      bookmarked: true,
      lastPlayedAtMs: 1700000000000,
      lastPositionMs: 15000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // List queries succeed for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("library").get());

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("channels").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("library").doc("dQw4w9WgXcQ").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("library").doc("dQw4w9WgXcQ").update({
      bookmarked: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' leakage in writes
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("library").doc("abc12345678").set({
      id: "abc12345678",
      userId: ALICE_UID,
      videoId: "abc12345678",
      title: "Bad Payload",
      channelName: "Canal",
      thumbnailUrl: "https://i.ytimg.com/vi/abc12345678/mqdefault.jpg",
      bookmarked: false,
      lastPlayedAtMs: 0,
      lastPositionMs: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
