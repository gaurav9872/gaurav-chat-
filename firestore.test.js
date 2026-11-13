const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";
const HASH_ALICE = "a".repeat(64);
const HASH_BOB = "b".repeat(64);

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

test("Unauthenticated user cannot read users, chats, or calls", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("calls").where("participantIds", "array-contains", ALICE_UID).get());
});

test("User can create own public profile and private PII doc, but cannot spoof or inject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      emailHash: HASH_ALICE,
      photoUrl: "",
      bio: "Hey there!",
      publicKey: "pub_key_1",
      isOnline: true,
      isDiscoverable: true,
      lastSeen: new Date(),
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      emailHash: HASH_ALICE,
      isOnline: true,
      isDiscoverable: true,
      isAdmin: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Private PII doc allowed for owner, denied for Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      fcmToken: "token123",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));
});

test("Authenticated user can query discoverable users only with matching filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await aliceDb.collection("users").doc(ALICE_UID).set({
    uid: ALICE_UID,
    displayName: "Alice",
    handle: "alice",
    emailHash: HASH_ALICE,
    isOnline: true,
    isDiscoverable: true,
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Blanket unfiltered list fails when non-discoverable could exist
  await assertFails(bobDb.collection("users").get());
  // Matching discoverable query succeeds
  await assertSucceeds(bobDb.collection("users").where("isDiscoverable", "==", true).get());
});

test("1-on-1 Chat & Messages: participants allowed, third party blocked, status moves forward only, secret deletes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;

  // Reading a non-existent chat doc before creation (createOrGetChat existence check) succeeds
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).get());

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPhotos: { [ALICE_UID]: "", [BOB_UID]: "" },
      lastMessageText: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageStatus: "sent",
      lastMessageAt: new Date(),
      secretModeEnabled: false,
      typingUids: [],
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Charlie cannot read or query Alice & Bob's chat
  await assertFails(charlieDb.collection("chats").doc(chatId).get());
  await assertFails(charlieDb.collection("chats").get());

  // Alice sends normal message
  const msgRef = aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      text: "Hello Bob!",
      isSecret: false,
      ciphertext: "",
      iv: "",
      status: "sent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob marks delivered then read on message
  const bobMsgRef = bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1");
  await assertSucceeds(bobMsgRef.update({ status: "delivered", updatedAt: new Date() }));
  await assertSucceeds(bobMsgRef.update({ status: "read", updatedAt: new Date() }));
  // Cannot revert message status from read back to sent or delivered
  await assertFails(bobMsgRef.update({ status: "sent", updatedAt: new Date() }));
  await assertFails(bobMsgRef.update({ status: "delivered", updatedAt: new Date() }));

  // Bob marks delivered then read on chat thread lastMessageStatus (forward-only)
  const bobChatRef = bobDb.collection("chats").doc(chatId);
  await assertSucceeds(
    bobChatRef.update({
      lastMessageStatus: "delivered",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobChatRef.update({
      lastMessageStatus: "read",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      updatedAt: new Date(),
    })
  );
  // Cannot regress chat thread lastMessageStatus from read back to delivered
  await assertFails(
    bobChatRef.update({
      lastMessageStatus: "delivered",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      updatedAt: new Date(),
    })
  );

  // Alice sends secret encrypted message; Bob can delete it immediately upon viewing
  const secretMsgRef = aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_secret_1");
  await assertSucceeds(
    secretMsgRef.set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      text: "",
      isSecret: true,
      ciphertext: "YWJjZGVm",
      iv: "MTIzNDU2Nzg5MDEy",
      status: "sent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_secret_1").delete());

  // Alice sends an image attachment with empty caption (allowed)
  const imgMsgRef = aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_img_1");
  await assertSucceeds(
    imgMsgRef.set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      text: "",
      isSecret: false,
      ciphertext: "",
      iv: "",
      attachmentUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/photo.jpg",
      attachmentType: "image",
      attachmentName: "photo.jpg",
      attachmentSizeBytes: 102400,
      attachmentMimeType: "image/jpeg",
      status: "sent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Reject invalid attachmentType or empty text without attachment
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_bad_attach").set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      text: "",
      isSecret: false,
      ciphertext: "",
      iv: "",
      attachmentUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/exe",
      attachmentType: "executable",
      attachmentName: "malware.exe",
      attachmentSizeBytes: 1024,
      attachmentMimeType: "application/octet-stream",
      status: "sent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_empty").set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      text: "",
      isSecret: false,
      ciphertext: "",
      iv: "",
      status: "sent",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Voice Call signaling: participants transition ringing -> connected -> ended, terminal state locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const callId = "call_123";

  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).set({
      callerId: ALICE_UID,
      callerName: "Alice",
      callerPhotoUrl: "",
      calleeId: BOB_UID,
      calleeName: "Bob",
      calleePhotoUrl: "",
      participantIds: [ALICE_UID, BOB_UID],
      status: "ringing",
      offerSdp: "v=0...",
      answerSdp: "",
      durationSeconds: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Charlie cannot read call
  await assertFails(charlieDb.collection("calls").doc(callId).get());

  // Bob answers
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      answerSdp: "v=0 answer...",
      status: "connected",
      updatedAt: new Date(),
    })
  );

  // Alice ends call
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: new Date(),
    })
  );

  // Terminal state locked: cannot reopen ended call
  await assertFails(
    bobDb.collection("calls").doc(callId).update({
      status: "connected",
      updatedAt: new Date(),
    })
  );
});
