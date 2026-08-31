package app.sonicsound.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import app.sonicsound.App
import app.sonicsound.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks GitHub Releases for a newer SonicSound APK and installs via FileProvider.
 *
 * Prefers `*-debug.apk` so sideloaded Shield debug builds can update in place.
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    private const val REPO = "asafelobotomy/SonicSound"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val PREFS = "app_updates"
    private const val KEY_LAST_TAG = "last_notified_tag"
    private const val KEY_LAST_AT = "last_notified_at_ms"
    private const val KEY_PENDING_APK = "pending_apk_path"
    private const val DEBOUNCE_MS = 24L * 60L * 60L * 1000L

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sessionChecked = AtomicBoolean(false)

    data class AvailableUpdate(
        val version: String,
        val tag: String,
        val apkUrl: String,
        val apkName: String,
    )

    sealed class CheckResult {
        data class UpdateAvailable(val update: AvailableUpdate) : CheckResult()
        object UpToDate : CheckResult()
        object Skipped : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    fun releasesPageUrl(): String = RELEASES_PAGE

    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * @param force when true, ignore session + 24h debounce (Settings manual check).
     */
    fun check(force: Boolean = false): CheckResult {
        if (!force) {
            if (!sessionChecked.compareAndSet(false, true)) {
                return CheckResult.Skipped
            }
        } else {
            sessionChecked.set(true)
        }

        return try {
            val body = httpGet(LATEST_URL)
                ?: return CheckResult.Failed("No response from GitHub")
            val root = JSONObject(body)
            val tag = root.optString("tag_name", "").trim()
            if (tag.isEmpty()) {
                return CheckResult.Failed("Release has no tag")
            }
            val version = normalizeVersion(tag)
            val current = normalizeVersion(BuildConfig.VERSION_NAME)
            if (compareVersions(version, current) <= 0) {
                Log.i(TAG, "Up to date (local=$current latest=$version)")
                return CheckResult.UpToDate
            }

            if (!force && wasRecentlyNotified(tag)) {
                return CheckResult.Skipped
            }

            val assets = root.optJSONArray("assets")
                ?: return CheckResult.Failed("Release has no assets")
            val apk = pickApkAsset(assets)
                ?: return CheckResult.Failed("No APK asset on latest release")

            CheckResult.UpdateAvailable(
                AvailableUpdate(
                    version = version,
                    tag = tag,
                    apkUrl = apk.first,
                    apkName = apk.second,
                ),
            ).also {
                Log.i(TAG, "Update available: $tag asset=${apk.second}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            CheckResult.Failed(e.message ?: "Update check failed")
        }
    }

    fun markNotified(update: AvailableUpdate) {
        prefs().edit()
            .putString(KEY_LAST_TAG, update.tag)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun downloadApk(context: Context, update: AvailableUpdate): File {
        val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val out = File(dir, "SonicSound-update.apk")
        if (out.exists()) out.delete()

        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "SonicSound/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/octet-stream")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty APK body")
            out.outputStream().use { sink ->
                body.byteStream().use { src -> src.copyTo(sink) }
            }
        }
        if (!out.exists() || out.length() < 1024L) {
            throw IllegalStateException("Downloaded APK is empty or too small")
        }
        Log.i(TAG, "Downloaded ${update.apkName} → ${out.absolutePath} (${out.length()} bytes)")
        return out
    }

    /**
     * Launch the system package installer. Returns false if unknown-sources
     * permission is required (caller should open settings and retry later).
     */
    fun installApk(activity: Activity, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                setPendingApk(apk.absolutePath)
                return false
            }
        }
        clearPendingApk()
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Grant to resolvers that handle the install Intent.
        val resInfo = activity.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        for (resolve in resInfo) {
            activity.grantUriPermission(
                resolve.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        activity.startActivity(intent)
        return true
    }

    fun openUnknownSourcesSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { activity.startActivity(intent) }
                .onFailure {
                    // Fallback: general install-unknown settings.
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
        }
    }

    /** If user granted install permission after being sent to Settings, retry. */
    fun resumePendingInstall(activity: Activity): Boolean {
        val path = prefs().getString(KEY_PENDING_APK, null) ?: return false
        val file = File(path)
        if (!file.exists()) {
            clearPendingApk()
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return false
        }
        return installApk(activity, file)
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val av = pa.getOrElse(i) { 0 }
            val bv = pb.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun wasRecentlyNotified(tag: String): Boolean {
        val p = prefs()
        val lastTag = p.getString(KEY_LAST_TAG, null) ?: return false
        if (lastTag != tag) return false
        val at = p.getLong(KEY_LAST_AT, 0L)
        return System.currentTimeMillis() - at < DEBOUNCE_MS
    }

    private fun setPendingApk(path: String) {
        prefs().edit().putString(KEY_PENDING_APK, path).apply()
    }

    private fun clearPendingApk() {
        prefs().edit().remove(KEY_PENDING_APK).apply()
    }

    private fun prefs() =
        App.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun httpGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SonicSound/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub HTTP ${response.code}")
            }
            return response.body?.string()
        }
    }

    private fun pickApkAsset(assets: org.json.JSONArray): Pair<String, String>? {
        data class Asset(val name: String, val url: String)
        val list = ArrayList<Asset>(assets.length())
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                list.add(Asset(name, url))
            }
        }
        if (list.isEmpty()) return null
        val debug = list.firstOrNull { it.name.contains("debug", ignoreCase = true) }
        if (debug != null) return debug.url to debug.name
        val release = list.firstOrNull { it.name.contains("release", ignoreCase = true) }
        if (release != null) return release.url to release.name
        val first = list.first()
        return first.url to first.name
    }

    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    private fun parseVersion(raw: String): List<Int> =
        normalizeVersion(raw)
            .split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }
}
