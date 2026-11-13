package com.elysium.vanguard.recordshield.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.elysium.vanguard.recordshield.RecordShieldIdentity
import com.elysium.vanguard.recordshield.service.StealthNotificationManager
import com.elysium.vanguard.recordshield.service.UploadWorker
import com.elysium.vanguard.recordshield.ui.auth.GoogleDriveAuth
import com.elysium.vanguard.recordshield.ui.theme.DeepBlack
import com.elysium.vanguard.recordshield.ui.theme.RecordShieldTheme
import com.elysium.vanguard.recordshield.util.SafeLog
import kotlinx.coroutines.launch

/**
 * RECORD SHIELD SECTION
 *
 * The RecordShield experience as a NATIVE section of the host app: the same
 * screens, the same PIN gate, the same vault, but rendered inside the host
 * navigation instead of jumping to a separate activity.
 *
 * The standalone activity (RecordShield MainActivity) reuses this composable,
 * so there is exactly one implementation of the flow.
 */
@Composable
fun RecordShieldSection(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher: works from a composable, so the section can live
    // inside any host activity.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.count { it.value }
        val denied = permissions.count { !it.value }
        SafeLog.i("RecordShield", "Permissions: $granted granted, $denied denied")
    }

    // Google Sign-In launcher (cloud vault).
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = GoogleDriveAuth.handleSignInResult(task)
        if (account == null) {
            android.widget.Toast.makeText(
                context,
                "Google Sign-In failed or cancelled",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }
        val authCode = account.serverAuthCode
        if (authCode == null) {
            android.widget.Toast.makeText(
                context,
                "No auth code received. Check Google Cloud Console config.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        SafeLog.i("RecordShield", "Got auth code, exchanging for tokens...")
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tokenResult = GoogleDriveAuth.exchangeAuthCodeForTokens(authCode, context)
            if (tokenResult != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val secureStorage =
                    com.elysium.vanguard.recordshield.data.local.SecureStorage(context)
                secureStorage.googleDriveAccessToken = tokenResult.accessToken
                tokenResult.refreshToken?.let { secureStorage.googleDriveRefreshToken = it }
                secureStorage.selectedCloudProvider = "google_drive"
                android.widget.Toast.makeText(
                    context,
                    "Google Drive connected: ${account.email}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                SafeLog.i("RecordShield", "DRIVE_TOKEN_STORED")
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Token exchange failed. Check logs.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // One time setup that the standalone activity used to do.
    LaunchedEffect(Unit) {
        StealthNotificationManager.createStealthChannel(context)
        UploadWorker.schedulePeriodicUpload(context)
        requestRecordShieldPermissions(context, permissionLauncher::launch)
    }

    // The vault is bound to the signed in principal.
    if (RecordShieldIdentity.principalId() == null) {
        Column(modifier = modifier.fillMaxSize()) {
            TextButton(onClick = onBack) { Text("← Volver a Elysium") }
            Text("Inicia sesión para abrir tus grabaciones privadas.")
        }
        return
    }

    RecordShieldTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = DeepBlack
        ) {
            Column {
                TextButton(onClick = onBack) { Text("← Volver a Elysium") }
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 0.dp)) {
                    RecordShieldApp(
                        onGoogleDriveSetup = {
                            if (com.elysium.vanguard.recordshield.BuildConfig.OAUTH_CLIENT_ID.isBlank()) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Google Drive aún no está configurado",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val signInClient = GoogleDriveAuth.getSignInClient(context)
                                googleSignInLauncher.launch(signInClient.signInIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Asks for the permissions the vault needs, but only for the ones that are
 * still missing.
 */
private fun requestRecordShieldPermissions(
    context: android.content.Context,
    launch: (Array<String>) -> Unit
) {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val notGranted = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
    if (notGranted.isNotEmpty()) {
        launch(notGranted.toTypedArray())
    }
}
