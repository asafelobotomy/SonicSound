package app.sonicsound.update

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import app.sonicsound.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV / Activity UI helpers around [AppUpdateChecker].
 */
object AppUpdateUi {
    fun checkAndPrompt(activity: Activity, force: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = AppUpdateChecker.check(force = force)
            withContext(Dispatchers.Main) {
                if (activity.isFinishing) return@withContext
                when (result) {
                    is AppUpdateChecker.CheckResult.UpdateAvailable -> {
                        AppUpdateChecker.markNotified(result.update)
                        Toast.makeText(
                            activity,
                            activity.getString(
                                R.string.update_available_toast,
                                result.update.version,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                        showInstallDialog(activity, result.update)
                    }
                    AppUpdateChecker.CheckResult.UpToDate -> {
                        if (force) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.update_up_to_date),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    is AppUpdateChecker.CheckResult.Failed -> {
                        if (force) {
                            Toast.makeText(
                                activity,
                                activity.getString(
                                    R.string.update_check_failed,
                                    result.message,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    AppUpdateChecker.CheckResult.Skipped -> {
                        // Quiet on auto-check debounce.
                    }
                }
            }
        }
    }

    fun resumePendingIfNeeded(activity: Activity) {
        CoroutineScope(Dispatchers.Main).launch {
            if (activity.isFinishing) return@launch
            runCatching { AppUpdateChecker.resumePendingInstall(activity) }
        }
    }

    private fun showInstallDialog(
        activity: Activity,
        update: AppUpdateChecker.AvailableUpdate,
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_dialog_title)
            .setMessage(
                activity.getString(
                    R.string.update_dialog_message,
                    update.version,
                    AppUpdateChecker.currentVersionName(),
                ),
            )
            .setPositiveButton(R.string.update_install) { _, _ ->
                downloadAndInstall(activity, update)
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun downloadAndInstall(
        activity: Activity,
        update: AppUpdateChecker.AvailableUpdate,
    ) {
        Toast.makeText(
            activity,
            activity.getString(R.string.update_downloading),
            Toast.LENGTH_SHORT,
        ).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apk = AppUpdateChecker.downloadApk(activity, update)
                withContext(Dispatchers.Main) {
                    if (activity.isFinishing) return@withContext
                    val started = AppUpdateChecker.installApk(activity, apk)
                    if (!started) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.update_allow_unknown_sources),
                            Toast.LENGTH_LONG,
                        ).show()
                        AppUpdateChecker.openUnknownSourcesSettings(activity)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (activity.isFinishing) return@withContext
                    Toast.makeText(
                        activity,
                        activity.getString(
                            R.string.update_download_failed,
                            e.message ?: "error",
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}
