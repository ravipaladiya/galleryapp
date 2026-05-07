package com.grow.gallery.core.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDuration(): String {
    val totalSec = this / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

// 1024-based to match Android Settings → Storage display
fun Long.toFormattedSize(): String {
    return when {
        this >= 1_073_741_824L -> "%.1f GB".format(this / 1_073_741_824.0)
        this >= 1_048_576L -> "%.1f MB".format(this / 1_048_576.0)
        this >= 1_024L -> "%.0f KB".format(this / 1_024.0)
        else -> "$this B"
    }
}

fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

fun Context.openMediaManageSettings() {
    // ACTION_REQUEST_MANAGE_MEDIA (API 31+) opens the dedicated MANAGE_MEDIA consent screen.
    // Pre-API-31 falls back to app details (MANAGE_MEDIA doesn't exist there anyway).
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.canManageMedia(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        MediaStore.canManageMedia(this)
    else false

fun Context.shareMedia(uri: Uri, mimeType: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData is required for per-URI grants to survive the chooser on Android 11+
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(shareIntent, "Share via"))
}

fun Context.shareMultipleMedia(uris: List<Uri>, mimeType: String = "*/*") {
    if (uris.isEmpty()) return
    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newRawUri("", uris.first()).also { cd ->
            uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(shareIntent, "Share via"))
}

fun Context.setAsWallpaper(uri: Uri, mimeType: String = "image/jpeg") {
    // Resolve wildcard mime to a concrete type so OEM wallpaper pickers accept it
    val resolvedMime = if (mimeType == "image/*") "image/jpeg" else mimeType
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(uri, resolvedMime)
        addCategory(Intent.CATEGORY_DEFAULT)
        putExtra("mimeType", resolvedMime)
        // ClipData carries the URI grant through the chooser (required on API 30+)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(intent, "Set as…"))
}
