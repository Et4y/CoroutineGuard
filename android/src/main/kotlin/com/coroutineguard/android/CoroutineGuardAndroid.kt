package com.coroutineguard.android

import android.app.Application
import android.content.pm.ApplicationInfo
import com.coroutineguard.core.CoroutineGuard
import com.coroutineguard.core.CoroutineGuardConfig

/**
 * Android entry point for CoroutineGuard.
 *
 * Wraps [CoroutineGuard.install] with Android-specific setup:
 *   1. Guards against running in production when [CoroutineGuardConfig.enableInProduction] is false
 *   2. Wires the [CoroutineGuardLifecycleObserver] into the Application's Activity lifecycle
 *
 * Typical usage — zero configuration needed for debug builds:
 *
 *   class MyApp : Application() {
 *       override fun onCreate() {
 *           super.onCreate()
 *           CoroutineGuardAndroid.install(this)
 *       }
 *   }
 *
 * Custom configuration:
 *
 *   CoroutineGuardAndroid.install(
 *       app = this,
 *       config = CoroutineGuardConfig.Builder()
 *           .hangThresholdMs(3_000)
 *           .onHang { label, ms -> myAnalytics.track("coroutine_hang", label, ms) }
 *           .build()
 *   )
 */
object CoroutineGuardAndroid {

    /**
     * Activates CoroutineGuard for an Android application.
     *
     * @param app    The [Application] instance. Used to register lifecycle callbacks and
     *               to read the debug flag from [ApplicationInfo].
     * @param config Defaults to [DefaultDebugReporter.buildConfig] — Logcat output for
     *               all three signal types, all detections enabled, 5 s hang threshold.
     *               Pass a custom config to override callbacks or tune thresholds.
     */
    fun install(
        app: Application,
        config: CoroutineGuardConfig = DefaultDebugReporter.buildConfig(),
    ) {
        // ── Production guard ──────────────────────────────────────────────────────────────
        // We read the debug flag from ApplicationInfo rather than BuildConfig.DEBUG because
        // BuildConfig.DEBUG in a library module reflects the LIBRARY'S build type, not the
        // consuming app's. An app release build would still see DEBUG=true if it depends on
        // a debug variant of this library — a false positive that could expose monitoring
        // overhead in production.
        //
        // ApplicationInfo.FLAG_DEBUGGABLE is set by the Gradle Android plugin based on the
        // *app's* build type, making it the reliable source of truth at runtime.
        val isDebugBuild = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        if (!isDebugBuild && !config.enableInProduction) {
            // Silent skip: the developer explicitly opted out of production monitoring,
            // and this is a production build. No log, no crash — just a no-op.
            return
        }

        // ── Core installation ─────────────────────────────────────────────────────────────
        // Writes the config into the @Volatile slot that GuardInterceptor reads.
        // GuardInterceptor itself is already installed via ServiceLoader — no registration
        // needed here. See CoroutineGuard KDoc for the full explanation.
        CoroutineGuard.install(config)

        // ── Lifecycle observer ────────────────────────────────────────────────────────────
        // Registered unconditionally when we reach this point: even if enableScopeLeakDetection
        // is false right now, the config could be swapped in tests via uninstall()+install().
        // The observer checks config.enableScopeLeakDetection at runtime per-callback, so
        // registering it eagerly is safe and avoids the complexity of conditional registration.
        if (config.enableScopeLeakDetection) {
            app.registerActivityLifecycleCallbacks(
                CoroutineGuardLifecycleObserver(config)
            )
        }
    }
}
