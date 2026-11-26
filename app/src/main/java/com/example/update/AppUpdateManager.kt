package com.example.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateUiState(
  val isCheckingChangelog: Boolean = false,
  val changelogText: String = "",
  val changelogError: String? = null,
  val isDownloading: Boolean = false,
  val isInstalling: Boolean = false,
  val needsInstallPermission: Boolean = false,
  val downloadProgress: Float = 0f,
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = 0L,
  val downloadedFilePath: String? = null,
  val statusMessage: String? = null,
  val errorMessage: String? = null
) {
  val isReadyToInstall: Boolean
    get() = !isDownloading && !isInstalling && !downloadedFilePath.isNullOrBlank()
}

class AppUpdateManager(private val context: Context) {

  private val appContext = context.applicationContext
  private val updatePrefs = appContext.getSharedPreferences("tasksplus_update_prefs", Context.MODE_PRIVATE)

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val _state = MutableStateFlow(UpdateUiState())
  val state: StateFlow<UpdateUiState> = _state.asStateFlow()

  init {
    val existing = getTargetApkFile()
    if (existing.exists() && existing.length() > 0L) {
      _state.value = _state.value.copy(
        downloadedFilePath = existing.absolutePath,
        downloadProgress = 1f,
        downloadedBytes = existing.length(),
        totalBytes = existing.length(),
        needsInstallPermission = !canInstallUnknownApps(),
        statusMessage = "Update APK ready (${formatBytes(existing.length())}). Tap 'Update Now' to install."
      )
    }
  }

  private fun getTargetApkFile(): File {
    val baseDir = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      appContext.externalCacheDir ?: appContext.cacheDir
    } else {
      appContext.cacheDir
    }
    val dir = File(baseDir, "updates").apply {
      mkdirs()
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && baseDir == appContext.cacheDir) {
        setReadable(true, false)
        setExecutable(true, false)
      }
    }
    return File(dir, "tasks+.apk")
  }

  private fun getAllPossibleApkFiles(): List<File> {
    return listOfNotNull(
      File(File(appContext.cacheDir, "updates"), "tasks+.apk"),
      appContext.externalCacheDir?.let { File(File(it, "updates"), "tasks+.apk") }
    ).distinctBy { it.absolutePath }
  }

  fun canInstallUnknownApps(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        appContext.packageManager.canRequestPackageInstalls()
      } catch (_: Exception) {
        false
      }
    } else {
      true
    }
  }

  fun openUnknownAppsPermissionSettings(activityOrContext: Context = appContext) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        val intent = Intent(
          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          Uri.parse("package:${appContext.packageName}")
        ).apply {
          if (activityOrContext !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
        activityOrContext.startActivity(intent)
        return
      } catch (_: Exception) {
      }
    }
    try {
      val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      activityOrContext.startActivity(fallback)
    } catch (_: Exception) {
    }
  }

  fun clearCacheOnStartupIfEnabled(autoDeleteEnabled: Boolean) {
    val pendingCleanup = updatePrefs.getBoolean(KEY_PENDING_POST_INSTALL_CLEANUP, false)
    val existingFiles = getAllPossibleApkFiles().filter { it.exists() }
    val targetFile = getTargetApkFile()
    if (pendingCleanup && autoDeleteEnabled && existingFiles.isNotEmpty()) {
      existingFiles.forEach { it.delete() }
      updatePrefs.edit().putBoolean(KEY_PENDING_POST_INSTALL_CLEANUP, false).apply()
      _state.value = _state.value.copy(
        downloadedFilePath = null,
        downloadProgress = 0f,
        downloadedBytes = 0L,
        totalBytes = 0L,
        statusMessage = null
      )
    } else if (targetFile.exists() && targetFile.length() > 0L) {
      _state.value = _state.value.copy(
        downloadedFilePath = targetFile.absolutePath,
        downloadProgress = 1f,
        downloadedBytes = targetFile.length(),
        totalBytes = targetFile.length(),
        needsInstallPermission = !canInstallUnknownApps()
      )
    }
  }

  suspend fun fetchUpdateDescription() {
    _state.value = _state.value.copy(
      isCheckingChangelog = true,
      changelogError = null
    )
    withContext(Dispatchers.IO) {
      try {
        val request = Request.Builder()
          .url(CHANGELOG_URL)
          .header("User-Agent", "TasksPlus-Android/1.0")
          .get()
          .build()

        httpClient.newCall(request).execute().use { response ->
          val rawBody = response.body?.string()?.trim().orEmpty()
          if (response.isSuccessful && rawBody.isNotBlank()) {
            _state.value = _state.value.copy(
              isCheckingChangelog = false,
              changelogText = rawBody,
              changelogError = null
            )
          } else {
            _state.value = _state.value.copy(
              isCheckingChangelog = false,
              changelogText = rawBody.ifBlank {
                "Tasks+ latest build at $APK_URL\n• Repeating priority notifications & alarm bell\n• Screen Time Guard & Device Admin\n• Cloud backup & Material You Tonal Spot themes"
              },
              changelogError = if (!response.isSuccessful) "HTTP ${response.code}" else null
            )
          }
        }
      } catch (e: Exception) {
        _state.value = _state.value.copy(
          isCheckingChangelog = false,
          changelogText = "Unable to reach $CHANGELOG_URL (${e.localizedMessage ?: "Network error"}). You can still download the latest Tasks+ build below.",
          changelogError = e.localizedMessage
        )
      }
    }
  }

  suspend fun downloadUpdateApk() {
    if (_state.value.isDownloading) return
    updatePrefs.edit().putBoolean(KEY_PENDING_POST_INSTALL_CLEANUP, false).apply()
    _state.value = _state.value.copy(
      isDownloading = true,
      downloadProgress = 0.02f,
      downloadedBytes = 0L,
      totalBytes = 0L,
      downloadedFilePath = null,
      errorMessage = null,
      statusMessage = "Connecting to $APK_URL..."
    )

    withContext(Dispatchers.IO) {
      val targetFile = getTargetApkFile()
      try {
        val request = Request.Builder()
          .url(APK_URL)
          .header("User-Agent", "TasksPlus-Android/1.0")
          .get()
          .build()

        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            _state.value = _state.value.copy(
              isDownloading = false,
              downloadProgress = 0f,
              errorMessage = "Server returned HTTP ${response.code}. Try Download APK in Browser below.",
              statusMessage = null
            )
            return@withContext
          }

          val body = response.body ?: throw IllegalStateException("Empty response body")
          val contentLength = body.contentLength()
          val inputStream = body.byteStream()

          FileOutputStream(targetFile).use { output ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
              totalRead += bytesRead
              val progress = if (contentLength > 0L) {
                (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0.05f, 0.99f)
              } else {
                0.65f
              }
              _state.value = _state.value.copy(
                downloadProgress = progress,
                downloadedBytes = totalRead,
                totalBytes = contentLength.coerceAtLeast(totalRead),
                statusMessage = "Downloading tasks+.apk (${formatBytes(totalRead)})..."
              )
            }
            output.flush()
          }
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            targetFile.setReadable(true, false)
          }
        }

        val hasInstallPerm = canInstallUnknownApps()
        _state.value = _state.value.copy(
          isDownloading = false,
          downloadProgress = 1f,
          downloadedFilePath = targetFile.absolutePath,
          needsInstallPermission = !hasInstallPerm,
          statusMessage = if (hasInstallPerm) {
            "Download complete (${formatBytes(targetFile.length())}). Tap 'Update Now' to install."
          } else {
            "Download complete (${formatBytes(targetFile.length())}). Tap 'Update Now' to grant install permission & update."
          }
        )
      } catch (e: Exception) {
        _state.value = _state.value.copy(
          isDownloading = false,
          downloadProgress = 0f,
          downloadedFilePath = null,
          errorMessage = "Direct download interrupted (${e.localizedMessage ?: "Network error"}). Use Browser or GitHub Releases below.",
          statusMessage = null
        )
      }
    }
  }

  /**
   * Installs the downloaded APK file:
   * 1. Checks if "Install unknown apps" permission is granted on Android 8.0+. If not, opens the
   *    system permission screen WITHOUT deleting the downloaded file so the user can tap Update Now
   *    immediately after toggling permission on.
   * 2. Once granted, launches the Android system package installer via FileProvider URI.
   * 3. Keeps the APK file on disk while the external Package Installer reads it, scheduling
   *    automatic cache cleanup for the next startup if `autoDeleteAfterInstall` is enabled.
   */
  suspend fun installDownloadedApk(autoDeleteAfterInstall: Boolean) {
    val targetFile = getTargetApkFile()
    if (!targetFile.exists() || targetFile.length() <= 0L) {
      _state.value = _state.value.copy(
        downloadedFilePath = null,
        downloadProgress = 0f,
        errorMessage = "Downloaded installer file not found. Tap In-App Update to download again."
      )
      return
    }

    // Step 1: Check "Install unknown apps" permission on Android 8.0+ (API 26+)
    if (!canInstallUnknownApps()) {
      openUnknownAppsPermissionSettings(appContext)
      _state.value = _state.value.copy(
        isInstalling = false,
        downloadedFilePath = targetFile.absolutePath,
        downloadProgress = 1f,
        needsInstallPermission = true,
        errorMessage = null,
        statusMessage = "Allow 'Install unknown apps' for Tasks+ in the settings screen, then tap 'Update Now' again to install."
      )
      return
    }

    _state.value = _state.value.copy(
      isInstalling = true,
      needsInstallPermission = false,
      errorMessage = null,
      statusMessage = "Launching Android Package Installer..."
    )

    withContext(Dispatchers.Main) {
      try {
        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            targetFile
          )
        } else {
          Uri.fromFile(targetFile)
        }

        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
          data = apkUri
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
          putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }

        val viewFallbackIntent = Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(apkUri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
          appContext.startActivity(viewFallbackIntent)
        } catch (_: Exception) {
          appContext.startActivity(installIntent)
        }

        // Mark post-install cleanup for next app launch so the system installer can read targetFile now!
        if (autoDeleteAfterInstall) {
          updatePrefs.edit().putBoolean(KEY_PENDING_POST_INSTALL_CLEANUP, true).apply()
        }

        _state.value = _state.value.copy(
          isInstalling = false,
          downloadedFilePath = null,
          downloadProgress = 0f,
          downloadedBytes = 0L,
          totalBytes = 0L,
          statusMessage = if (autoDeleteAfterInstall) {
            "Package installer opened! Installer cache will clear automatically after update."
          } else {
            "Package installer opened!"
          }
        )
      } catch (e: Exception) {
        _state.value = _state.value.copy(
          isInstalling = false,
          downloadedFilePath = targetFile.absolutePath,
          downloadProgress = 1f,
          errorMessage = "Could not open package installer: ${e.localizedMessage ?: "Error"}",
          statusMessage = "Tap 'Update Now' to retry."
        )
      }
    }
  }

  fun deleteDownloadedApk(): Boolean {
    var deleted = true
    getAllPossibleApkFiles().forEach { file ->
      if (file.exists() && !file.delete()) {
        deleted = false
      }
    }
    updatePrefs.edit().putBoolean(KEY_PENDING_POST_INSTALL_CLEANUP, false).apply()
    _state.value = _state.value.copy(
      downloadedFilePath = null,
      downloadProgress = 0f,
      downloadedBytes = 0L,
      totalBytes = 0L,
      statusMessage = "Downloaded installer file deleted from device."
    )
    return deleted
  }

  private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
      String.format(Locale.US, "%.1f KB", kb)
    } else {
      String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }
  }

  companion object {
    private const val KEY_PENDING_POST_INSTALL_CLEANUP = "pending_post_install_cleanup"
    const val APK_URL = "https://edvanced.ct.ws/tasks/tasks+.apk"
    const val CHANGELOG_URL = "https://edvanced.ct.ws/tasks/tasks+.html"
    const val GITHUB_REPO_URL = "https://github.com/frosty-sys/tasksplus"
    const val GITHUB_RELEASES_URL = "https://github.com/frosty-sys/tasksplus/releases"
    const val FROSTY_GITHUB_URL = "https://github.com/frosty-sys"
    const val RNATV_GITHUB_URL = "https://github.com/frosty-sys/rnatv"
    const val NAVSTACK_GITHUB_URL = "https://github.com/frosty-sys/navstack"
  }
}
