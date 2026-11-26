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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.data.KuaLocalDatabase
import com.example.data.KuaRepository
import com.example.data.UserRole
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.ModinDashboardScreen
import com.example.ui.screens.StaffLoginScreen
import com.example.ui.theme.KuaDonorojoTheme
import com.example.util.SmartReminderManager
import com.example.viewmodel.KuaViewModel

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
    val viewModel: KuaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dao = KuaLocalDatabase.getInstance(app).kuaDao()
                KuaViewModel(KuaRepository(dao))
            }
        }
    )

    val showSplash by viewModel.showSplash.collectAsStateWithLifecycle()
    val activeStaffAccount by viewModel.activeStaffAccount.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val dashboardMode by viewModel.dashboardMode.collectAsStateWithLifecycle()
    val previewVillageId by viewModel.previewVillageId.collectAsStateWithLifecycle()
    val villagesState by viewModel.villagesState.collectAsStateWithLifecycle()
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val staffAccountsState by viewModel.staffAccountsState.collectAsStateWithLifecycle()
    val reminderSettingsState by viewModel.reminderSettingsState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        val staffAccount = activeStaffAccount
        if (staffAccount == null) {
            StaffLoginScreen(
                staffAccountsState = staffAccountsState,
                loginError = loginError,
                onClearError = viewModel::clearLoginError,
                onLogin = viewModel::loginWithCredentials
            )
        } else {
            val isStaffAdmin = staffAccount.resolvedRole() == UserRole.ADMIN
            if (isStaffAdmin && dashboardMode == DashboardMode.ADMIN_KUA) {
                AdminDashboardScreen(
                    activeAccount = staffAccount,
                    villagesState = villagesState,
                    schedulesState = schedulesState,
                    staffAccountsState = staffAccountsState,
                    reminderSettingsState = reminderSettingsState,
                    isSaving = isSaving,
                    actionMessage = actionMessage,
                    onClearMessage = viewModel::clearMessage,
                    onPostMessage = viewModel::postMessage,
                    onSwitchMode = viewModel::switchDashboardMode,
                    onLogoutStaff = viewModel::logoutStaffSession,
                    onSaveSchedule = viewModel::saveSchedule,
                    onUpdateVerification = viewModel::updateVerification,
                    onDeleteSchedule = viewModel::deleteSchedule,
                    onCreateModinAccount = viewModel::createModinAccount,
                    onUpdateStaffAccount = viewModel::updateStaffAccount,
                    onDeleteStaffAccount = viewModel::deleteStaffAccount,
                    onSaveReminderSettings = viewModel::saveReminderSettings
                )
            } else {
                ModinDashboardScreen(
                    activeAccount = staffAccount,
                    isAdminPreview = isStaffAdmin,
                    villagesState = villagesState,
                    schedulesState = schedulesState,
                    reminderSettingsState = reminderSettingsState,
                    previewVillageId = previewVillageId,
                    onSelectPreviewVillage = viewModel::selectPreviewVillage,
                    onSwitchMode = viewModel::switchDashboardMode,
                    onLogoutStaff = viewModel::logoutStaffSession
                )
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 320))
        ) {
            KuaFullWhiteSplashOverlay()
        }
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
            modifier = Modifier.size(148.dp)
        )
    }
}
