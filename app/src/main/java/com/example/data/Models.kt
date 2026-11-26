package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DEFAULT_ADMIN_USERNAME = "adminkua"
const val DEFAULT_ADMIN_PASSWORD = "kua2026"

enum class UserRole(val code: String, val label: String) {
    ADMIN("ADMIN", "Admin KUA Donorojo"),
    PENGHULU("PENGHULU", "Penghulu KUA"),
    MODIN("MODIN", "Modin Desa");

    companion object {
        fun fromCode(raw: String?): UserRole =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: MODIN
    }
}

enum class DashboardMode {
    ADMIN_KUA,
    MODIN_DESA,
    PENGHULU
}

enum class LocationType(val code: String, val label: String) {
    KUA("KUA", "Balai Nikah KUA"),
    LUAR_KUA("LUAR_KUA", "Luar KUA / Rumah");

    companion object {
        fun fromCode(raw: String?): LocationType =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: KUA
    }
}

enum class WaliStatus(val code: String, val label: String) {
    WALI_NASAB("WALI_NASAB", "Wali Nasab"),
    WALI_HAKIM("WALI_HAKIM", "Wali Hakim");

    companion object {
        fun fromCode(raw: String?): WaliStatus =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: WALI_NASAB
    }
}

object WaliReferenceData {
    val nasabRelationships: List<String> = listOf(
        "Ayah Kandung",
        "Kakek dari Ayah (Ayah dari Ayah)",
        "Saudara Laki-laki Sekandung",
        "Saudara Laki-laki Seayah",
        "Anak Laki-laki dari Saudara Laki-laki Sekandung",
        "Anak Laki-laki dari Saudara Laki-laki Seayah",
        "Paman Sekandung (Saudara Ayah Sekandung)",
        "Paman Seayah (Saudara Ayah Seayah)",
        "Anak Laki-laki dari Paman Sekandung",
        "Anak Laki-laki dari Paman Seayah"
    )

    val waliHakimReasons: List<String> = listOf(
        "Tidak mempunyai wali nasab",
        "Wali nasab tidak diketahui keberadaannya (Mafqud)",
        "Wali nasab berada di tempat jauh / tidak dapat dihadirkan",
        "Wali nasab enggan / menolak (Wali Adhal - Penetapan PA)",
        "Wali nasab tidak memenuhi syarat (non-muslim / belum baligh / hilang akal / ihram)",
        "Wali nasab sedang menjalani hukuman / tidak cakap hukum"
    )
}

enum class ValidationStatus(
    val code: String,
    val label: String,
    val shortLabel: String,
    val stepIndex: Int,
    val description: String
) {
    MENUNGGU(
        code = "MENUNGGU",
        label = "Menunggu Verifikasi",
        shortLabel = "Menunggu",
        stepIndex = 0,
        description = "Data pendaftaran baru masuk dan menunggu pemeriksaan KUA Donorojo."
    ),
    VERIFIKASI_BERKAS(
        code = "VERIFIKASI_BERKAS",
        label = "Pemeriksaan Berkas",
        shortLabel = "Cek Berkas",
        stepIndex = 1,
        description = "Petugas KUA Donorojo sedang memeriksa kelengkapan berkas N1–N4."
    ),
    PERLU_REVISI(
        code = "PERLU_REVISI",
        label = "Perlu Perbaikan",
        shortLabel = "Revisi",
        stepIndex = 1,
        description = "Ada dokumen atau data yang perlu dilengkapi sebelum validasi akhir."
    ),
    TERVERIFIKASI(
        code = "TERVERIFIKASI",
        label = "Terverifikasi Akhir",
        shortLabel = "Terverifikasi",
        stepIndex = 2,
        description = "Validasi akhir disetujui KUA Donorojo. Siap dilaksanakan sesuai jadwal."
    ),
    SELESAI(
        code = "SELESAI",
        label = "Akad Selesai",
        shortLabel = "Selesai",
        stepIndex = 3,
        description = "Akad nikah telah terlaksana dan tercatat resmi di KUA Donorojo."
    ),
    DIBATALKAN(
        code = "DIBATALKAN",
        label = "Dibatalkan / Tunda",
        shortLabel = "Batal",
        stepIndex = 0,
        description = "Jadwal akad dibatalkan atau dijadwalkan ulang oleh pihak terkait."
    );

    companion object {
        fun fromCode(raw: String?): ValidationStatus =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: MENUNGGU
    }
}

data class DocumentChecklist(
    val n1: Boolean = false,
    val n2: Boolean = false,
    val n4: Boolean = false,
    val ktp: Boolean = false,
    val akta: Boolean = false,
    val sehat: Boolean = false,
    val tambahan: Boolean = false
) {
    val mandatoryCompletedCount: Int
        get() = listOf(n1, n2, n4, ktp, akta, sehat).count { it }

    val completedCount: Int
        get() = listOf(n1, n2, n4, ktp, akta, sehat, tambahan).count { it }

    val totalCount: Int
        get() = TOTAL_ITEMS

    val isAllMandatoryComplete: Boolean
        get() = n1 && n2 && n4 && ktp && akta && sehat

    fun missingMandatoryLabels(): List<String> = buildList {
        if (!n1) add("Pengantar Nikah (N1)")
        if (!n2) add("Permohonan Kehendak Nikah (N2)")
        if (!n4) add("Persetujuan Calon Pengantin (N4)")
        if (!ktp) add("Fotokopi KTP & KK")
        if (!akta) add("Akta Kelahiran / Ijazah")
        if (!sehat) add("Surat Kesehatan / Imunisasi TT")
    }

    fun serialize(): String = buildList {
        if (n1) add(CODE_N1)
        if (n2) add(CODE_N2)
        if (n4) add(CODE_N4)
        if (ktp) add(CODE_KTP)
        if (akta) add(CODE_AKTA)
        if (sehat) add(CODE_SEHAT)
        if (tambahan) add(CODE_TAMBAHAN)
    }.joinToString(",")

    fun summaryText(): String = "$completedCount/$TOTAL_ITEMS Berkas"

    companion object {
        const val TOTAL_ITEMS = 7
        const val CODE_N1 = "N1"
        const val CODE_N2 = "N2"
        const val CODE_N4 = "N4"
        const val CODE_KTP = "KTP"
        const val CODE_AKTA = "AKTA"
        const val CODE_SEHAT = "SEHAT"
        const val CODE_TAMBAHAN = "TAMBAHAN"

        fun allComplete(includeOptional: Boolean = true): DocumentChecklist = DocumentChecklist(
            n1 = true,
            n2 = true,
            n4 = true,
            ktp = true,
            akta = true,
            sehat = true,
            tambahan = includeOptional
        )

        fun fromSerialized(raw: String, fallbackComplete: Boolean = false): DocumentChecklist {
            val tokens = raw.split(",")
                .map { it.trim().uppercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .toSet()
            if (tokens.isEmpty() && fallbackComplete) {
                return allComplete(includeOptional = false)
            }
            return DocumentChecklist(
                n1 = CODE_N1 in tokens,
                n2 = CODE_N2 in tokens,
                n4 = CODE_N4 in tokens,
                ktp = CODE_KTP in tokens,
                akta = CODE_AKTA in tokens,
                sehat = CODE_SEHAT in tokens,
                tambahan = CODE_TAMBAHAN in tokens
            )
        }
    }
}

data class CloudSyncStatus(
    val userEmail: String = "",
    val isSyncing: Boolean = false,
    val isOnlineSynced: Boolean = false,
    val syncedScheduleCount: Int = 0,
    val lastSyncedAtMillis: Long? = null,
    val errorMessage: String? = null
)

data class StaffAccount(
    val id: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val role: String = UserRole.MODIN.code,
    val villageId: String = "",
    val villageName: String = "",
    val officerName: String = "",
    val whatsappNumber: String = "",
    val googleEmail: String = "",
    val active: Boolean = true,
    val isWaliHakim: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedRole(): UserRole = UserRole.fromCode(role)
}

enum class WhatsAppAppPreference(
    val code: String,
    val label: String,
    val packageName: String?
) {
    AUTO("AUTO", "Otomatis", null),
    WHATSAPP("WHATSAPP", "WhatsApp", "com.whatsapp"),
    WHATSAPP_BUSINESS("WHATSAPP_BUSINESS", "WA Business", "com.whatsapp.w4b");

    companion object {
        fun fromCode(raw: String?): WhatsAppAppPreference =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

data class ReminderSettings(
    val id: String = GLOBAL_DOC_ID,
    val autoPhoneNotifEnabled: Boolean = true,
    val whatsappReminderEnabled: Boolean = true,
    val remindOnWeddingDay: Boolean = true,
    val remindOneDayBefore: Boolean = true,
    val remindThreeDaysBefore: Boolean = false,
    val reminderTime: String = "07:00",
    val adminWhatsappNumber: String = "",
    val adminWhatsappApp: String = WhatsAppAppPreference.AUTO.code,
    val whatsappTemplate: String = DEFAULT_WA_TEMPLATE,
    val whatsappPenghuluTemplate: String = DEFAULT_WA_PENGHULU_TEMPLATE,
    val headOfKuaTitle: String = DEFAULT_HEAD_OF_KUA_TITLE,
    val headOfKuaName: String = "",
    val headOfKuaNip: String = "",
    val defaultWaliHakimAccountId: String = "",
    val defaultWaliHakimName: String = "",
    val updatedByName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedAdminWhatsappApp(): WhatsAppAppPreference =
        WhatsAppAppPreference.fromCode(adminWhatsappApp)

    companion object {
        const val GLOBAL_DOC_ID = "global_config"
        const val DEFAULT_HEAD_OF_KUA_TITLE = "Kepala KUA Donorojo"
        const val DEFAULT_WA_TEMPLATE =
            "Assalamu'alaikum Wr. Wb.\nYth. {modin} ({desa}),\n\nMengingatkan jadwal akad nikah:\n• Pengantin: {pengantin}\n• No. Daftar: {no_daftar}\n• Tanggal: {tanggal}\n• Jam: {jam} WIB\n• Lokasi: {lokasi}\n• Penghulu: {penghulu}\n• Wali Nikah: {wali}\n• Status Validasi KUA: {status}\n\nMohon kesiapan pendampingan di wilayah desa. Terima kasih.\n— Admin KUA Donorojo\nWA Resmi Admin KUA: {wa_admin}"
        const val DEFAULT_WA_PENGHULU_TEMPLATE =
            "Assalamu'alaikum Wr. Wb.\nYth. {penghulu},\n\nMengingatkan jadwal tugas pencatatan akad nikah:\n• Pengantin: {pengantin}\n• No. Daftar: {no_daftar}\n• Wilayah: {desa}\n• Tanggal: {tanggal}\n• Jam: {jam} WIB\n• Lokasi: {lokasi}\n• Modin Desa: {modin}\n• Wali Nikah: {wali}\n• Status Validasi KUA: {status}\n\nMohon kesiapan pelaksanaan tugas akad nikah. Terima kasih.\n— Admin KUA Donorojo\nWA Resmi Admin KUA: {wa_admin}"
    }
}

data class Village(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val modinName: String = "",
    val modinPhone: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class MarriageSchedule(
    val id: String = "",
    val registrationNumber: String = "",
    val groomName: String = "",
    val groomNik: String = "",
    val brideName: String = "",
    val brideNik: String = "",
    val villageId: String = "",
    val villageName: String = "",
    val modinAccountId: String = "",
    val modinName: String = "",
    val weddingDate: String = "",
    val weddingTime: String = "",
    val locationType: String = LocationType.KUA.code,
    val locationAddress: String = "",
    val penghuluAccountId: String = "",
    val penghuluName: String = "",
    val waliStatus: String = WaliStatus.WALI_NASAB.code,
    val waliRelationship: String = "",
    val waliName: String = "",
    val waliBin: String = "",
    val waliNik: String = "",
    val waliAddress: String = "",
    val waliHakimAccountId: String = "",
    val waliHakimReason: String = "",
    val validationStatus: String = ValidationStatus.MENUNGGU.code,
    val documentsComplete: Boolean = false,
    val documentChecklistCodes: String = "",
    val verificationNote: String = "",
    val verifiedByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedStatus(): ValidationStatus = ValidationStatus.fromCode(validationStatus)
    fun resolvedLocationType(): LocationType = LocationType.fromCode(locationType)
    fun resolvedWaliStatus(): WaliStatus = WaliStatus.fromCode(waliStatus)
    fun resolvedChecklist(): DocumentChecklist =
        DocumentChecklist.fromSerialized(documentChecklistCodes, documentsComplete)

    fun waliSummaryText(): String {
        return when (resolvedWaliStatus()) {
            WaliStatus.WALI_HAKIM -> {
                if (waliName.isNotBlank()) "Wali Hakim: $waliName" else "Wali Hakim"
            }
            WaliStatus.WALI_NASAB -> {
                val rel = waliRelationship.trim()
                val fullName = buildString {
                    append(waliName.trim())
                    if (waliBin.isNotBlank()) {
                        if (isNotEmpty()) append(" bin ")
                        append(waliBin.trim())
                    }
                }
                when {
                    rel.isNotBlank() && fullName.isNotBlank() -> "Wali Nasab ($rel): $fullName"
                    fullName.isNotBlank() -> "Wali Nasab: $fullName"
                    rel.isNotBlank() -> "Wali Nasab ($rel)"
                    else -> ""
                }
            }
        }
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ScheduleDraft(
    val id: String? = null,
    val registrationNumber: String = "",
    val groomName: String = "",
    val groomNik: String = "",
    val brideName: String = "",
    val brideNik: String = "",
    val villageId: String = "",
    val villageName: String = "",
    val modinAccountId: String = "",
    val modinName: String = "",
    val weddingDate: String = todayIsoDate(),
    val weddingTime: String = "09:00",
    val locationType: LocationType = LocationType.KUA,
    val locationAddress: String = "Balai Nikah KUA Donorojo",
    val penghuluAccountId: String = "",
    val penghuluName: String = "",
    val waliStatus: WaliStatus = WaliStatus.WALI_NASAB,
    val waliRelationship: String = "",
    val waliName: String = "",
    val waliBin: String = "",
    val waliNik: String = "",
    val waliAddress: String = "",
    val waliHakimAccountId: String = "",
    val waliHakimReason: String = "",
    val validationStatus: ValidationStatus = ValidationStatus.MENUNGGU,
    val documentsComplete: Boolean = false,
    val documentChecklist: DocumentChecklist = DocumentChecklist(),
    val verificationNote: String = ""
)

data class OfficialModinPreset(
    val accountId: String,
    val villageCode: String,
    val villageName: String,
    val defaultUsername: String,
    val defaultPassword: String,
    val defaultOfficerName: String
)

data class OfficialPenghuluPreset(
    val accountId: String,
    val defaultUsername: String,
    val defaultPassword: String,
    val defaultOfficerName: String
)

object DonorojoOfficialDirectory {
    const val ADMIN_ACCOUNT_ID = "admin_kua"

    val defaultPenghuluPresets: List<OfficialPenghuluPreset> = listOf(
        OfficialPenghuluPreset(
            accountId = "penghulu_kua_1",
            defaultUsername = "penghulu1",
            defaultPassword = "donorojo09",
            defaultOfficerName = "Penghulu KUA Donorojo 1"
        )
    )

    val eightModinPresets: List<OfficialModinPreset> = listOf(
        OfficialModinPreset(
            accountId = "modin_bandungharjo",
            villageCode = "bandungharjo",
            villageName = "Desa Bandungharjo",
            defaultUsername = "modin.bandungharjo",
            defaultPassword = "donorojo01",
            defaultOfficerName = "Modin Desa Bandungharjo"
        ),
        OfficialModinPreset(
            accountId = "modin_banyumanis",
            villageCode = "banyumanis",
            villageName = "Desa Banyumanis",
            defaultUsername = "modin.banyumanis",
            defaultPassword = "donorojo02",
            defaultOfficerName = "Modin Desa Banyumanis"
        ),
        OfficialModinPreset(
            accountId = "modin_blingoh",
            villageCode = "blingoh",
            villageName = "Desa Blingoh",
            defaultUsername = "modin.blingoh",
            defaultPassword = "donorojo03",
            defaultOfficerName = "Modin Desa Blingoh"
        ),
        OfficialModinPreset(
            accountId = "modin_clering",
            villageCode = "clering",
            villageName = "Desa Clering",
            defaultUsername = "modin.clering",
            defaultPassword = "donorojo04",
            defaultOfficerName = "Modin Desa Clering"
        ),
        OfficialModinPreset(
            accountId = "modin_jugo",
            villageCode = "jugo",
            villageName = "Desa Jugo",
            defaultUsername = "modin.jugo",
            defaultPassword = "donorojo05",
            defaultOfficerName = "Modin Desa Jugo"
        ),
        OfficialModinPreset(
            accountId = "modin_sumberrejo",
            villageCode = "sumberrejo",
            villageName = "Desa Sumberrejo",
            defaultUsername = "modin.sumberrejo",
            defaultPassword = "donorojo06",
            defaultOfficerName = "Modin Desa Sumberrejo"
        ),
        OfficialModinPreset(
            accountId = "modin_tulakan",
            villageCode = "tulakan",
            villageName = "Desa Tulakan",
            defaultUsername = "modin.tulakan",
            defaultPassword = "donorojo07",
            defaultOfficerName = "Modin Desa Tulakan"
        ),
        OfficialModinPreset(
            accountId = "modin_ujungwatu",
            villageCode = "ujungwatu",
            villageName = "Desa Ujungwatu",
            defaultUsername = "modin.ujungwatu",
            defaultPassword = "donorojo08",
            defaultOfficerName = "Modin Desa Ujungwatu"
        )
    )
}

fun todayIsoDate(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return formatter.format(Date())
}

fun daysUntilWedding(isoDate: String): Int? {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val wedding = parser.parse(isoDate) ?: return null
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calWedding = Calendar.getInstance().apply {
            time = wedding
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = calWedding.timeInMillis - calToday.timeInMillis
        (diffMillis / (24L * 60L * 60L * 1000L)).toInt()
    } catch (_: Exception) {
        null
    }
}

fun formatIndonesianDate(isoDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = parser.parse(isoDate) ?: return isoDate
        val formatter = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))
        formatter.format(parsed)
    } catch (_: Exception) {
        isoDate
    }
}

fun formatShortTimestamp(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) return "Baru saja"
    return try {
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        formatter.format(Date(timestampMillis))
    } catch (_: Exception) {
        "Baru saja"
    }
}

fun generateRegistrationNumber(existingCount: Int): String {
    val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
    val seq = (existingCount + 1).toString().padStart(3, '0')
    return "KUA-DNR/$year/$seq"
}
