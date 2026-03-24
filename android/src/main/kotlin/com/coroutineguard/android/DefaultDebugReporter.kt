package com.coroutineguard.android

import android.util.Log
import com.coroutineguard.core.CoroutineGuardConfig

private const val TAG = "CoroutineGuard"

/**
 * Builds a [CoroutineGuardConfig] with Logcat callbacks pre-wired.
 *
 * Used as the default config in [CoroutineGuardAndroid.install] so that calling
 * install(app) with no arguments immediately produces visible output in debug builds —
 * no setup required from the developer.
 *
 * Intentionally NOT a singleton config instance: each call to [buildConfig] produces a
 * fresh [CoroutineGuardConfig.Builder], letting the caller layer additional settings on
 * top without mutating a shared object.
 */
internal object DefaultDebugReporter {

    fun buildConfig(): CoroutineGuardConfig = CoroutineGuardConfig.Builder()
        .onHang { label, durationMs ->
            // w() = warning priority: hang is degraded performance, not a crash.
            Log.w(TAG, "Hang detected — '${label ?: "unnamed"}' ran for ${durationMs}ms")
        }
        .onSilentCancel { label, cause ->
            // Silent cancel: coroutine died with no error context. Worth a warning because
            // operations may have been abandoned without the caller knowing.
            Log.w(TAG, "Silent cancel — '${label ?: "unnamed"}' was cancelled with no cause", cause)
        }
        .onScopeLeak { label ->
            // e() = error priority: a scope leak is a correctness bug, not just slowness.
            // Active coroutines survived their host scope — they'll keep running and
            // consuming resources until they naturally complete or the process dies.
            Log.e(TAG, "Scope leak — '${label ?: "unnamed"}' has coroutines still active after scope destruction")
        }
        .build()
}
