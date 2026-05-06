package com.grow.gallery.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.grow.gallery.app.navigation.*
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
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
import dagger.hilt.android.AndroidEntryPoint

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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appTheme by appViewModel.appTheme.collectAsStateWithLifecycle()

            GalleryTheme(appTheme = appTheme) {
                GalleryApp(appViewModel = appViewModel)
            }
        }
    }
}

@Composable
fun GalleryApp(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()

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
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
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
                    onOpenSlideshow = {
                        navController.navigate(Screen.Slideshow.createRoute())
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
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            ) { backStack ->
                val albumId = backStack.arguments?.getLong("albumId") ?: -1L
                SlideshowScreen(
                    albumId = albumId,
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
                )
            }
        }
    }
}
