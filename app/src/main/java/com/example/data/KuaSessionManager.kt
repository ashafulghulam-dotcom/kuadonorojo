package com.example.data

import android.content.Context

data class SavedStaffSession(
    val staffAccountId: String,
    val dashboardMode: DashboardMode,
    val boundGoogleUid: String
)

class KuaSessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveSession(
        staffAccountId: String,
        dashboardMode: DashboardMode,
        googleUid: String,
        rememberSession: Boolean
    ) {
        if (!rememberSession) {
            clearSession()
            return
        }
        prefs.edit()
            .putString(KEY_STAFF_ACCOUNT_ID, staffAccountId)
            .putString(KEY_DASHBOARD_MODE, dashboardMode.name)
            .putString(KEY_BOUND_GOOGLE_UID, googleUid)
            .putBoolean(KEY_REMEMBER_SESSION, true)
            .apply()
    }

    fun updateDashboardMode(dashboardMode: DashboardMode) {
        if (prefs.getBoolean(KEY_REMEMBER_SESSION, false) &&
            !prefs.getString(KEY_STAFF_ACCOUNT_ID, null).isNullOrBlank()
        ) {
            prefs.edit()
                .putString(KEY_DASHBOARD_MODE, dashboardMode.name)
                .apply()
        }
    }

    fun getSavedSession(forGoogleUid: String? = null): SavedStaffSession? {
        if (!prefs.getBoolean(KEY_REMEMBER_SESSION, false)) return null
        val staffId = prefs.getString(KEY_STAFF_ACCOUNT_ID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val boundUid = prefs.getString(KEY_BOUND_GOOGLE_UID, "").orEmpty()
        if (!forGoogleUid.isNullOrBlank() && boundUid.isNotBlank() && boundUid != forGoogleUid) {
            return null
        }
        val modeRaw = prefs.getString(KEY_DASHBOARD_MODE, DashboardMode.ADMIN_KUA.name)
        val mode = runCatching { DashboardMode.valueOf(modeRaw ?: DashboardMode.ADMIN_KUA.name) }
            .getOrDefault(DashboardMode.ADMIN_KUA)
        return SavedStaffSession(
            staffAccountId = staffId,
            dashboardMode = mode,
            boundGoogleUid = boundUid
        )
    }

    fun isRememberSessionPreferred(): Boolean =
        prefs.getBoolean(KEY_REMEMBER_DEFAULT, true)

    fun setRememberSessionPreferred(remember: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_DEFAULT, remember).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_STAFF_ACCOUNT_ID)
            .remove(KEY_DASHBOARD_MODE)
            .remove(KEY_BOUND_GOOGLE_UID)
            .putBoolean(KEY_REMEMBER_SESSION, false)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "kua_donorojo_session_prefs"
        const val KEY_STAFF_ACCOUNT_ID = "saved_staff_account_id"
        const val KEY_DASHBOARD_MODE = "saved_dashboard_mode"
        const val KEY_BOUND_GOOGLE_UID = "bound_google_uid"
        const val KEY_REMEMBER_SESSION = "remember_session_active"
        const val KEY_REMEMBER_DEFAULT = "remember_session_default"
    }
}
