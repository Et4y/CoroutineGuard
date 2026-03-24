// ── No-op implementation ──────────────────────────────────────────────────────────────────
// This is a no-op stub. It exists solely to provide the same API surface as the real
// CoroutineGuardConfig in :core with zero runtime cost. Every Builder method accepts and
// silently discards its argument — nothing is stored, no validation runs, no callbacks
// are ever invoked.
//
// Use releaseImplementation("com.coroutineguard:no-op") to swap :android (and transitively
// :core) for this self-contained stub in release builds.
//
// WHY this file is in com.coroutineguard.core (not com.coroutineguard.noop):
// Developer app code references CoroutineGuardConfig by its fully-qualified package name.
// For the release build to compile against the no-op stub without any source changes, the
// stub must live in exactly the same package as the real class it replaces.
// ─────────────────────────────────────────────────────────────────────────────────────────
package com.coroutineguard.core

/**
 * No-op stub for [com.coroutineguard.core.CoroutineGuardConfig].
 *
 * API surface is identical to the real implementation so app code compiles unchanged
 * against either the :android (debug) or :no-op (release) artifact. All Builder methods
 * accept the same argument types and return `this` for chaining; `build()` returns an
 * empty config instance. No data is retained, no callbacks are stored.
 */
class CoroutineGuardConfig private constructor() {

    class Builder {

        // Each method returns `this` for chaining — identical to the real Builder contract.
        // The argument is accepted (so call sites compile) but immediately discarded.

        fun hangThresholdMs(@Suppress("UNUSED_PARAMETER") value: Long) = this
        fun enableInProduction(@Suppress("UNUSED_PARAMETER") value: Boolean) = this
        fun enableHangDetection(@Suppress("UNUSED_PARAMETER") value: Boolean) = this
        fun enableSilentCancelDetection(@Suppress("UNUSED_PARAMETER") value: Boolean) = this
        fun enableScopeLeakDetection(@Suppress("UNUSED_PARAMETER") value: Boolean) = this

        fun onHang(
            @Suppress("UNUSED_PARAMETER") callback: (label: String?, durationMs: Long) -> Unit,
        ) = this

        fun onSilentCancel(
            @Suppress("UNUSED_PARAMETER") callback: (label: String?, cause: Throwable?) -> Unit,
        ) = this

        fun onScopeLeak(
            @Suppress("UNUSED_PARAMETER") callback: (label: String?) -> Unit,
        ) = this

        // No validation — the no-op config is always "valid" because it does nothing.
        fun build(): CoroutineGuardConfig = CoroutineGuardConfig()
    }
}
