const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_kua_001";
const ADMIN_EMAIL = "minhashaf02@gmail.com";
const MODIN_A_UID = "modin_blingoh_001";
const MODIN_A_EMAIL = "modin.blingoh@gmail.com";
const MODIN_B_UID = "modin_clering_002";
const MODIN_B_EMAIL = "modin.clering@gmail.com";
const DUMMY_HASH = "a".repeat(64);

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function futureTime(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
}

function validVillagePayload(overrides = {}) {
  const now = pastTime();
  return {
    name: "Desa Blingoh",
    code: "blingoh",
    modinName: "H. Ahmad Fauzi",
    modinEmail: MODIN_A_EMAIL,
    modinPhone: "081234567890",
    createdByUid: ADMIN_UID,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validStaffAccountPayload(overrides = {}) {
  const now = pastTime();
  return {
    username: "modin.blingoh",
    passwordHash: DUMMY_HASH,
    passwordSalt: "kua_salt_001",
    role: "MODIN",
    villageId: "blingoh",
    villageName: "Desa Blingoh",
    officerName: "Modin Desa Blingoh",
    whatsappNumber: "081234567890",
    googleEmail: MODIN_A_EMAIL,
    active: true,
    ownerUid: ADMIN_UID,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validReminderSettingsPayload(overrides = {}) {
  const now = pastTime();
  return {
    autoPhoneNotifEnabled: true,
    whatsappReminderEnabled: true,
    remindOnWeddingDay: true,
    remindOneDayBefore: true,
    remindThreeDaysBefore: false,
    reminderTime: "07:00",
    whatsappTemplate: "Assalamu'alaikum, pengingat jadwal nikah {pengantin} pada {tanggal} {jam} WIB.",
    updatedByName: "Admin KUA Donorojo",
    allowedReaderEmails: [MODIN_A_EMAIL],
    ownerUid: ADMIN_UID,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSchedulePayload(overrides = {}) {
  const now = pastTime();
  return {
    registrationNumber: "KUA-DNR/2026/001",
    groomName: "Muhammad Rizki Pratama",
    groomNik: "3320161504980001",
    brideName: "Siti Aminah",
    brideNik: "3320165208990002",
    villageId: "blingoh",
    villageName: "Desa Blingoh",
    weddingDate: "2026-12-20",
    weddingTime: "09:00",
    locationType: "KUA",
    locationAddress: "Balai Nikah KUA Donorojo",
    penghuluName: "H. Nurul Huda, S.Ag.",
    validationStatus: "MENUNGGU",
    documentsComplete: false,
    verificationNote: "Menunggu kelengkapan N1 dan fotokopi KK.",
    verifiedByName: "",
    allowedModinEmails: [MODIN_A_EMAIL],
    createdByUid: ADMIN_UID,
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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("villages").get());
  await assertFails(unauthDb.collection("staff_accounts").get());
  await assertFails(unauthDb.collection("reminder_settings").get());
  await assertFails(unauthDb.collection("marriage_schedules").get());
});

test("Admin KUA: manages staff_accounts and reminder_settings; Modin cannot mutate them", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const modinADb = testEnv
    .authenticatedContext(MODIN_A_UID, { email: MODIN_A_EMAIL, email_verified: true })
    .firestore();

  // Admin creates Modin staff account and global reminder settings
  await assertSucceeds(
    adminDb.collection("staff_accounts").doc("modin_blingoh").set(validStaffAccountPayload())
  );
  await assertSucceeds(
    adminDb.collection("reminder_settings").doc("global_config").set(validReminderSettingsPayload())
  );

  // Admin updates password hash & reminder settings
  await assertSucceeds(
    adminDb.collection("staff_accounts").doc("modin_blingoh").update({
      passwordHash: "b".repeat(64),
      passwordSalt: "kua_salt_002",
      whatsappNumber: "085700001111",
      updatedAt: pastTime(1000),
    })
  );
  await assertSucceeds(
    adminDb.collection("reminder_settings").doc("global_config").update({
      reminderTime: "06:30",
      remindThreeDaysBefore: true,
      updatedAt: pastTime(1000),
    })
  );

  // Modin A can read own staff account and reminder settings (scoped query)
  await assertSucceeds(
    modinADb.collection("staff_accounts").where("googleEmail", "==", MODIN_A_EMAIL).get()
  );
  await assertSucceeds(
    modinADb.collection("reminder_settings").where("allowedReaderEmails", "array-contains", MODIN_A_EMAIL).get()
  );

  // Modin A CANNOT list unfiltered staff_accounts or update credentials / reminder settings
  await assertFails(modinADb.collection("staff_accounts").get());
  await assertFails(
    modinADb.collection("staff_accounts").doc("modin_blingoh").update({
      passwordHash: "c".repeat(64),
      updatedAt: pastTime(500),
    })
  );
  await assertFails(
    modinADb.collection("reminder_settings").doc("global_config").update({
      autoPhoneNotifEnabled: false,
      updatedAt: pastTime(500),
    })
  );
});

test("Admin KUA: full CRUD on villages and marriage_schedules, rejects ghost fields and future timestamps", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(
    adminDb.collection("villages").doc("blingoh").set(validVillagePayload())
  );
  await assertSucceeds(
    adminDb.collection("marriage_schedules").doc("sched_1").set(validSchedulePayload())
  );
  await assertSucceeds(
    adminDb.collection("marriage_schedules").doc("sched_1").update({
      validationStatus: "TERVERIFIKASI",
      documentsComplete: true,
      verificationNote: "Berkas N1-N4 lengkap.",
      verifiedByName: "Admin KUA Donorojo",
      updatedAt: pastTime(1000),
    })
  );

  // Shadow update with ghost field fails
  await assertFails(
    adminDb.collection("marriage_schedules").doc("sched_1").update({
      validationStatus: "SELESAI",
      ghostField: "hacked",
      updatedAt: pastTime(500),
    })
  );

  // Future timestamp fails
  await assertFails(
    adminDb.collection("marriage_schedules").doc("sched_1").update({
      validationStatus: "SELESAI",
      updatedAt: futureTime(),
    })
  );
});

test("Modin Desa: strict read-only access scoped ONLY to assigned village", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("villages")
      .doc("blingoh")
      .set(validVillagePayload({ code: "blingoh", name: "Desa Blingoh", modinEmail: MODIN_A_EMAIL }));
    await db
      .collection("villages")
      .doc("clering")
      .set(validVillagePayload({ code: "clering", name: "Desa Clering", modinEmail: MODIN_B_EMAIL }));

    await db
      .collection("marriage_schedules")
      .doc("sched_blingoh")
      .set(validSchedulePayload({ villageId: "blingoh", villageName: "Desa Blingoh", allowedModinEmails: [MODIN_A_EMAIL] }));
    await db
      .collection("marriage_schedules")
      .doc("sched_clering")
      .set(validSchedulePayload({ registrationNumber: "KUA-DNR/2026/002", villageId: "clering", villageName: "Desa Clering", allowedModinEmails: [MODIN_B_EMAIL] }));
  });

  const modinADb = testEnv
    .authenticatedContext(MODIN_A_UID, { email: MODIN_A_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(
    modinADb.collection("marriage_schedules").where("allowedModinEmails", "array-contains", MODIN_A_EMAIL).get()
  );
  await assertFails(modinADb.collection("marriage_schedules").get());
  await assertFails(modinADb.collection("marriage_schedules").doc("sched_clering").get());
  await assertFails(
    modinADb.collection("marriage_schedules").doc("sched_blingoh").update({
      validationStatus: "TERVERIFIKASI",
      updatedAt: pastTime(500),
    })
  );
});
