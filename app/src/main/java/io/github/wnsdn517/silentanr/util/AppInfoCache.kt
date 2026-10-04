package io.github.wnsdn517.silentanr.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/** Caches app labels/icons safely; PackageManager lookups are slow and called from list rows. */
object AppInfoCache {
    private val labels = ConcurrentHashMap<String, String>()
    private val system = ConcurrentHashMap<String, Boolean>()

    // LruCache for ImageBitmap to avoid memory leak & OOM when scrolling quickly
    private val iconCache = object : LruCache<String, ImageBitmap>(150) {}
    private val lock = Any()

    fun label(context: Context, packageName: String): String = labels.getOrPut(packageName) {
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Throwable) {
            packageName
        }
    }

    fun iconBitmap(context: Context, packageName: String): ImageBitmap? {
        synchronized(lock) {
            iconCache.get(packageName)?.let { return it }
        }
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = synchronized(drawable) {
                drawable.toSafeBitmap(96, 96)
            }
            val imageBitmap = bitmap?.asImageBitmap()
            if (imageBitmap != null) {
                synchronized(lock) {
                    iconCache.put(packageName, imageBitmap)
                }
            }
            imageBitmap
        } catch (e: Throwable) {
            null
        }
    }

    private fun Drawable.toSafeBitmap(width: Int, height: Int): Bitmap? {
        return runCatching {
            if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
                if (bitmap.width == width && bitmap.height == height) {
                    return bitmap
                }
                return Bitmap.createScaledBitmap(bitmap, width, height, true)
            }
            val w = if (intrinsicWidth > 0) intrinsicWidth else width
            val h = if (intrinsicHeight > 0) intrinsicHeight else height
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrNull()
    }

    /** System and updated-system apps; unknown packages count as not system. */
    fun isSystem(context: Context, packageName: String): Boolean = system.getOrPut(packageName) {
        try {
            isSystem(context.packageManager.getApplicationInfo(packageName, 0))
        } catch (e: Throwable) {
            false
        }
    }

    fun isSystem(info: ApplicationInfo) =
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
