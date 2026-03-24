package com.coroutineguard.reporter

/**
 * Delegates each detection event to all provided [reporters] in order.
 *
 * Use this when you need to forward events to multiple destinations simultaneously —
 * for example, Crashlytics for crash grouping AND a custom analytics backend for dashboards.
 * Each reporter is called unconditionally; an exception thrown by one reporter does NOT
 * prevent subsequent reporters from receiving the event.
 *
 * Usage:
 *
 *   CoroutineGuardConfig.Builder()
 *       .reporter(
 *           CompositeReporter(
 *               FirebaseReporter(),
 *               SentryReporter(),
 *               MyCustomAnalyticsReporter(),
 *           )
 *       )
 *       .build()
 */
class CompositeReporter(vararg reporters: CoroutineGuardReporter) : CoroutineGuardReporter {

    // Snapshot into a List at construction time so the vararg array can't be mutated
    // externally after this object is created.
    private val delegates: List<CoroutineGuardReporter> = reporters.toList()

    override fun onHang(label: String?, durationMs: Long) {
        delegates.forEach { it.onHang(label, durationMs) }
    }

    override fun onSilentCancel(label: String?, cause: Throwable?) {
        delegates.forEach { it.onSilentCancel(label, cause) }
    }

    override fun onScopeLeak(label: String?) {
        delegates.forEach { it.onScopeLeak(label) }
    }
}
