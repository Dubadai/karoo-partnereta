package com.dubadai.karoo.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.MANIFEST_URL_META
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import timber.log.Timber

/**
 * "Update available" for a side-loaded Karoo app. Part of karoo-common: [UpdateActivity] comes with
 * the library's manifest; the app adds the manifest meta-data and a manifest.json with each release
 * (karoo-starter/README.md "Update notices").
 *
 * The app names its `manifest.json` (karoo-ext's KarooAppManifest) in the AndroidManifest
 * (`io.hammerhead.karooext.MANIFEST_URL`). The Karoo itself reads it only when the rider opens
 * Settings › Apps › the app, where it shows the release notes and an UPDATE button. This class
 * tells the rider: when the Karoo starts and then once a day, and only while no ride is recording,
 * it fetches the manifest and, if there's a newer version, puts one card per version in
 * Control Center › Notifications. Tapping it opens that Karoo page ([UpdateActivity]).
 */
class UpdateCheck(
    private val context: Context,
    private val karoo: KarooSystemService,
    /** What the rider calls the app, e.g. "BetterIntervals". */
    private val appName: String,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Runs until [scope] is cancelled (the extension stops). */
    fun start(scope: CoroutineScope): Job = scope.launch {
        val manifestUrl = manifestUrl(context) ?: return@launch Timber.i("Update check: no manifest URL")
        // A build with another manifest URL (e.g. a test build) checks right away.
        if (prefs.getString(KEY_URL, null) != manifestUrl) {
            prefs.edit().putString(KEY_URL, manifestUrl).remove(KEY_NEXT_CHECK).remove(KEY_NOTIFIED_CODE).apply()
        }
        // Every start of the app (so every time the Karoo starts) checks once, whatever the last
        // check was; after that once a day.
        var checkNow = true
        rideStates()
            .map { it is RideState.Idle }
            .distinctUntilChanged()
            .collectLatest { idle ->
                // A ride starting cancels whatever is waiting or running here.
                if (!idle) return@collectLatest
                delay(SETTLE_MS) // after boot or a ride, let the Karoo get its connection first
                while (true) {
                    val wait = if (checkNow) 0 else prefs.getLong(KEY_NEXT_CHECK, 0) - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    checkNow = false
                    val ok = !isEnabled(context) || runCatching { checkOnce(manifestUrl) }
                        .onFailure { Timber.w(it, "Update check failed") }
                        .isSuccess
                    val next = System.currentTimeMillis() + if (ok) DAY_MS else RETRY_MS
                    prefs.edit().putLong(KEY_NEXT_CHECK, next).apply()
                }
            }
    }

    private suspend fun checkOnce(manifestUrl: String) {
        // No manifest (no normal release yet, or a private repo): nothing to say; try tomorrow.
        val body = httpGet(manifestUrl) ?: return Timber.i("Update check: no manifest at $manifestUrl")
        val m = JSONObject(body)
        val latestCode = m.getLong("latestVersionCode")
        val latestName = m.getString("latestVersion")
        require(m.getString("packageName") == context.packageName) { "manifest is for another app" }
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedCode = PackageInfoCompat.getLongVersionCode(installed)
        Timber.i("Update check: installed $installedCode, latest $latestCode")
        if (latestCode <= installedCode) return
        if (prefs.getLong(KEY_NOTIFIED_CODE, 0) == latestCode) return // one card per version
        karoo.dispatch(
            SystemNotification(
                id = "${context.packageName}.update",
                header = context.getString(R.string.update_header),
                message = "$appName $latestName",
                subText = context.getString(R.string.update_text, installed.versionName),
                style = SystemNotification.Style.UPDATE,
                action = context.getString(R.string.update_action),
                actionIntent = "${context.packageName}$ACTION_SUFFIX",
            ),
        )
        prefs.edit().putLong(KEY_NOTIFIED_CODE, latestCode).apply()
    }

    private fun rideStates() = callbackFlow {
        val id = karoo.addConsumer<RideState> { trySend(it) }
        awaitClose { karoo.removeConsumer(id) }
    }

    /** GET through the Karoo (Wi-Fi, or the phone over Bluetooth). Fails fast when offline; null on 404. */
    private suspend fun httpGet(url: String): String? {
        val done = withTimeout(HTTP_TIMEOUT_MS) {
            callbackFlow {
                val id = karoo.addConsumer(
                    OnHttpResponse.MakeHttpRequest("GET", url, mapOf("User-Agent" to appName), waitForConnection = false),
                    onError = { msg -> close(IllegalStateException(msg)) },
                ) { event: OnHttpResponse -> trySend(event.state) }
                awaitClose { karoo.removeConsumer(id) }
            }.filterIsInstance<HttpResponseState.Complete>().first()
        }
        if (done.statusCode == 404) return null
        check(done.statusCode in 200..299) { "HTTP ${done.statusCode} ${done.error.orEmpty()}" }
        return done.body?.decodeToString().orEmpty()
    }

    companion object {
        private const val PREFS = "update_check"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NEXT_CHECK = "next_check"
        private const val KEY_NOTIFIED_CODE = "notified_code"
        private const val KEY_URL = "manifest_url"
        private const val ACTION_SUFFIX = ".UPDATE"
        private const val SETTLE_MS = 60_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val RETRY_MS = 60 * 60 * 1000L
        private const val HTTP_TIMEOUT_MS = 45_000L

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, on: Boolean) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

        fun manifestUrl(context: Context): String? = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString(MANIFEST_URL_META)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

/**
 * Opened by the "Update available" card: hands over to the Karoo's own page for this app
 * (Settings › Apps), which shows the release notes and the UPDATE button.
 */
class UpdateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val karooPage = Intent("io.hammerhead.action.APP_INFO").putExtra("packageName", packageName)
        runCatching { startActivity(karooPage) }.onFailure {
            Timber.w(it, "No Karoo app page; opening the app instead")
            packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
        }
        finish()
    }
}
