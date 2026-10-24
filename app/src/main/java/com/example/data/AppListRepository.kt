package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppListRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        val appList = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName) continue // Exclude Floating Ball app itself from target list
            if (seenPackages.contains(pkg)) continue
            seenPackages.add(pkg)

            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val label = resolveInfo.loadLabel(packageManager).toString()
                val icon = resolveInfo.loadIcon(packageManager)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val pkgInfo = try {
                    packageManager.getPackageInfo(pkg, 0)
                } catch (e: Exception) {
                    null
                }

                appList.add(
                    AppInfo(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        isSystemApp = isSystem,
                        versionName = pkgInfo?.versionName ?: ""
                    )
                )
            } catch (e: Exception) {
                // Ignore apps that failed to load
            }
        }

        // Sort: user apps first, then alphabetical by name
        appList.sortedWith(
            compareBy<AppInfo> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
