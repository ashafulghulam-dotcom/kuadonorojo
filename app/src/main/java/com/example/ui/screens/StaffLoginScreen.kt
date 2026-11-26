package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.EmeraldPrimary

private const val KUA_INSTAGRAM_URL = "https://www.instagram.com/kuadonorojo7?cnvx=dzZ5Z2M0MzZramtk"
private const val KUA_TIKTOK_URL = "https://www.tiktok.com/@kua.donorojo?_r=1&_t=ZS-9BiybvCMgM8"

@Composable
fun StaffLoginScreen(
    staffAccountsState: UiState<List<StaffAccount>>,
    loginError: String?,
    onClearError: () -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoadingAccounts = staffAccountsState is UiState.Loading
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .drawBehind {
                // Soft refined institutional atmospheric halos on pure white canvas
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.width * 0.85f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ChampagneAccent.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.08f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                    radius = size.width * 0.55f
                )
                // Ultra-fine decorative architectural rings behind crest
                val crestCenter = Offset(size.width * 0.5f, size.height * 0.15f)
                drawCircle(
                    color = EmeraldPrimary.copy(alpha = 0.055f),
                    radius = size.width * 0.30f,
                    center = crestCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = ChampagneAccent.copy(alpha = 0.06f),
                    radius = size.width * 0.44f,
                    center = crestCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Official KUA Emblem directly on clean white canvas (no box background)
            Image(
                painter = painterResource(id = R.drawable.ic_logo_kua_resmi),
                contentDescription = stringResource(R.string.cd_app_emblem),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(92.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "KUA DONOROJO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 27.sp,
                    letterSpacing = 0.8.sp
                ),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Portal Layanan Jadwal Nikah, Penghulu & Modin Desa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Premium Floating Login Card
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Subtle top institutional emerald-gold ribbon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        primaryColor,
                                        primaryColor.copy(alpha = 0.72f),
                                        ChampagneAccent.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Masuk Akun Petugas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gunakan username dan password resmi Admin KUA, Penghulu, atau Modin Desa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.trim()
                                if (loginError != null) onClearError()
                            },
                            label = { Text("Username") },
                            placeholder = { Text("Masukkan username petugas") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = primaryColor
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = outlineColor,
                                focusedLabelColor = primaryColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (loginError != null) onClearError()
                            },
                            label = { Text("Password") },
                            placeholder = { Text("Masukkan password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = primaryColor
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.cd_toggle_password),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onLogin(username, password) }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = outlineColor,
                                focusedLabelColor = primaryColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        AnimatedVisibility(
                            visible = loginError != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = loginError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Premium Gradient CTA Button
                        Button(
                            onClick = { onLogin(username, password) },
                            enabled = !isLoadingAccounts,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = primaryColor.copy(alpha = 0.45f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(0.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 1.dp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("login_submit_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                EmeraldPrimary,
                                                Color(0xFF0A523B)
                                            )
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isLoadingAccounts) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Menyiapkan akun...",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = "Masuk ke Dasbor",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))
            Spacer(modifier = Modifier.height(28.dp))

            // Minimalist Institutional Footer with Official Social Media Logos Only
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.width(120.dp),
                    color = outlineColor.copy(alpha = 0.8f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialLogoIconButton(
                        iconRes = R.drawable.ic_social_instagram,
                        contentDescription = stringResource(R.string.cd_social_instagram),
                        testTagName = "social_instagram_button",
                        onClick = { openExternalProfile(context, KUA_INSTAGRAM_URL) }
                    )
                    SocialLogoIconButton(
                        iconRes = R.drawable.ic_social_tiktok,
                        contentDescription = stringResource(R.string.cd_social_tiktok),
                        testTagName = "social_tiktok_button",
                        onClick = { openExternalProfile(context, KUA_TIKTOK_URL) }
                    )
                }

                Text(
                    text = "KUA Donorojo • Layanan Digital Terpadu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SocialLogoIconButton(
    iconRes: Int,
    contentDescription: String,
    testTagName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(50.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun openExternalProfile(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Ignore if no browser or social app is available on the device
    }
}
