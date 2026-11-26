package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.DashboardMode
import com.example.data.KuaFirestoreSyncService
import com.example.data.KuaLocalDatabase
import com.example.data.KuaRepository
import com.example.data.KuaSessionManager
import com.example.data.UserRole
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CloudAuthScreen
import com.example.ui.screens.ModinDashboardScreen
import com.example.ui.screens.PenghuluDashboardScreen
import com.example.ui.screens.StaffLoginScreen
import com.example.ui.theme.KuaDonorojoTheme
import com.example.util.BackgroundReminderScheduler
import com.example.util.GoogleAuthHelper
import com.example.util.GoogleAuthHelper.authStateFlow
import com.example.util.SmartReminderManager
import com.example.viewmodel.KuaViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SmartReminderManager.ensureNotificationChannel(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            KuaDonorojoTheme {
                KuaAppRoot()
            }
        }
    }
}

@Composable
fun KuaAppRoot() {
    val context = LocalContext.current
    val isFirebaseReady = remember(context) {
        try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    if (!isFirebaseReady) {
        KuaAuthenticatedRoot(user = null)
        return
    }

    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsState(initial = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        CloudAuthScreen()
    } else {
        key(user.uid) {
            KuaAuthenticatedRoot(user = user)
        }
    }
}

@Composable
private fun KuaAuthenticatedRoot(user: FirebaseUser?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val workspaceKey = user?.uid ?: "local_device_workspace"

    val viewModel: KuaViewModel = viewModel(
        key = workspaceKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dao = KuaLocalDatabase.getInstance(app).kuaDao()
                val dbIdRes = app.resources.getIdentifier(
                    "firestore_database_id",
                    "string",
                    app.packageName
                )
                val dbId = if (dbIdRes != 0) app.getString(dbIdRes).trim() else ""
                val syncService = if (user != null && dbId.isNotBlank()) {
                    val firestore = FirebaseFirestore.getInstance(dbId)
                    KuaFirestoreSyncService(firestore)
                } else {
                    null
                }
                val repository = KuaRepository(dao = dao, firestoreSync = syncService)
                val sessionManager = KuaSessionManager(app)
                KuaViewModel(
                    repository = repository,
                    sessionManager = sessionManager,
                    firestoreSync = syncService,
                    currentUserId = workspaceKey,
                    currentUserEmail = user?.email.orEmpty(),
                    onScheduleReminderAlarm = { settings ->
                        BackgroundReminderScheduler.scheduleDailyReminder(app, settings)
                    }
                )
            }
        }
    )

    val showSplash by viewModel.showSplash.collectAsStateWithLifecycle()
    val activeStaffAccount by viewModel.activeStaffAccount.collectAsStateWithLifecycle()
    val rememberSessionDefault by viewModel.rememberSessionDefault.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val dashboardMode by viewModel.dashboardMode.collectAsStateWithLifecycle()
    val previewModinId by viewModel.previewModinId.collectAsStateWithLifecycle()
    val previewPenghuluId by viewModel.previewPenghuluId.collectAsStateWithLifecycle()
    val villagesState by viewModel.villagesState.collectAsStateWithLifecycle()
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val staffAccountsState by viewModel.staffAccountsState.collectAsStateWithLifecycle()
    val reminderSettingsState by viewModel.reminderSettingsState.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val signOutGoogleAndStaff: () -> Unit = {
        showLogoutDialog = false
        viewModel.logoutStaffSession()
        if (user != null) {
            scope.launch {
                GoogleAuthHelper.signOutGoogle(context)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val staffAccount = activeStaffAccount
        if (staffAccount == null) {
            StaffLoginScreen(
                staffAccountsState = staffAccountsState,
                loginError = loginError,
                rememberSessionDefault = rememberSessionDefault,
                connectedGoogleEmail = user?.email.orEmpty(),
                onSignOutGoogle = signOutGoogleAndStaff,
                onClearError = viewModel::clearLoginError,
                onLogin = viewModel::loginWithCredentials
            )
        } else {
            val isStaffAdmin = staffAccount.resolvedRole() == UserRole.ADMIN
            val isStaffPenghulu = staffAccount.resolvedRole() == UserRole.PENGHULU
            when {
                isStaffAdmin && dashboardMode == DashboardMode.ADMIN_KUA -> {
                    AdminDashboardScreen(
                        activeAccount = staffAccount,
                        villagesState = villagesState,
                        schedulesState = schedulesState,
                        staffAccountsState = staffAccountsState,
                        reminderSettingsState = reminderSettingsState,
                        cloudSyncStatus = cloudSyncStatus,
                        isSaving = isSaving,
                        actionMessage = actionMessage,
                        onClearMessage = viewModel::clearMessage,
                        onPostMessage = viewModel::postMessage,
                        onSwitchMode = viewModel::switchDashboardMode,
                        onLogoutStaff = { showLogoutDialog = true },
                        onTriggerCloudSync = viewModel::triggerManualCloudSync,
                        onRestoreBackup = viewModel::restoreFromBackup,
                        onSaveSchedule = viewModel::saveSchedule,
                        onUpdateVerification = viewModel::updateVerification,
                        onDeleteSchedule = viewModel::deleteSchedule,
                        onCreateModinAccount = viewModel::createModinAccount,
                        onCreatePenghuluAccount = viewModel::createPenghuluAccount,
                        onUpdateStaffAccount = viewModel::updateStaffAccount,
                        onDeleteStaffAccount = viewModel::deleteStaffAccount,
                        onSaveReminderSettings = viewModel::saveReminderSettings,
                        onSaveHeadOfKuaSettings = viewModel::saveHeadOfKuaSettings,
                        onSaveWaliHakimSettings = viewModel::saveWaliHakimSettings
                    )
                }

                isStaffPenghulu || (isStaffAdmin && dashboardMode == DashboardMode.PENGHULU) -> {
                    PenghuluDashboardScreen(
                        activeAccount = staffAccount,
                        isAdminPreview = isStaffAdmin,
                        staffAccountsState = staffAccountsState,
                        schedulesState = schedulesState,
                        reminderSettingsState = reminderSettingsState,
                        cloudSyncStatus = cloudSyncStatus,
                        previewPenghuluId = previewPenghuluId,
                        isSaving = isSaving,
                        actionMessage = actionMessage,
                        onClearMessage = viewModel::clearMessage,
                        onSelectPreviewPenghulu = viewModel::selectPreviewPenghulu,
                        onUpdateOwnProfile = viewModel::updateOwnProfile,
                        onSwitchMode = viewModel::switchDashboardMode,
                        onLogoutStaff = { showLogoutDialog = true }
                    )
                }

                else -> {
                    ModinDashboardScreen(
                        activeAccount = staffAccount,
                        isAdminPreview = isStaffAdmin,
                        staffAccountsState = staffAccountsState,
                        schedulesState = schedulesState,
                        reminderSettingsState = reminderSettingsState,
                        cloudSyncStatus = cloudSyncStatus,
                        previewModinId = previewModinId,
                        isSaving = isSaving,
                        actionMessage = actionMessage,
                        onClearMessage = viewModel::clearMessage,
                        onSelectPreviewModin = viewModel::selectPreviewModin,
                        onUpdateOwnProfile = viewModel::updateOwnProfile,
                        onSwitchMode = viewModel::switchDashboardMode,
                        onLogoutStaff = { showLogoutDialog = true }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 320))
        ) {
            KuaFullWhiteSplashOverlay()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Keluar dari Dasbor?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (user != null) {
                            "Pilih keluar dari akun petugas saja (tetap terhubung ke cloud ${user.email.orEmpty()}) atau keluar penuh dari akun Google."
                        } else {
                            "Anda akan keluar dari akun petugas saat ini dan kembali ke halaman Masuk Akun."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            viewModel.logoutStaffSession()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_staff_only_button")
                    ) {
                        Text(if (user != null) "Ganti Akun Petugas" else "Keluar Akun Petugas")
                    }
                    if (user != null) {
                        OutlinedButton(
                            onClick = signOutGoogleAndStaff,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("logout_google_and_staff_button")
                        ) {
                            Text("Keluar Google & Petugas")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun KuaFullWhiteSplashOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("kua_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo_kua_resmi),
            contentDescription = stringResource(R.string.cd_app_emblem),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(136.dp)
        )
    }
}
