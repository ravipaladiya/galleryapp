package com.grow.gallery.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.grow.gallery.app.navigation.*
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.security.VaultManager
import com.grow.gallery.feature.albums.AlbumsScreen
import com.grow.gallery.feature.albums.AlbumDetailScreen
import com.grow.gallery.feature.cleaner.CleanerScreen
import com.grow.gallery.feature.collage.CollageScreen
import com.grow.gallery.feature.editor.EditorScreen
import com.grow.gallery.feature.home.HomeScreen
import com.grow.gallery.feature.map.MapViewScreen
import com.grow.gallery.feature.memories.MemoriesScreen
import com.grow.gallery.feature.onboarding.OnboardingScreen
import com.grow.gallery.feature.premium.PremiumScreen
import com.grow.gallery.feature.search.SearchScreen
import com.grow.gallery.feature.settings.SettingsScreen
import com.grow.gallery.feature.settings.BackupScreen
import com.grow.gallery.feature.settings.AppLockScreen
import com.grow.gallery.feature.slideshow.SlideshowScreen
import com.grow.gallery.feature.splash.SplashScreen
import com.grow.gallery.feature.storage.StorageScreen
import com.grow.gallery.feature.trash.TrashScreen
import com.grow.gallery.feature.vault.VaultScreen
import com.grow.gallery.feature.video.VideoPlayerScreen
import com.grow.gallery.feature.video.VideoTrimmerScreen
import com.grow.gallery.feature.viewer.ViewerScreen
import com.grow.gallery.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        label = "Photos",
        icon = Icons.Outlined.PhotoLibrary,
        selectedIcon = Icons.Filled.PhotoLibrary,
    ),
    BottomNavItem(
        route = Screen.Albums.route,
        label = "Albums",
        icon = Icons.Outlined.GridView,
        selectedIcon = Icons.Filled.GridView,
    ),
    BottomNavItem(
        route = Screen.Search.route,
        label = "Search",
        icon = Icons.Outlined.Search,
        selectedIcon = Icons.Filled.Search,
    ),
    BottomNavItem(
        route = Screen.Memories.route,
        label = "Memories",
        icon = Icons.Outlined.AutoAwesome,
        selectedIcon = Icons.Filled.AutoAwesome,
    ),
)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var vaultManager: VaultManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_GalleryApp)
        enableEdgeToEdge()

        // Handle VIEW intents (e.g. "Open with Gallery" from other apps)
        val viewIntentUri = if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent?.data
        } else null

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appTheme by appViewModel.appTheme.collectAsStateWithLifecycle()

            GalleryTheme(appTheme = appTheme) {
                GalleryApp(
                    appViewModel = appViewModel,
                    vaultManager = vaultManager,
                    activity = this,
                    viewIntentUri = viewIntentUri,
                )
            }
        }
    }
}

@Composable
fun GalleryApp(
    appViewModel: AppViewModel,
    vaultManager: VaultManager,
    activity: MainActivity,
    viewIntentUri: android.net.Uri? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()
    val appLockEnabled by appViewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appLockPinSet by appViewModel.appLockPinSet.collectAsStateWithLifecycle()
    val appLockBiometricEnabled by appViewModel.appLockBiometricEnabled.collectAsStateWithLifecycle()

    // App-lock gate: true = user has authenticated in this session
    var appUnlocked by remember { mutableStateOf(!appLockEnabled) }

    // Re-lock when appLockEnabled turns on
    LaunchedEffect(appLockEnabled) {
        if (appLockEnabled) appUnlocked = false
    }

    // Show app-lock PIN gate if lock is enabled and session is not yet unlocked
    if (appLockEnabled && !appUnlocked && appLockPinSet) {
        AppLockGateScreen(
            biometricEnabled = appLockBiometricEnabled,
            vaultManager = vaultManager,
            activity = activity,
            onUnlocked = { appUnlocked = true },
        )
        return
    }

    // Navigate to Viewer when launched from a VIEW intent (e.g. "Open with Gallery")
    LaunchedEffect(viewIntentUri) {
        viewIntentUri ?: return@LaunchedEffect
        // Wait for the splash → home transition to complete before navigating
        // by resolving the media ID from the URI via the MediaStore
        val mimeType = activity.contentResolver.getType(viewIntentUri) ?: ""
        val isVideo = mimeType.startsWith("video/")
        // Derive a stable media ID from the URI's last path segment (numeric part)
        val mediaId = viewIntentUri.lastPathSegment?.substringAfterLast(":")?.toLongOrNull()
        if (mediaId != null) {
            navController.navigate(Screen.Viewer.createRoute(mediaId, isVideo)) {
                popUpTo(Screen.Home.route) { inclusive = false }
            }
        }
    }

    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = slideInVertically(tween(300)) { it },
                exit = slideOutVertically(tween(300)) { it },
            ) {
                GalleryBottomNav(
                    items = bottomNavItems,
                    currentRoute = currentRoute ?: Screen.Home.route,
                    onItemSelected = { item ->
                        navController.navigate(item.route) {
                            // Use Screen.Home.route rather than startDestinationId (which is Splash)
                            // to prevent back-navigation landing on the splash screen.
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(bottom = if (showBottomNav) paddingValues.calculateBottomPadding() else 0.dp),
            enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 3 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 3 } },
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigate = {
                        val destination = if (onboardingDone == true) Screen.Home.route
                        else Screen.Onboarding.route
                        navController.navigate(destination) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenViewer = { mediaId, isVideo ->
                        navController.navigate(Screen.Viewer.createRoute(mediaId, isVideo))
                    },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenVault = { navController.navigate(Screen.Vault.route) },
                    onOpenCleaner = { navController.navigate(Screen.Cleaner.route) },
                    onOpenStorage = { navController.navigate(Screen.Storage.route) },
                    onOpenPremium = { navController.navigate(Screen.Premium.route) },
                    onOpenTrash = { navController.navigate(Screen.Trash.route) },
                )
            }

            composable(Screen.Albums.route) {
                AlbumsScreen(
                    onOpenAlbum = { albumId, albumName ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId, albumName))
                    },
                )
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.LongType },
                    navArgument("albumName") { type = NavType.StringType },
                ),
            ) { backStack ->
                val albumId = backStack.arguments?.getLong("albumId") ?: 0L
                val albumName = backStack.arguments?.getString("albumName") ?: ""
                AlbumDetailScreen(
                    albumId = albumId,
                    albumName = java.net.URLDecoder.decode(albumName, "UTF-8"),
                    onOpenViewer = { mediaId, isVideo ->
                        navController.navigate(Screen.Viewer.createRoute(mediaId, isVideo))
                    },
                    onOpenSlideshow = { aid ->
                        navController.navigate(Screen.Slideshow.createRoute(aid))
                    },
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onOpenViewer = { mediaId, isVideo ->
                        navController.navigate(Screen.Viewer.createRoute(mediaId, isVideo))
                    },
                    onOpenMapView = { navController.navigate(Screen.MapView.route) },
                )
            }

            composable(Screen.Memories.route) {
                MemoriesScreen(
                    onOpenViewer = { mediaId, isVideo ->
                        navController.navigate(Screen.Viewer.createRoute(mediaId, isVideo))
                    },
                    onOpenSlideshow = { mediaIds ->
                        navController.navigate(Screen.Slideshow.createRoute(mediaIds = mediaIds))
                    },
                )
            }

            composable(
                route = Screen.Viewer.route,
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.LongType },
                    navArgument("isVideo") { type = NavType.BoolType },
                ),
            ) { backStack ->
                val mediaId = backStack.arguments?.getLong("mediaId") ?: 0L
                val isVideo = backStack.arguments?.getBoolean("isVideo") ?: false
                ViewerScreen(
                    mediaId = mediaId,
                    isVideo = isVideo,
                    onNavigateUp = navController::navigateUp,
                    onOpenEditor = { navController.navigate(Screen.Editor.createRoute(mediaId)) },
                    onOpenVideoPlayer = { navController.navigate(Screen.VideoPlayer.createRoute(mediaId)) },
                    onOpenTrimmer = { navController.navigate(Screen.VideoTrimmer.createRoute(mediaId)) },
                )
            }

            composable(
                route = Screen.VideoPlayer.route,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) { backStack ->
                val mediaId = backStack.arguments?.getLong("mediaId") ?: 0L
                VideoPlayerScreen(
                    mediaId = mediaId,
                    onNavigateUp = navController::navigateUp,
                    onOpenTrimmer = { navController.navigate(Screen.VideoTrimmer.createRoute(mediaId)) },
                )
            }

            composable(
                route = Screen.VideoTrimmer.route,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) { backStack ->
                val mediaId = backStack.arguments?.getLong("mediaId") ?: 0L
                VideoTrimmerScreen(
                    mediaId = mediaId,
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(
                route = Screen.Editor.route,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
            ) { backStack ->
                val mediaId = backStack.arguments?.getLong("mediaId") ?: 0L
                EditorScreen(
                    mediaId = mediaId,
                    onNavigateUp = navController::navigateUp,
                    onOpenPremium = { navController.navigate(Screen.Premium.route) },
                )
            }

            composable(Screen.Vault.route) {
                VaultScreen(
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.Cleaner.route) {
                CleanerScreen(
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.Premium.route) {
                PremiumScreen(
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateUp = navController::navigateUp,
                    onOpenVault = { navController.navigate(Screen.Vault.route) },
                    onOpenPremium = { navController.navigate(Screen.Premium.route) },
                    onOpenStorage = { navController.navigate(Screen.Storage.route) },
                    onOpenBackup = { navController.navigate(Screen.Backup.route) },
                    onOpenAppLock = { navController.navigate(Screen.AppLock.route) },
                )
            }

            composable(Screen.Trash.route) {
                TrashScreen(
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.Storage.route) {
                StorageScreen(
                    onNavigateUp = navController::navigateUp,
                    onOpenCleaner = { navController.navigate(Screen.Cleaner.route) },
                    onOpenTrash = { navController.navigate(Screen.Trash.route) },
                )
            }

            composable(Screen.Collage.route) {
                CollageScreen(
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(
                route = Screen.Slideshow.route,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.LongType },
                    navArgument("mediaIds") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val albumId = backStack.arguments?.getLong("albumId") ?: -1L
                val rawIds = backStack.arguments?.getString("mediaIds") ?: ""
                val mediaIds = if (rawIds.isBlank()) emptyList()
                else rawIds.split(",").mapNotNull { it.toLongOrNull() }
                SlideshowScreen(
                    albumId = albumId,
                    mediaIds = mediaIds,
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.MapView.route) {
                MapViewScreen(
                    onNavigateUp = navController::navigateUp,
                    onOpenViewer = { mediaId, isVideo ->
                        navController.navigate(Screen.Viewer.createRoute(mediaId, isVideo))
                    },
                )
            }

            composable(Screen.Backup.route) {
                BackupScreen(
                    onNavigateUp = navController::navigateUp,
                )
            }

            composable(Screen.AppLock.route) {
                AppLockScreen(
                    onNavigateUp = navController::navigateUp,
                    onSetupPin = { navController.navigate(Screen.Vault.route) },
                )
            }
        }
    }
}

/** Gate screen shown on launch when App Lock is enabled and the session is not yet authenticated. */
@Composable
private fun AppLockGateScreen(
    biometricEnabled: Boolean,
    vaultManager: VaultManager,
    activity: MainActivity,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.VaultBlueGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xxxl),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                "Gallery Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Enter your PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxxl))

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (i < pin.length) Color.White else Color.White.copy(alpha = 0.3f),
                                CircleShape,
                            ),
                    )
                }
            }
            error?.let { err ->
                Spacer(Modifier.height(Spacing.md))
                Text(err, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.xxxl))

            // PIN pad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxxl)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Box(modifier = Modifier.size(64.dp))
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        if (key == "⌫") {
                                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        } else if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                scope.launch {
                                                    val correct = vaultManager.verifyPin(pin)
                                                    if (correct) {
                                                        onUnlocked()
                                                    } else {
                                                        error = "Incorrect PIN"
                                                        pin = ""
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                ) {
                                    if (key == "⌫") {
                                        Icon(Icons.Default.Backspace, "Delete", tint = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text(key, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (biometricEnabled) {
                Spacer(Modifier.height(Spacing.xl))
                TextButton(onClick = {
                    val executor = ContextCompat.getMainExecutor(activity)
                    val callback = object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onUnlocked()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                error = errString.toString()
                            }
                        }
                        override fun onAuthenticationFailed() {}
                    }
                    BiometricPrompt(activity, executor, callback).authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Gallery")
                            .setSubtitle("Use biometric to access your gallery")
                            .setNegativeButtonText("Use PIN")
                            .build()
                    )
                }) {
                    Icon(Icons.Default.Fingerprint, null, tint = Color.White)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Use Biometric", color = Color.White)
                }
            }
        }
    }
}
