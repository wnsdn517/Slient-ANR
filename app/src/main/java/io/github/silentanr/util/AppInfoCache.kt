package io.github.silentanr.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

/** Caches app labels/icons; PackageManager lookups are slow and called from list rows. */
object AppInfoCache {
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Drawable>()
    private val system = ConcurrentHashMap<String, Boolean>()

    fun label(context: Context, packageName: String): String = labels.getOrPut(packageName) {
        val pm = context.packageManager
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun icon(context: Context, packageName: String): Drawable? = icons[packageName] ?: try {
        context.packageManager.getApplicationIcon(packageName).also { icons[packageName] = it }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** System and updated-system apps; unknown packages count as not system. */
    fun isSystem(context: Context, packageName: String): Boolean = system.getOrPut(packageName) {
        try {
            isSystem(context.packageManager.getApplicationInfo(packageName, 0))
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isSystem(info: ApplicationInfo) =
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
