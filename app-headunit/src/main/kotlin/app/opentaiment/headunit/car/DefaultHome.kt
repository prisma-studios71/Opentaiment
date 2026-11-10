package app.opentaiment.headunit.car

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Being the device's home screen (launcher). Android never lets an app take this silently –
 * the user confirms once, in a system dialog (Android 10+) or in the "home app" settings.
 */
object DefaultHome {

    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    /** The system "use Opentaiment as home app?" dialog, where the device offers it. */
    fun roleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /** Fallback: the "default home app" settings page (or the general settings if a ROM hides it). */
    fun settingsIntent(context: Context): Intent {
        val home = Intent(Settings.ACTION_HOME_SETTINGS)
        return if (home.resolveActivity(context.packageManager) != null) home
        else Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).takeIf { it.resolveActivity(context.packageManager) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
    }
}
