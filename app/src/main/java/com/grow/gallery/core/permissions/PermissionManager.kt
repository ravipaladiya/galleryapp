package com.grow.gallery.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaPermissionState {
    GRANTED,        // Full access
    PARTIAL,        // Android 14+ partial (selected photos)
    DENIED,         // No permission
    NOT_ASKED,      // Never asked
}

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _permissionState = MutableStateFlow(checkCurrentState())
    val permissionState: StateFlow<MediaPermissionState> = _permissionState.asStateFlow()

    fun refresh() {
        _permissionState.value = checkCurrentState()
    }

    fun checkCurrentState(): MediaPermissionState {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+
                val fullImages = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                val fullVideos = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
                val partial = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED

                when {
                    fullImages && fullVideos -> MediaPermissionState.GRANTED
                    partial -> MediaPermissionState.PARTIAL
                    else -> MediaPermissionState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13
                val images = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                val videos = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
                if (images && videos) MediaPermissionState.GRANTED else MediaPermissionState.DENIED
            }
            else -> {
                // Android 12 and below
                val storage = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (storage) MediaPermissionState.GRANTED else MediaPermissionState.DENIED
            }
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasFullAccess(): Boolean = _permissionState.value == MediaPermissionState.GRANTED
    fun hasAnyAccess(): Boolean = _permissionState.value == MediaPermissionState.GRANTED ||
            _permissionState.value == MediaPermissionState.PARTIAL
}
