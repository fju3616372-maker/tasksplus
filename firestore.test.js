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

function validTaskPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId,
    title: "Complete daily review",
    subtext: "Check pending priorities",
    scheduledAtMillis: 1762900000000,
    maxCompletionMillis: 1762903600000,
    reminderIntervalMinutes: 30,
    recurrenceType: "DAILY",
    recurrenceInterval: 1,
    recurrenceDaysCsv: "",
    isCompleted: false,
    completedAtMillis: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validPrefPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId,
    themeMode: "SYSTEM",
    useDynamicColor: true,
    tonalSpotPalette: "INDIGO",
    defaultReminderMinutes: 30,
    notificationsEnabled: true,
    autoDeleteUpdateApk: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validScreenTimePayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId,
    packageName: "com.google.android.youtube",
    appName: "YouTube",
    limitMinutes: 45,
    usedMinutes: 15,
    periodType: "DAILY",
    isEnabled: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated user cannot read or list tasks or screentime", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("screentime").get());
});

test("Authenticated user can create, read, list, update, and delete own task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");

  const initialPayload = validTaskPayload(ALICE_UID);
  await assertSucceeds(taskRef.set(initialPayload));
  await assertSucceeds(taskRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  await assertSucceeds(
    taskRef.set({
      ...initialPayload,
      isCompleted: true,
      completedAtMillis: 1762901000000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(taskRef.delete());
});

test("Authenticated user cannot access another user's tasks or screentime", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("tasks")
      .doc("bob_task")
      .set(validTaskPayload(BOB_UID));
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("screentime")
      .doc("com_google_android_youtube")
      .set(validScreenTimePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("screentime").doc("com_google_android_youtube").get()
  );
});

test("Shadow update with ghost field or spoofed userId is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");

  await assertFails(
    taskRef.set({
      ...validTaskPayload(ALICE_UID),
      isVerified: true,
    })
  );

  await assertFails(
    taskRef.set(validTaskPayload(BOB_UID))
  );
});

test("Authenticated user can create and update own settings and screen time limits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");
  const initialPref = validPrefPayload(ALICE_UID);
  await assertSucceeds(prefRef.set(initialPref));
  await assertSucceeds(prefRef.get());

  const stRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("screentime")
    .doc("com_google_android_youtube");
  const initialSt = validScreenTimePayload(ALICE_UID);
  await assertSucceeds(stRef.set(initialSt));
  await assertSucceeds(stRef.get());
  await assertSucceeds(
    stRef.set({
      ...initialSt,
      limitMinutes: 30,
      usedMinutes: 30,
      periodType: "HOURLY",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(stRef.delete());
});

function validGroupPayload(managerUid, managerEmail, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    name: "Smith Family",
    groupType: "FAMILY",
    managerUid,
    managerEmail,
    managerName: "Alice",
    memberUids: [managerUid],
    adminUids: [managerUid],
    invitedEmails: [],
    invitedAdminEmails: [],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validGroupInvitePayload(groupId, senderUid, targetEmail, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    groupId,
    groupName: "Smith Family",
    groupType: "FAMILY",
    senderUid,
    senderName: "Alice",
    senderEmail: "alice@example.com",
    targetEmail,
    proposedRole: "CHILD",
    groupAdminUids: [senderUid],
    status: "PENDING",
    acceptedByUid: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validGroupMemberPayload(groupId, uid, email, role = "MANAGER", canManage = true, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    groupId,
    uid,
    email,
    displayName: "Member",
    role,
    canManageOthers: canManage,
    remoteDeviceLocked: false,
    remoteLockReason: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Group lifecycle: manager creates group, invites verified member, member accepts, child cannot unlock self or tamper limits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const spoofedBobDb = testEnv.authenticatedContext("spoof_bob", {
    email: "bob@example.com",
    email_verified: false,
  }).firestore();
  const outsiderDb = testEnv.authenticatedContext("charlie_789", {
    email: "charlie@example.com",
    email_verified: true,
  }).firestore();

  const groupId = "grp_family_1";
  const groupRefAlice = aliceDb.collection("groups").doc(groupId);
  const now = new Date(Date.now() - 1000);

  // 1. Alice creates group & her manager member doc
  await assertSucceeds(groupRefAlice.set(validGroupPayload(ALICE_UID, "alice@example.com")));
  await assertSucceeds(
    groupRefAlice.collection("members").doc(ALICE_UID).set(
      validGroupMemberPayload(groupId, ALICE_UID, "alice@example.com", "MANAGER", true)
    )
  );

  // 2. Alice queries her groups (list)
  await assertSucceeds(aliceDb.collection("groups").where("memberUids", "array-contains", ALICE_UID).get());

  // 3. Outsider cannot get or list Alice's group
  await assertFails(outsiderDb.collection("groups").doc(groupId).get());
  await assertFails(outsiderDb.collection("groups").get());

  // 4. Alice invites Bob as CHILD
  await assertSucceeds(
    groupRefAlice.update({
      invitedEmails: ["bob@example.com"],
      updatedAt: now,
    })
  );
  const inviteRefAlice = aliceDb.collection("groupInvites").doc("inv_1");
  await assertSucceeds(inviteRefAlice.set(validGroupInvitePayload(groupId, ALICE_UID, "bob@example.com")));

  // 5. Unverified spoofed Bob cannot read group or invites targeting bob@example.com
  await assertFails(
    spoofedBobDb.collection("groupInvites").where("targetEmail", "==", "bob@example.com").get()
  );
  await assertFails(spoofedBobDb.collection("groups").doc(groupId).get());

  // 6. Verified Bob queries invites for his email and accepts
  await assertSucceeds(
    bobDb.collection("groupInvites").where("targetEmail", "==", "bob@example.com").get()
  );
  await assertSucceeds(bobDb.collection("groups").doc(groupId).get());

  // Bob CANNOT elevate himself into adminUids when accepting CHILD invite
  await assertFails(
    bobDb.collection("groups").doc(groupId).update({
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID, BOB_UID],
      invitedEmails: [],
      invitedAdminEmails: [],
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Verified Bob accepts legitimately as non-admin member
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).update({
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      invitedEmails: [],
      invitedAdminEmails: [],
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).set(
      validGroupMemberPayload(groupId, BOB_UID, "bob@example.com", "CHILD", false)
    )
  );
  await assertSucceeds(
    bobDb.collection("groupInvites").doc("inv_1").update({
      status: "ACCEPTED",
      acceptedByUid: BOB_UID,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // 7. Alice locks Bob's device & assigns a task-gated limit
  await assertSucceeds(
    groupRefAlice.collection("members").doc(BOB_UID).update({
      remoteDeviceLocked: true,
      remoteLockReason: "Bedtime lock",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Child Bob CAN sync his own installed device apps (appName + packageName) so Manager can pick them
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      installedApps: [
        { packageName: "com.google.android.youtube", appName: "YouTube" },
        { packageName: "com.whatsapp", appName: "WhatsApp" },
      ],
      appsLastSyncedAtMillis: 1762902000000,
      updatedAt: new Date(Date.now() - 150),
    })
  );

  // Child Bob CANNOT unlock his own device or escalate role while syncing apps
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      remoteDeviceLocked: false,
      remoteLockReason: "",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      role: "PARENT_ADMIN",
      canManageOthers: true,
      updatedAt: new Date(Date.now() - 90),
    })
  );

  // Alice sets exact-time lock + task instructions + beep alert requiring task completion; child Bob cannot remove it without completing task
  const limitRefAlice = groupRefAlice.collection("memberScreenTime").doc("st_bob_yt");
  await assertSucceeds(
    limitRefAlice.set({
      groupId,
      targetUid: BOB_UID,
      targetMemberName: "Bob",
      packageName: "com.google.android.youtube",
      appName: "YouTube",
      limitMinutes: 1,
      usedMinutes: 0,
      periodType: "DAILY",
      lockMode: "LOCK_AT_TIME",
      lockAtMillis: 1762905000000,
      taskInstructions: "Finish math worksheet and submit photo proof.",
      playBeepOnLock: true,
      requireTaskIdToUnlock: "gtask_1",
      requireTaskTitle: "Clean desk",
      enforcedByUid: ALICE_UID,
      enforcedByName: "Alice",
      isEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("groups").doc(groupId).collection("memberScreenTime").doc("st_bob_yt").delete());

  // 8. Credit system rules & ledger tests
  // Manager Alice can configure optional credit rules (e.g., 2 pts/sec early, 1 pt/sec late, toggle off/on)
  await assertSucceeds(
    groupRefAlice.update({
      creditSystemEnabled: true,
      earlyPointsPerSecond: 2,
      latePenaltyPerSecond: 1.5,
      creditGracePeriodSeconds: 10,
      maxCreditPerTask: 5000,
      maxPenaltyPerTask: 2000,
      allowNegativeCredits: true,
      updatedAt: new Date(Date.now() - 50),
    })
  );

  // Child Bob CANNOT tamper with group credit rules
  await assertFails(
    bobDb.collection("groups").doc(groupId).update({
      creditSystemEnabled: false,
      latePenaltyPerSecond: 0,
      updatedAt: new Date(Date.now() - 40),
    })
  );

  // Child Bob CANNOT forge a manual bonus transaction (taskId == '')
  const forgedTxRef = bobDb.collection("groups").doc(groupId).collection("creditTransactions").doc("tx_forge");
  await assertFails(
    forgedTxRef.set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "",
      taskTitle: "Manual Bonus",
      pointsDelta: 500,
      secondsDelta: 0,
      reason: "Fake bonus",
      awardedByUid: BOB_UID,
      awardedByName: "Bob",
      createdAt: new Date(Date.now() - 30),
      updatedAt: new Date(Date.now() - 30),
    })
  );

  // Child Bob CAN log his own task-completion credit transaction and update his own creditBalance
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      creditBalance: 120,
      totalEarnedCredits: 120,
      totalDeductedCredits: 0,
      updatedAt: new Date(Date.now() - 25),
    })
  );
  const taskTxRef = bobDb.collection("groups").doc(groupId).collection("creditTransactions").doc("tx_bob_task1");
  await assertSucceeds(
    taskTxRef.set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "gtask_1",
      taskTitle: "Clean desk",
      pointsDelta: 120,
      secondsDelta: 120,
      reason: "Completed 2m before deadline",
      awardedByUid: BOB_UID,
      awardedByName: "Bob",
      createdAt: new Date(Date.now() - 20),
      updatedAt: new Date(Date.now() - 20),
    })
  );

  // Credit ledger entries are immutable (updates rejected)
  await assertFails(taskTxRef.update({ pointsDelta: 9999 }));

  // Manager Alice can award a manual bonus to Bob
  const adminBonusRef = groupRefAlice.collection("creditTransactions").doc("tx_alice_bonus");
  await assertSucceeds(
    adminBonusRef.set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "",
      taskTitle: "Manual Adjustment",
      pointsDelta: 250,
      secondsDelta: 0,
      reason: "Great attitude this week",
      awardedByUid: ALICE_UID,
      awardedByName: "Alice",
      createdAt: new Date(Date.now() - 10),
      updatedAt: new Date(Date.now() - 10),
    })
  );

  // Child Bob CAN equip a free profile photo icon without deducting points
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      activeProfileIcon: "roster_solar_titan",
      unlockedProfileIcons: ["default_spark", "roster_solar_titan"],
      updatedAt: new Date(Date.now() - 5),
    })
  );

  // Child Bob CANNOT forge a positive credit gain using an icon_purchase_* or perk_* transaction ID
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("creditTransactions").doc("tx_bob_icon_forge").set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "icon_purchase_bolt_turbo",
      taskTitle: "Profile Icon: Turbo Bolt",
      pointsDelta: 500,
      secondsDelta: 0,
      reason: "Forged icon refund",
      awardedByUid: BOB_UID,
      awardedByName: "Bob",
      createdAt: new Date(Date.now() - 2),
      updatedAt: new Date(Date.now() - 2),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("creditTransactions").doc("tx_bob_perk_forge").set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "perk_bonus",
      taskTitle: "Perk Refund",
      pointsDelta: 500,
      secondsDelta: 0,
      reason: "Forged perk refund",
      awardedByUid: BOB_UID,
      awardedByName: "Bob",
      createdAt: new Date(Date.now() - 1),
      updatedAt: new Date(Date.now() - 1),
    })
  );
});

test("Group points governance & manager-assigned perks: only main manager configures rules or perks, and assigned member redeems when points enabled", async () => {
  const COADMIN_UID = "co_admin_456";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const coadminDb = testEnv.authenticatedContext(COADMIN_UID, {
    email: "coadmin@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  const groupId = "grp_perks_1";
  const now = new Date(Date.now() - 2000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("groups").doc(groupId).set({
      name: "Work Team",
      groupType: "ORGANIZATION",
      managerUid: ALICE_UID,
      managerEmail: "alice@example.com",
      managerName: "Alice",
      memberUids: [ALICE_UID, COADMIN_UID, BOB_UID],
      adminUids: [ALICE_UID, COADMIN_UID],
      invitedEmails: [],
      invitedAdminEmails: [],
      creditSystemEnabled: false,
      earlyPointsPerSecond: 1,
      latePenaltyPerSecond: 1,
      creditGracePeriodSeconds: 0,
      maxCreditPerTask: 0,
      maxPenaltyPerTask: 0,
      allowNegativeCredits: true,
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("groups").doc(groupId).collection("members").doc(ALICE_UID).set(
      validGroupMemberPayload(groupId, ALICE_UID, "alice@example.com", "MANAGER", true, { displayName: "Alice" })
    );
    await adminDb.collection("groups").doc(groupId).collection("members").doc(COADMIN_UID).set(
      validGroupMemberPayload(groupId, COADMIN_UID, "coadmin@example.com", "PARENT_ADMIN", true, { displayName: "CoAdmin" })
    );
    await adminDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).set(
      validGroupMemberPayload(groupId, BOB_UID, "bob@example.com", "MEMBER", false, {
        displayName: "Bob",
        creditBalance: 300,
        totalEarnedCredits: 300,
      })
    );
  });

  const groupRef = aliceDb.collection("groups").doc(groupId);

  // Co-admin CANNOT enable group points or change credit rules
  await assertFails(
    coadminDb.collection("groups").doc(groupId).update({
      creditSystemEnabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Co-admin CANNOT issue manual point adjustments to Bob or write manual credit transactions
  await assertFails(
    coadminDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      creditBalance: 900,
      updatedAt: new Date(Date.now() - 900),
    })
  );
  await assertFails(
    coadminDb.collection("groups").doc(groupId).collection("creditTransactions").doc("tx_coadmin_bonus").set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "",
      taskTitle: "Manual Adjustment",
      pointsDelta: 200,
      secondsDelta: 0,
      reason: "Unauthorized bonus",
      awardedByUid: COADMIN_UID,
      awardedByName: "CoAdmin",
      createdAt: new Date(Date.now() - 800),
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Co-admin CANNOT assign perks
  const perkPayloadForBob = {
    groupId,
    managerUid: ALICE_UID,
    assignedToUid: BOB_UID,
    assignedToName: "Bob",
    assignedByName: "Alice",
    title: "Friday Half-Day Off",
    description: "Leave early on Friday afternoon",
    pointsCost: 150,
    perkStatus: "ASSIGNED",
    redeemedCount: 0,
    redeemedAtMillis: 0,
    createdAt: new Date(Date.now() - 700),
    updatedAt: new Date(Date.now() - 700),
  };
  await assertFails(
    coadminDb.collection("groups").doc(groupId).collection("perks").doc("perk_coadmin").set({
      ...perkPayloadForBob,
      managerUid: COADMIN_UID,
      assignedByName: "CoAdmin",
    })
  );

  // Main manager Alice CANNOT assign a perk to herself or to an outsider
  await assertFails(
    groupRef.collection("perks").doc("perk_self").set({
      ...perkPayloadForBob,
      assignedToUid: ALICE_UID,
      assignedToName: "Alice",
    })
  );
  await assertFails(
    groupRef.collection("perks").doc("perk_outsider").set({
      ...perkPayloadForBob,
      assignedToUid: "outsider_999",
      assignedToName: "Outsider",
    })
  );

  // Main manager Alice CAN assign a perk to Bob
  const bobPerkRef = groupRef.collection("perks").doc("perk_friday");
  await assertSucceeds(bobPerkRef.set(perkPayloadForBob));

  // Bob CANNOT redeem while group creditSystemEnabled == false
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("perks").doc("perk_friday").update({
      perkStatus: "REDEEMED",
      redeemedCount: 1,
      redeemedAtMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Main manager Alice enables optional group points
  await assertSucceeds(
    groupRef.update({
      creditSystemEnabled: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Co-admin CANNOT redeem Bob's perk
  await assertFails(
    coadminDb.collection("groups").doc(groupId).collection("perks").doc("perk_friday").update({
      perkStatus: "REDEEMED",
      redeemedCount: 1,
      redeemedAtMillis: Date.now() - 350,
      updatedAt: new Date(Date.now() - 350),
    })
  );

  // Bob CANNOT rewrite perk pointsCost or title while redeeming
  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("perks").doc("perk_friday").update({
      pointsCost: 0,
      perkStatus: "REDEEMED",
      redeemedCount: 1,
      redeemedAtMillis: Date.now() - 300,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Bob CAN redeem his assigned perk when points are enabled (and deduct points + log negative perk_* tx)
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("perks").doc("perk_friday").update({
      perkStatus: "REDEEMED",
      redeemedCount: 1,
      redeemedAtMillis: Date.now() - 250,
      updatedAt: new Date(Date.now() - 250),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("members").doc(BOB_UID).update({
      creditBalance: 150,
      totalDeductedCredits: 150,
      updatedAt: new Date(Date.now() - 250),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("creditTransactions").doc("tx_perk_friday").set({
      userId: BOB_UID,
      userName: "Bob",
      groupId,
      taskId: "perk_perk_friday",
      taskTitle: "Perk: Friday Half-Day Off",
      pointsDelta: -150,
      secondsDelta: 0,
      reason: "Redeemed group perk: Friday Half-Day Off",
      awardedByUid: BOB_UID,
      awardedByName: "Bob",
      createdAt: new Date(Date.now() - 250),
      updatedAt: new Date(Date.now() - 250),
    })
  );

  // Main manager Alice can reset perk to ASSIGNED or mark GRANTED or delete it; Co-admin cannot delete
  await assertFails(coadminDb.collection("groups").doc(groupId).collection("perks").doc("perk_friday").delete());
  await assertSucceeds(
    bobPerkRef.update({
      perkStatus: "GRANTED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertSucceeds(bobPerkRef.delete());
});

test("Personal profile photo icon preferences & settings allow owner and block outsiders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");
  await assertSucceeds(
    prefRef.set(
      validPrefPayload(ALICE_UID, {
        creditSystemEnabled: false,
        personalCredits: 0,
        activeProfileIcon: "roster_solar_titan",
        unlockedProfileIcons: ["default_spark", "roster_solar_titan"],
      })
    )
  );

  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("creditTransactions").doc("ctx_1");
  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      userName: "Alice",
      groupId: "",
      taskId: "task_legacy",
      taskTitle: "Legacy entry",
      pointsDelta: 0,
      secondsDelta: 0,
      reason: "Legacy log compatibility",
      awardedByUid: ALICE_UID,
      awardedByName: "Alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(txRef.update({ pointsDelta: 5000 }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("creditTransactions").get());
});

test("Legacy global leaderboard is denied and Family/Workplace group leaderboard member standings stay private to group members", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const outsiderDb = testEnv.authenticatedContext("outsider_999", {
    email: "outsider@example.com",
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 1000);

  // Legacy /leaderboard collection is fully removed (default deny)
  await assertFails(unauthDb.collection("leaderboard").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("leaderboard").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice",
    points: 500,
    activeProfileIcon: "",
    visibility: "PUBLIC",
    createdAt: now,
    updatedAt: now,
  }));
  await assertFails(bobDb.collection("leaderboard").where("visibility", "==", "PUBLIC").get());

  // Seed a Workplace (ORGANIZATION) group with two ranked members
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("groups").doc("workplace_1").set({
      name: "Design Studio",
      groupType: "ORGANIZATION",
      managerUid: ALICE_UID,
      managerEmail: "alice@example.com",
      managerName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      invitedEmails: [],
      invitedAdminEmails: [],
      creditSystemEnabled: true,
      earlyPointsPerSecond: 1,
      latePenaltyPerSecond: 1,
      creditGracePeriodSeconds: 0,
      maxCreditPerTask: 0,
      maxPenaltyPerTask: 0,
      allowNegativeCredits: true,
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("groups").doc("workplace_1").collection("members").doc(ALICE_UID).set({
      groupId: "workplace_1",
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "MANAGER",
      canManageOthers: true,
      remoteDeviceLocked: false,
      remoteLockReason: "",
      installedApps: [],
      appsLastSyncedAtMillis: 0,
      creditBalance: 1450,
      totalEarnedCredits: 1600,
      totalDeductedCredits: 150,
      activeProfileIcon: "hero_valkyrie",
      unlockedProfileIcons: ["hero_valkyrie"],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("groups").doc("workplace_1").collection("members").doc(BOB_UID).set({
      groupId: "workplace_1",
      uid: BOB_UID,
      email: "bob@example.com",
      displayName: "Bob",
      role: "MEMBER",
      canManageOthers: false,
      remoteDeviceLocked: false,
      remoteLockReason: "",
      installedApps: [],
      appsLastSyncedAtMillis: 0,
      creditBalance: 1920,
      totalEarnedCredits: 1920,
      totalDeductedCredits: 0,
      activeProfileIcon: "hero_titan",
      unlockedProfileIcons: ["hero_titan"],
      createdAt: now,
      updatedAt: now,
    });
  });

  // Group members can list all members' points/ranks for the Group Leaderboard
  await assertSucceeds(bobDb.collection("groups").doc("workplace_1").collection("members").get());
  await assertSucceeds(aliceDb.collection("groups").doc("workplace_1").collection("members").get());

  // Outsiders and unauthenticated users cannot view the group leaderboard
  await assertFails(outsiderDb.collection("groups").doc("workplace_1").collection("members").get());
  await assertFails(unauthDb.collection("groups").doc("workplace_1").collection("members").get());
});

test("Task categoryId and category preferences sync are allowed for owner and reject oversized or invalid values", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_cat_1");
  const prefRef = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences");

  const initialTask = validTaskPayload(ALICE_UID, { categoryId: "work" });
  await assertSucceeds(taskRef.set(initialTask));
  await assertSucceeds(
    taskRef.set({
      ...initialTask,
      categoryId: "custom_abc123",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Oversized categoryId (> 64 chars) is rejected
  await assertFails(
    taskRef.set({
      ...initialTask,
      categoryId: "c".repeat(65),
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Valid category preferences create and update succeed
  const initialPref = validPrefPayload(ALICE_UID, {
    categoriesEnabled: true,
    disabledPremadeCategoryIds: ["shopping"],
    customCategoriesJson: JSON.stringify([{ id: "custom_1", name: "Errands", colorHex: 4282339765 }]),
  });
  await assertSucceeds(prefRef.set(initialPref));
  await assertSucceeds(
    prefRef.set({
      ...initialPref,
      categoriesEnabled: false,
      disabledPremadeCategoryIds: ["shopping", "home"],
      customCategoriesJson: "[]",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Invalid type for categoriesEnabled or oversized customCategoriesJson is rejected
  await assertFails(
    prefRef.set({
      ...initialPref,
      categoriesEnabled: "yes",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    prefRef.set({
      ...initialPref,
      customCategoriesJson: "x".repeat(8001),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

function validTaskRecordPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId,
    recordType: "DONE",
    sourceScope: "FAMILY",
    taskId: "gtask_clean_room",
    taskTitle: "Clean study desk",
    taskSubtext: "Organize books and cables",
    groupId: "grp_family_1",
    groupName: "Smith Family",
    counterpartyName: "Alice",
    pointsReceived: 120,
    hasPoints: true,
    pointsReason: "Completed 2m before deadline",
    eventTimeMillis: 1762902500000,
    dueAtMillis: 1762903600000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Private taskRecords library is strictly accessible only to the signed-in owner and validates all fields", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const aliceRecordRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("taskRecords")
    .doc("done_group_gtask_clean_room");

  // Unauthenticated user cannot read, list, or create records
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("taskRecords").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("rec_unauth")
      .set(validTaskRecordPayload(ALICE_UID))
  );

  // Owner Alice can create ENTERED, DONE (with points), GIVEN (workplace/family), and POINTS_RECEIVED records
  const donePayload = validTaskRecordPayload(ALICE_UID);
  await assertSucceeds(aliceRecordRef.set(donePayload));
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("entered_personal_t1")
      .set(
        validTaskRecordPayload(ALICE_UID, {
          recordType: "ENTERED",
          sourceScope: "PERSONAL",
          taskId: "t1",
          taskTitle: "Prepare quarterly deck",
          groupId: "",
          groupName: "",
          counterpartyName: "",
          pointsReceived: 0,
          hasPoints: false,
          pointsReason: "",
        })
      )
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("given_group_g1")
      .set(
        validTaskRecordPayload(ALICE_UID, {
          recordType: "GIVEN",
          sourceScope: "WORKPLACE",
          taskId: "g1",
          taskTitle: "Audit sprint tickets",
          groupId: "grp_work_1",
          groupName: "Design Studio",
          counterpartyName: "Bob",
          pointsReceived: 0,
          hasPoints: false,
          pointsReason: "",
        })
      )
  );
  await assertSucceeds(aliceRecordRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("taskRecords").get()
  );

  // Owner Alice can update points after manager approval while preserving userId & createdAt
  await assertSucceeds(
    aliceRecordRef.set({
      ...donePayload,
      pointsReceived: 180,
      pointsReason: "Approved 3m early",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Another signed-in user (Bob) CANNOT get, list, create, update, or delete Alice's records
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("done_group_gtask_clean_room")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("taskRecords").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("forged_by_bob")
      .set(validTaskRecordPayload(ALICE_UID))
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("done_group_gtask_clean_room")
      .update({
        pointsReceived: 9999,
        updatedAt: new Date(Date.now() - 200),
      })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("done_group_gtask_clean_room")
      .delete()
  );

  // Invalid recordType, invalid sourceScope, ghost field, or spoofed userId are rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("bad_type")
      .set(validTaskRecordPayload(ALICE_UID, { recordType: "UNKNOWN" }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("bad_scope")
      .set(validTaskRecordPayload(ALICE_UID, { sourceScope: "PUBLIC" }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("spoofed_uid")
      .set(validTaskRecordPayload(BOB_UID))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("taskRecords")
      .doc("ghost_field")
      .set({ ...validTaskRecordPayload(ALICE_UID), extraFlag: true })
  );

  // Owner Alice can delete her own record
  await assertSucceeds(aliceRecordRef.delete());
});

test("Group Relay Chains (Pass the Baton): members create, only active runner or admin advances baton, wrong runner and outsiders blocked", async () => {
  const CHARLIE_UID = "charlie_789";
  const OUTSIDER_UID = "outsider_999";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, {
    email: "charlie@example.com",
    email_verified: true,
  }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "outsider@example.com",
    email_verified: true,
  }).firestore();

  const groupId = "grp_relay_1";
  const now = new Date(Date.now() - 2000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("groups").doc(groupId).set({
      name: "Sunday Dinner Team",
      groupType: "FAMILY",
      managerUid: ALICE_UID,
      managerEmail: "alice@example.com",
      managerName: "Alice",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      adminUids: [ALICE_UID],
      invitedEmails: [],
      invitedAdminEmails: [],
      creditSystemEnabled: false,
      earlyPointsPerSecond: 1,
      latePenaltyPerSecond: 1,
      creditGracePeriodSeconds: 0,
      maxCreditPerTask: 0,
      maxPenaltyPerTask: 0,
      allowNegativeCredits: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const initialSteps = [
    {
      stepTitle: "Wash & chop veggies",
      assigneeUid: BOB_UID,
      assigneeName: "Bob",
      durationMinutes: 15,
      done: false,
      completedAtMillis: 0,
      handoffSpeedSeconds: 0,
    },
    {
      stepTitle: "Cook pasta & plate dinner",
      assigneeUid: CHARLIE_UID,
      assigneeName: "Charlie",
      durationMinutes: 20,
      done: false,
      completedAtMillis: 0,
      handoffSpeedSeconds: 0,
    },
  ];

  const relayPayload = {
    groupId,
    title: "Sunday Dinner Relay",
    description: "Work together so dinner is ready on time",
    createdByUid: ALICE_UID,
    createdByName: "Alice",
    steps: initialSteps,
    currentStepIndex: 0,
    currentAssigneeUid: BOB_UID,
    relayStatus: "ACTIVE",
    streakCount: 0,
    startedAtMillis: Date.now() - 1500,
    lastHandoffAtMillis: Date.now() - 1500,
    completedAtMillis: 0,
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  };

  await assertFails(
    outsiderDb.collection("groups").doc(groupId).collection("relays").doc("relay_outsider").set({
      ...relayPayload,
      createdByUid: OUTSIDER_UID,
    })
  );

  const relayRef = aliceDb.collection("groups").doc(groupId).collection("relays").doc("relay_dinner");
  await assertSucceeds(relayRef.set(relayPayload));

  await assertFails(
    outsiderDb.collection("groups").doc(groupId).collection("relays").doc("relay_dinner").get()
  );

  const step1DoneSteps = [
    { ...initialSteps[0], done: true, completedAtMillis: Date.now() - 500, handoffSpeedSeconds: 45 },
    initialSteps[1],
  ];
  await assertFails(
    charlieDb.collection("groups").doc(groupId).collection("relays").doc("relay_dinner").update({
      steps: step1DoneSteps,
      currentStepIndex: 1,
      currentAssigneeUid: CHARLIE_UID,
      streakCount: 1,
      lastHandoffAtMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    bobDb.collection("groups").doc(groupId).collection("relays").doc("relay_dinner").update({
      steps: step1DoneSteps,
      currentStepIndex: 1,
      currentAssigneeUid: CHARLIE_UID,
      streakCount: 1,
      lastHandoffAtMillis: Date.now() - 400,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  const allDoneSteps = [
    step1DoneSteps[0],
    { ...initialSteps[1], done: true, completedAtMillis: Date.now() - 200, handoffSpeedSeconds: 60 },
  ];
  await assertSucceeds(
    charlieDb.collection("groups").doc(groupId).collection("relays").doc("relay_dinner").update({
      steps: allDoneSteps,
      currentStepIndex: 2,
      currentAssigneeUid: "",
      relayStatus: "COMPLETED",
      streakCount: 2,
      lastHandoffAtMillis: Date.now() - 200,
      completedAtMillis: Date.now() - 200,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertFails(
    bobDb.collection("groups").doc(groupId).collection("relays").doc("relay_dinner").delete()
  );
  await assertSucceeds(relayRef.delete());
});




