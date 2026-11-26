package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DEFAULT_ADMIN_USERNAME = "adminkua"
const val DEFAULT_ADMIN_PASSWORD = "kua2026"

enum class UserRole(val code: String, val label: String) {
    ADMIN("ADMIN", "Admin KUA Donorojo"),
    MODIN("MODIN", "Modin Desa");

    companion object {
        fun fromCode(raw: String?): UserRole =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: MODIN
    }
}

enum class DashboardMode {
    ADMIN_KUA,
    MODIN_DESA
}

enum class LocationType(val code: String, val label: String) {
    KUA("KUA", "Balai Nikah KUA"),
    LUAR_KUA("LUAR_KUA", "Luar KUA / Rumah");

    companion object {
        fun fromCode(raw: String?): LocationType =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: KUA
    }
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
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedRole(): UserRole = UserRole.fromCode(role)
}

data class ReminderSettings(
    val id: String = GLOBAL_DOC_ID,
    val autoPhoneNotifEnabled: Boolean = true,
    val whatsappReminderEnabled: Boolean = true,
    val remindOnWeddingDay: Boolean = true,
    val remindOneDayBefore: Boolean = true,
    val remindThreeDaysBefore: Boolean = false,
    val reminderTime: String = "07:00",
    val whatsappTemplate: String = DEFAULT_WA_TEMPLATE,
    val updatedByName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val GLOBAL_DOC_ID = "global_config"
        const val DEFAULT_WA_TEMPLATE =
            "Assalamu'alaikum Wr. Wb.\nYth. {modin} ({desa}),\n\nMengingatkan jadwal akad nikah:\n• Pengantin: {pengantin}\n• No. Daftar: {no_daftar}\n• Tanggal: {tanggal}\n• Jam: {jam} WIB\n• Lokasi: {lokasi}\n• Modin: {modin}\n• Penghulu: {penghulu}\n• Status Validasi KUA: {status}\n\nMohon kesiapan pendampingan di wilayah desa. Terima kasih.\n— KUA Donorojo"
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
    val penghuluName: String = "",
    val validationStatus: String = ValidationStatus.MENUNGGU.code,
    val documentsComplete: Boolean = false,
    val verificationNote: String = "",
    val verifiedByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedStatus(): ValidationStatus = ValidationStatus.fromCode(validationStatus)
    fun resolvedLocationType(): LocationType = LocationType.fromCode(locationType)
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
    val penghuluName: String = "",
    val validationStatus: ValidationStatus = ValidationStatus.MENUNGGU,
    val documentsComplete: Boolean = false,
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

object DonorojoOfficialDirectory {
    const val ADMIN_ACCOUNT_ID = "admin_kua"

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
            villageName = "Desa Sumberrejo (Sumberejo)",
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
