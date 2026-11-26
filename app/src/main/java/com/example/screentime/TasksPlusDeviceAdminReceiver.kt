package com.example.screentime

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

class TasksPlusDeviceAdminReceiver : DeviceAdminReceiver() {

  override fun onEnabled(context: Context, intent: Intent) {
    super.onEnabled(context, intent)
    Toast.makeText(
      context,
      "Tasks+ Device Admin Guard enabled",
      Toast.LENGTH_SHORT
    ).show()
  }

  override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
    return "Disabling Tasks+ Device Admin will weaken Screen Time Guard protection."
  }

  override fun onDisabled(context: Context, intent: Intent) {
    super.onDisabled(context, intent)
    Toast.makeText(
      context,
      "Tasks+ Device Admin Guard disabled",
      Toast.LENGTH_SHORT
    ).show()
  }

  companion object {
    fun getComponentName(context: Context): ComponentName {
      return ComponentName(context.applicationContext, TasksPlusDeviceAdminReceiver::class.java)
    }

    fun isAdminActive(context: Context): Boolean {
      return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm?.isAdminActive(getComponentName(context)) == true
      } catch (_: Exception) {
        false
      }
    }

    /**
     * Immediately locks the device screen using DevicePolicyManager.lockNow() if Device Admin is granted.
     */
    fun lockDeviceNow(context: Context): Boolean {
      return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm != null && dpm.isAdminActive(getComponentName(context))) {
          dpm.lockNow()
          true
        } else {
          false
        }
      } catch (_: Exception) {
        false
      }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
      is Activity -> this
      is android.content.ContextWrapper -> baseContext.findActivity()
      else -> null
    }

    /**
     * Opens the official Android "Activate device admin app" screen for Tasks+.
     * Falls back to Device Admin settings list if already active or if OEM customizes the intent.
     */
    fun requestDeviceAdminActivation(context: Context) {
      val adminComponent = getComponentName(context)
      val activity = context.findActivity()
      val launchContext: Context = activity ?: context
      if (!isAdminActive(context)) {
        try {
          val addAdminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
              DevicePolicyManager.EXTRA_ADD_EXPLANATION,
              "Tasks+ Screen Time Guard uses Device Administrator to protect your app screen time limits and prevent easy bypass."
            )
            if (launchContext !is Activity) {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          }
          launchContext.startActivity(addAdminIntent)
          return
        } catch (_: Exception) {
        }
      }
      try {
        val deviceAdminSettingsIntent = Intent().apply {
          component = ComponentName(
            "com.android.settings",
            "com.android.settings.DeviceAdminSettings"
          )
          if (launchContext !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
        launchContext.startActivity(deviceAdminSettingsIntent)
      } catch (_: Exception) {
        try {
          val securityIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            if (launchContext !is Activity) {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          }
          launchContext.startActivity(securityIntent)
        } catch (_: Exception) {
        }
      }
    }
  }
}
