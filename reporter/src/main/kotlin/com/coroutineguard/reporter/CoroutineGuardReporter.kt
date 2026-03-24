package com.coroutineguard.reporter

/**
 * Contract for receiving CoroutineGuard detection events.
 *
 * Implement this interface to forward hang, silent-cancel, and scope-leak signals to any
 * destination — a crash tool, an analytics backend, a local log, or a combination via
 * [CompositeReporter].
 *
 * Ready-made implementations:
 *   • [FirebaseReporter]   — forwards to Firebase Crashlytics
 *   • [SentryReporter]     — forwards to Sentry
 *   • [CompositeReporter]  — delegates to multiple reporters at once
 *
 * Wire a reporter into the library via the [reporter] extension on
 * [com.coroutineguard.core.CoroutineGuardConfig.Builder]:
 *
 *   CoroutineGuardConfig.Builder()
 *       .reporter(FirebaseReporter())
 *       .build()
 */
interface CoroutineGuardReporter {

    /**
     * Called when a coroutine ran longer than the configured hang threshold.
     *
     * @param label     The coroutine's [kotlinx.coroutines.CoroutineName], or null if unnamed.
     * @param durationMs How long the coroutine ran before completing or being cancelled, in ms.
     */
    fun onHang(label: String?, durationMs: Long)

    /**
     * Called when a coroutine was cancelled via a [kotlinx.coroutines.CancellationException]
     * that carried no wrapped root cause — i.e., the cancellation was silent.
     *
     * @param label The coroutine's [kotlinx.coroutines.CoroutineName], or null if unnamed.
     * @param cause The [kotlinx.coroutines.CancellationException] itself, or null.
     */
    fun onSilentCancel(label: String?, cause: Throwable?)

    /**
     * Called when coroutines are still active after their host scope was destroyed.
     *
     * @param label The [kotlinx.coroutines.CoroutineName] of the scope owner (e.g., Activity
     *              simple class name), or null if unresolvable.
     */
    fun onScopeLeak(label: String?)
}
