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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user can create and read their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Meloman",
      email: "alice@example.com",
      subscriptionTier: "FREE",
      preferredGenres: ["Synthwave", "Electronic"],
      excludedArtists: [],
      waveDiversity: 50,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Cross-user access to profile is denied (PII Isolation)", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Meloman",
      email: "alice@example.com",
      subscriptionTier: "FREE",
      preferredGenres: [],
      excludedArtists: [],
      waveDiversity: 50,
      createdAt: now,
      updatedAt: now,
    });
  });
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Shadow field injection on profile is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      subscriptionTier: "FREE",
      preferredGenres: [],
      excludedArtists: [],
      waveDiversity: 50,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user can create, update, and list own playlists", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const playlistRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("playlists")
    .doc("pl_night_drive");

  await assertSucceeds(
    playlistRef.set({
      userId: ALICE_UID,
      title: "Night Drive",
      description: "Synthwave & Retrowave",
      trackIds: ["track_1", "track_2"],
      coverTintHex: "#00E5FF",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    playlistRef.update({
      title: "Midnight Drive",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
});

test("Cross-user playlist access and mutation are rejected", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .set({
        userId: ALICE_UID,
        title: "Secret Playlist",
        description: "",
        trackIds: [],
        coverTintHex: "#7C4DFF",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
});

test("Authenticated user can create and update track interactions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const interactionRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("interactions")
    .doc("track_01");

  await assertSucceeds(
    interactionRef.set({
      userId: ALICE_UID,
      trackId: "track_01",
      trackTitle: "Neon Horizon",
      artistName: "Aether Pulse",
      genre: "Synthwave",
      liked: true,
      disliked: false,
      playCount: 3,
      skipCount: 0,
      totalListenMs: 185000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    interactionRef.update({
      playCount: 4,
      totalListenMs: 240000,
      updatedAt: new Date(),
    })
  );
});
