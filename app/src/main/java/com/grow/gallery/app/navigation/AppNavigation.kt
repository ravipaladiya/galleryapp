package com.grow.gallery.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Albums : Screen("albums")
    object Search : Screen("search")
    object Memories : Screen("memories")
    object AlbumDetail : Screen("album/{albumId}/{albumName}") {
        fun createRoute(albumId: Long, albumName: String) = "album/$albumId/${albumName.encode()}"
    }
    object Viewer : Screen("viewer/{mediaId}/{isVideo}") {
        fun createRoute(mediaId: Long, isVideo: Boolean) = "viewer/$mediaId/$isVideo"
    }
    object VideoPlayer : Screen("video/{mediaId}") {
        fun createRoute(mediaId: Long) = "video/$mediaId"
    }
    object VideoTrimmer : Screen("trimmer/{mediaId}") {
        fun createRoute(mediaId: Long) = "trimmer/$mediaId"
    }
    object Editor : Screen("editor/{mediaId}") {
        fun createRoute(mediaId: Long) = "editor/$mediaId"
    }
    object Vault : Screen("vault")
    object Cleaner : Screen("cleaner")
    object Premium : Screen("premium")
    object Settings : Screen("settings")
    object Trash : Screen("trash")
    object Storage : Screen("storage")
    object Collage : Screen("collage")
    object Slideshow : Screen("slideshow/{albumId}/{mediaIds}") {
        /**
         * Navigate to a slideshow.
         * @param albumId  Real album id (>0) or -1 for all-media.
         * @param mediaIds Specific media ids to play (memory group). Empty list means use albumId.
         */
        fun createRoute(albumId: Long = -1L, mediaIds: List<Long> = emptyList()): String {
            val encoded = if (mediaIds.isEmpty()) "" else mediaIds.joinToString(",")
            return "slideshow/$albumId/${encoded.encode()}"
        }
    }
    object MapView : Screen("map")
    object Backup : Screen("backup")
    object AppLock : Screen("applock")
}

val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Albums.route,
    Screen.Search.route,
    Screen.Memories.route,
)

private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")
