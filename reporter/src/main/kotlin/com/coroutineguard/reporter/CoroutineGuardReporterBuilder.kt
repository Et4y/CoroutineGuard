package com.coroutineguard.reporter

import com.coroutineguard.core.CoroutineGuardConfig

/**
 * Bridges a [CoroutineGuardReporter] into a [CoroutineGuardConfig.Builder] by wiring
 * the reporter's three methods to the builder's three callback slots.
 *
 * Declared as an extension function so it slots naturally into the builder chain without
 * requiring the reporter concept to exist in :core. :core stays dependency-free; :reporter
 * adds the abstraction layer on top.
 *
 * Usage:
 *
 *   CoroutineGuardConfig.Builder()
 *       .hangThresholdMs(3_000)
 *       .reporter(FirebaseReporter())
 *       .build()
 *
 * A single call to [reporter] replaces the need to call [CoroutineGuardConfig.Builder.onHang],
 * [CoroutineGuardConfig.Builder.onSilentCancel], and [CoroutineGuardConfig.Builder.onScopeLeak]
 * separately. If you need callbacks AND a reporter, call the individual setters first and
 * [reporter] last — it will overwrite any previously set callbacks.
 */
fun CoroutineGuardConfig.Builder.reporter(
    reporter: CoroutineGuardReporter,
): CoroutineGuardConfig.Builder = this
    .onHang { label, durationMs ->
        reporter.onHang(label, durationMs)
    }
    .onSilentCancel { label, cause ->
        reporter.onSilentCancel(label, cause)
    }
    .onScopeLeak { label ->
        reporter.onScopeLeak(label)
    }
