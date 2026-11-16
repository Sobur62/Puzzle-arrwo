const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastDate = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const VALID_UUID_V4 = "550e8400-e29b-41d4-a716-446655440000";
const INVALID_NON_UUID = "alice_123";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid) {
  const now = Date.now() - 1000;
  return {
    schemaVersion: 1,
    userId: uid,
    createdAt: now,
    lastActiveAt: now,
    displayName: "Player",
    classicLevel: 1,
    classicHighestCleared: 0,
    fallStage: 1,
    fallHighScore: 0,
    selectedSkinId: "green_dots",
    unlockedSkins: ["green_dots", "classic_ink", "googly_noodles", "matchsticks", "caterpillars", "prism_arrows", "reindeer", "santa", "pine_tree"],
    lives: 5,
    lastLivesRefillEpochMs: now,
    hintsRemaining: 3,
    sfxEnabled: true,
    bgmEnabled: false,
    vibrationEnabled: true,
    darkMode: false,
    totalArrowsCleared: 0,
    coins: 50,
    redeemedPromos: [],
    totalSessions: 1,
    totalPlayTimeSeconds: 0,
    rewardedAdsWatched: 0,
    interstitialAdsWatched: 0,
    lastRewardedAdAtMs: 0,
    lastInterstitialAdAtMs: 0,
    updatedAt: pastDate(),
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

test("Collection listing on /users is denied to prevent enumeration", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(db.collection("users").doc(VALID_UUID_V4).set(validProfilePayload(VALID_UUID_V4)));
  await assertFails(db.collection("users").get());
});

test("Non-UUID v4 document IDs are rejected", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(INVALID_NON_UUID).get());
  await assertFails(db.collection("users").doc(INVALID_NON_UUID).set(validProfilePayload(INVALID_NON_UUID)));
});

test("Valid UUID v4 PlayerProfile can be created, read, and updated cleanly", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(db.collection("users").doc(VALID_UUID_V4).set(validProfilePayload(VALID_UUID_V4)));
  await assertSucceeds(db.collection("users").doc(VALID_UUID_V4).get());

  await assertSucceeds(
    db.collection("users").doc(VALID_UUID_V4).update({
      classicLevel: 2,
      classicHighestCleared: 1,
      totalArrowsCleared: 18,
      coins: 150,
      redeemedPromos: ["PIXEL100"],
      rewardedAdsWatched: 1,
      lastRewardedAdAtMs: Date.now(),
      lastActiveAt: Date.now(),
      updatedAt: pastDate(),
    })
  );
});

test("Immutable fields (userId, createdAt) and delete are rejected", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(db.collection("users").doc(VALID_UUID_V4).set(validProfilePayload(VALID_UUID_V4)));

  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).update({
      createdAt: 123,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).update({
      userId: "660e8400-e29b-41d4-a716-446655440001",
      updatedAt: pastDate(),
    })
  );
  await assertFails(db.collection("users").doc(VALID_UUID_V4).delete());
});

test("Shadow update with ghost field or out-of-bounds lives/coins is rejected", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(db.collection("users").doc(VALID_UUID_V4).set(validProfilePayload(VALID_UUID_V4)));

  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).update({
      classicLevel: 2,
      isAdmin: true,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).update({
      lives: 99,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).update({
      coins: -10,
      updatedAt: pastDate(),
    })
  );
});

test("UUID v4 user can create and list level_history but cannot update/delete", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(db.collection("users").doc(VALID_UUID_V4).set(validProfilePayload(VALID_UUID_V4)));

  await assertSucceeds(
    db.collection("users").doc(VALID_UUID_V4).collection("level_history").doc("rec_1").set({
      userId: VALID_UUID_V4,
      mode: "classic",
      levelNumber: 1,
      stars: 3,
      mistakes: 0,
      durationSeconds: 12,
      arrowsCleared: 18,
      createdAt: pastDate(),
    })
  );

  await assertSucceeds(
    db.collection("users").doc(VALID_UUID_V4).collection("level_history").get()
  );

  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).collection("level_history").doc("rec_1").update({ stars: 1 })
  );
  await assertFails(
    db.collection("users").doc(VALID_UUID_V4).collection("level_history").doc("rec_1").delete()
  );
});
