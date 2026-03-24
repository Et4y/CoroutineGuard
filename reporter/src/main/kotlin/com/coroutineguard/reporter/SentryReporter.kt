package com.coroutineguard.reporter

import com.coroutineguard.core.CoroutineHangException
import com.coroutineguard.core.ScopeLeakException
import com.coroutineguard.core.SilentCancellationException
import io.sentry.Sentry

/**
 * Forwards CoroutineGuard detection events to Sentry as captured exceptions.
 *
 * Each event is mapped to a dedicated [RuntimeException] subclass so Sentry groups
 * them into separate issues in the dashboard. The exception message contains the label
 * and relevant metadata (e.g., duration for hangs), which Sentry surfaces as the issue title.
 *
 * ── Prerequisites ───────────────────────────────────────────────────────────────────────
 *
 * This class compiles against the Sentry SDK but does NOT bundle it (compileOnly).
 * The consuming app MUST declare its own Sentry dependency, e.g.:
 *
 *   // app/build.gradle.kts
 *   implementation("io.sentry:sentry-android:7.x.x")
 *
 * If the app does not have Sentry on the classpath, instantiating [SentryReporter]
 * will throw [NoClassDefFoundError] at runtime.
 *
 * Unlike Firebase, [Sentry.captureException] is safe to call before Sentry is initialized
 * (it silently no-ops), so no strict initialization ordering is required.
 */
class SentryReporter : CoroutineGuardReporter {

    override fun onHang(label: String?, durationMs: Long) {
        // captureException() sends the exception to Sentry as an error-level event.
        // The exception class name and message form the issue title in the Sentry dashboard.
        Sentry.captureException(
            CoroutineHangException(label, durationMs)
        )
    }

    override fun onSilentCancel(label: String?, cause: Throwable?) {
        // SilentCancellationException carries the original cause as its Java cause.
        // Sentry renders this as a chained exception, showing both the wrapper and the root.
        Sentry.captureException(
            SilentCancellationException(label, cause)
        )
    }

    override fun onScopeLeak(label: String?) {
        Sentry.captureException(
            ScopeLeakException(label)
        )
    }
}
