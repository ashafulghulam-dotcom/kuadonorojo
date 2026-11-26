const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "admin_owner_001";
const MODIN_UID = "modin_device_002";
const PENGHULU_UID = "penghulu_device_003";
const STRANGER_UID = "stranger_999";

const OWNER_EMAIL = "admin.donorojo@gmail.com";
const MODIN_EMAIL = "modin.sumberrejo@gmail.com";
const PENGHULU_EMAIL = "penghulu.donorojo@gmail.com";
const STRANGER_EMAIL = "stranger@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const SAMPLE_HASH = "a".repeat(64);
const SAMPLE_SALT = "salt12345678";

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

function makeVillagePayload(ownerUid = OWNER_UID, created = pastDate()) {
  return {
    name: "Desa Sumberrejo",
    code: "sumberrejo",
    modinName: "Modin Desa Sumberrejo",
    modinEmail: MODIN_EMAIL,
    modinPhone: "081234567890",
    allowedReaderEmails: [MODIN_EMAIL, PENGHULU_EMAIL],
    createdByUid: ownerUid,
    createdAt: created,
    updatedAt: created,
  };
}

function makeStaffPayload(ownerUid = OWNER_UID, role = "MODIN", googleEmail = MODIN_EMAIL, created = pastDate()) {
  return {
    localId: role === "PENGHULU" ? "penghulu_kua_1" : "modin_sumberrejo",
    username: role === "PENGHULU" ? "penghulu1" : "modin.sumberrejo",
    passwordHash: SAMPLE_HASH,
    passwordSalt: SAMPLE_SALT,
    role,
    villageId: role === "MODIN" ? "sumberrejo" : "",
    villageName: role === "MODIN" ? "Desa Sumberrejo" : "KUA Donorojo",
    officerName: role === "PENGHULU" ? "H. Ahmad Subhan" : "Modin Sumberrejo",
    whatsappNumber: "081234567890",
    googleEmail,
    active: true,
    waliHakimAuthorized: role === "PENGHULU",
    ownerUid,
    createdAt: created,
    updatedAt: created,
  };
}

function makeReminderSettingsPayload(ownerUid = OWNER_UID, created = pastDate()) {
  return {
    autoPhoneNotifEnabled: true,
    whatsappReminderEnabled: true,
    remindOnWeddingDay: true,
    remindOneDayBefore: true,
    remindThreeDaysBefore: false,
    reminderTime: "07:00",
    adminWhatsappNumber: "081122334455",
    adminWhatsappApp: "WHATSAPP_BUSINESS",
    whatsappTemplate: "Pengingat jadwal akad untuk {modin} di {desa}.",
    whatsappPenghuluTemplate: "Pengingat tugas akad untuk {penghulu} di {desa}.",
    headOfKuaTitle: "Kepala KUA Donorojo",
    headOfKuaName: "H. Abdullah, S.Ag.",
    headOfKuaNip: "197801012005011001",
    defaultWaliHakimAccountId: "penghulu_kua_1",
    defaultWaliHakimName: "H. Ahmad Subhan",
    updatedByName: "Admin KUA Donorojo",
    allowedReaderEmails: [MODIN_EMAIL, PENGHULU_EMAIL],
    ownerUid,
    createdAt: created,
    updatedAt: created,
  };
}

function makeSchedulePayload(ownerUid = OWNER_UID, villageDocId = `${OWNER_UID}_sumberrejo`, created = pastDate()) {
  return {
    registrationNumber: "KUA-DNR/2026/001",
    groomName: "Ahmad Fauzan",
    groomNik: "3320123456789001",
    brideName: "Siti Aminah",
    brideNik: "3320123456789002",
    villageDocId,
    villageId: "sumberrejo",
    villageName: "Desa Sumberrejo",
    modinAccountId: "modin_sumberrejo",
    modinName: "Modin Desa Sumberrejo",
    weddingDate: "2026-12-10",
    weddingTime: "09:00",
    locationType: "KUA",
    locationAddress: "Balai Nikah KUA Donorojo",
    penghuluAccountId: "penghulu_kua_1",
    penghuluName: "H. Ahmad Subhan",
    waliStatus: "WALI_NASAB",
    waliRelationship: "Ayah Kandung",
    waliName: "H. Supriyadi",
    waliBin: "Kasan",
    waliNik: "3320123456789003",
    waliAddress: "Desa Sumberrejo RT 02 RW 01",
    waliHakimAccountId: "",
    waliHakimReason: "",
    validationStatus: "MENUNGGU",
    documentsComplete: false,
    documentChecklistCodes: "N1,N2,KTP",
    verificationNote: "Menunggu berkas N4 dan Surat Sehat.",
    verifiedByName: "",
    allowedMemberEmails: [MODIN_EMAIL, PENGHULU_EMAIL],
    createdByUid: ownerUid,
    createdAt: created,
    updatedAt: created,
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

test("Unauthenticated users are completely blocked across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("villages").get());
  await assertFails(unauthDb.collection("staff_accounts").get());
  await assertFails(unauthDb.collection("reminder_settings").get());
  await assertFails(unauthDb.collection("marriage_schedules").get());
  await assertFails(unauthDb.collection("villages").doc("v1").set(makeVillagePayload()));
});

test("Workspace owner can create/update/list villages, staff, settings, and schedules with Kotlin-aligned queries", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  const villageDocId = `${OWNER_UID}_sumberrejo`;
  await assertSucceeds(ownerDb.collection("villages").doc(villageDocId).set(makeVillagePayload()));
  await assertSucceeds(ownerDb.collection("staff_accounts").doc(`${OWNER_UID}_modin_sumberrejo`).set(makeStaffPayload()));
  await assertSucceeds(ownerDb.collection("reminder_settings").doc(`config_${OWNER_UID}`).set(makeReminderSettingsPayload()));
  await assertSucceeds(ownerDb.collection("marriage_schedules").doc("sched_001").set(makeSchedulePayload()));

  // Partial verification + checklist update
  await assertSucceeds(
    ownerDb.collection("marriage_schedules").doc("sched_001").update({
      validationStatus: "TERVERIFIKASI",
      documentsComplete: true,
      documentChecklistCodes: "N1,N2,N4,KTP,AKTA,SEHAT",
      verificationNote: "Berkas N1-N4 lengkap dan valid.",
      verifiedByName: "Admin KUA Donorojo",
      updatedAt: pastDate(1000),
    })
  );

  // Scoped Kotlin queries succeed
  await assertSucceeds(ownerDb.collection("villages").where("createdByUid", "==", OWNER_UID).get());
  await assertSucceeds(ownerDb.collection("staff_accounts").where("ownerUid", "==", OWNER_UID).get());
  await assertSucceeds(ownerDb.collection("reminder_settings").where("ownerUid", "==", OWNER_UID).get());
  await assertSucceeds(ownerDb.collection("marriage_schedules").where("createdByUid", "==", OWNER_UID).get());

  // Unfiltered list queries MUST fail
  await assertFails(ownerDb.collection("marriage_schedules").get());
  await assertFails(ownerDb.collection("staff_accounts").get());
});

test("Invited Modin & Penghulu can read synced schedules and update ONLY their own self-profile fields", async () => {
  const villageDocId = `${OWNER_UID}_sumberrejo`;
  const staffDocId = `${OWNER_UID}_modin_sumberrejo`;

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("villages").doc(villageDocId).set(makeVillagePayload());
    await db.collection("staff_accounts").doc(staffDocId).set(makeStaffPayload());
    await db.collection("reminder_settings").doc(`config_${OWNER_UID}`).set(makeReminderSettingsPayload());
    await db.collection("marriage_schedules").doc("sched_001").set(makeSchedulePayload());
  });

  const modinDb = testEnv.authenticatedContext(MODIN_UID, {
    email: MODIN_EMAIL,
    email_verified: true,
  }).firestore();

  // Scoped cross-device reads succeed
  await assertSucceeds(modinDb.collection("marriage_schedules").where("allowedMemberEmails", "array-contains", MODIN_EMAIL).get());
  await assertSucceeds(modinDb.collection("staff_accounts").where("googleEmail", "==", MODIN_EMAIL).get());
  await assertSucceeds(modinDb.collection("reminder_settings").where("allowedReaderEmails", "array-contains", MODIN_EMAIL).get());

  // Modin CANNOT modify marriage schedules (read-only)
  await assertFails(
    modinDb.collection("marriage_schedules").doc("sched_001").update({
      validationStatus: "TERVERIFIKASI",
      updatedAt: pastDate(1000),
    })
  );

  // Modin CAN update own profile (name, WA, username, password)
  await assertSucceeds(
    modinDb.collection("staff_accounts").doc(staffDocId).update({
      officerName: "H. Modin Sumberrejo Baru",
      whatsappNumber: "085299887766",
      updatedAt: pastDate(1000),
    })
  );

  // Modin CANNOT escalate role or toggle active status
  await assertFails(
    modinDb.collection("staff_accounts").doc(staffDocId).update({
      role: "ADMIN",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    modinDb.collection("staff_accounts").doc(staffDocId).update({
      active: false,
      updatedAt: pastDate(500),
    })
  );
});

test("Email spoofing (unverified email) and stranger cross-user access are strictly rejected", async () => {
  const villageDocId = `${OWNER_UID}_sumberrejo`;
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("villages").doc(villageDocId).set(makeVillagePayload());
    await db.collection("marriage_schedules").doc("sched_001").set(makeSchedulePayload());
  });

  // Unverified spoofed email cannot read
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: MODIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("marriage_schedules").doc("sched_001").get());

  // Stranger with verified email cannot read or edit
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID, {
    email: STRANGER_EMAIL,
    email_verified: true,
  }).firestore();
  await assertFails(strangerDb.collection("marriage_schedules").doc("sched_001").get());
  await assertFails(strangerDb.collection("marriage_schedules").doc("sched_001").delete());
});

test("Shadow fields, future timestamps, and immutable createdAt tampering are rejected", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  const villageDocId = `${OWNER_UID}_sumberrejo`;
  await assertSucceeds(ownerDb.collection("villages").doc(villageDocId).set(makeVillagePayload()));

  // Ghost field rejected
  await assertFails(
    ownerDb.collection("marriage_schedules").doc("sched_bad_ghost").set({
      ...makeSchedulePayload(),
      ghostField: true,
    })
  );

  // Future timestamp rejected
  const future = new Date(Date.now() + 60000);
  await assertFails(
    ownerDb.collection("marriage_schedules").doc("sched_bad_time").set(
      makeSchedulePayload(OWNER_UID, villageDocId, future)
    )
  );

  // Valid create, then reject mutating createdAt
  await assertSucceeds(ownerDb.collection("marriage_schedules").doc("sched_ok").set(makeSchedulePayload()));
  await assertFails(
    ownerDb.collection("marriage_schedules").doc("sched_ok").update({
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
});
