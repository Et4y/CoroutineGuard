package com.coroutineguard.core

import java.util.concurrent.atomic.AtomicReference

/**
 * Public entry point for the CoroutineGuard library.
 *
 * ─── Why install() doesn't register the interceptor ──────────────────────────────────────
 *
 * GuardInterceptor is declared in:
 *   META-INF/services/kotlinx.coroutines.CoroutineContextElement
 *
 * The JVM ServiceLoader reads that file at kotlinx-coroutines startup (before any coroutine
 * runs) and permanently installs GuardInterceptor into the global default CoroutineContext.
 * It is already in place by the time your application code runs — no registration call needed.
 *
 * install() has exactly ONE job: write the CoroutineGuardConfig into the @Volatile slot
 * that GuardInterceptor reads on every coroutine completion. Until install() is called,
 * GuardInterceptor checks CoroutineGuard.config, finds null, and returns immediately — a
 * true zero-cost no-op. After install(), the same interceptor, already wired into every
 * coroutine, starts acting on the provided config.
 *
 * ─────────────────────────────────────────────────────────────────────────────────────────
 *
 * Typical usage:
 *
 *   CoroutineGuard.install(
 *       CoroutineGuardConfig.Builder()
 *           .hangThresholdMs(3_000)
 *           .onHang { label, ms -> println("$label hung for ${ms}ms") }
 *           .build()
 *   )
 */
object CoroutineGuard {

    /**
     * AtomicReference gives us both visibility (like @Volatile) AND the ability to do
     * a single atomic compareAndSet for the double-install guard.
     *
     * A plain @Volatile var would require a separate read + write, which is not atomic:
     *   if (config != null) warn()   // thread A reads null here
     *   config = newConfig           // thread B also reads null and sets its config
     *                                // thread A sets its config — silently overwrites B
     * AtomicReference.compareAndSet(null, newConfig) collapses check + set into one
     * hardware instruction, eliminating the race entirely.
     */
    private val configRef = AtomicReference<CoroutineGuardConfig?>(null)

    /**
     * Internal read path used by GuardInterceptor on every coroutine completion.
     * Kept as a direct property access (no function call overhead) since it is
     * on the hot path of every coroutine lifecycle event.
     */
    internal val config: CoroutineGuardConfig?
        get() = configRef.get()

    /**
     * Activates CoroutineGuard with the given [config].
     *
     * Safe to call from any thread. If called more than once, the second call is ignored
     * and a warning is printed to stderr — the library does not crash because a double-install
     * is more likely a startup-ordering bug than a fatal error, and crashing at launch would
     * be worse than the misconfiguration itself.
     *
     * @param config Defaults to a config with all detections enabled and 5 s hang threshold.
     */
    fun install(config: CoroutineGuardConfig = CoroutineGuardConfig.Builder().build()) {
        val installed = configRef.compareAndSet(null, config)
        if (!installed) {
            // compareAndSet returns false when the current value is NOT null, meaning
            // install() was already called. We warn rather than throw: a double-install
            // is almost always an accidental duplicate initialization (e.g., called in
            // both Application.onCreate and a test setUp), not a logic error worth crashing.
            System.err.println(
                "[CoroutineGuard] install() called more than once. " +
                    "The second call has been ignored. Call uninstall() first if reconfiguration is intended."
            )
        }
    }

    /**
     * Deactivates CoroutineGuard. GuardInterceptor immediately becomes a no-op for all
     * subsequently completing coroutines.
     *
     * Primary use case: resetting state between unit tests so each test starts clean.
     */
    fun uninstall() {
        configRef.set(null)
    }

    /**
     * Returns true if [install] has been called and [uninstall] has not been called since.
     */
    val isInstalled: Boolean
        get() = configRef.get() != null
}
