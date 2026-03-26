package com.coroutineguard.core

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException

/**
 * GuardInterceptor is the engine of CoroutineGuard. It tracks the lifecycle of every
 * coroutine launched from a scope wrapped with [CoroutineScope.guarded].
 *
 * ─── How injection works ──────────────────────────────────────────────────────────────────
 *
 * Injection is EXPLICIT, not automatic. The developer wraps a scope once:
 *
 *   val scope = viewModelScope.guarded("OrdersViewModel")
 *
 * [CoroutineScope.guarded] places a GuardInterceptor into the scope's CoroutineContext.
 * From that point, every child coroutine launched from the scope automatically receives
 * its own fresh GuardInterceptor via [copyForChild] — no further developer action needed.
 *
 * ─── Why explicit injection and not ServiceLoader ─────────────────────────────────────────
 *
 * A META-INF/services/kotlinx.coroutines.CoroutineContextElement file was attempted.
 * It silently failed on Android for three reasons:
 *   1. `kotlinx.coroutines.CoroutineContextElement` is not a stable public API — it is
 *      an internal JetBrains class whose service-loading behaviour is undocumented and
 *      can change between coroutines releases.
 *   2. AGP's AAR resource merger does not guarantee that META-INF/services/ entries from
 *      transitive library dependencies are merged into the final APK in all configurations.
 *   3. R8 can silently strip service entries it cannot prove are reachable.
 *
 * Explicit context composition via [guarded] has none of these failure modes: the
 * interceptor is placed directly into the context at the call site, where it is
 * immediately visible to the Kotlin runtime and immune to build-tool interference.
 *
 * ─── Why CopyableThreadContextElement and not just CoroutineContext.Element ─────────────
 *
 * A plain CoroutineContext.Element is SHARED between parent and child. A single shared
 * instance would mean all child coroutines share the parent's [startTime] and
 * [listenerAttached] flag — hang detection would be meaningless and the observer would
 * only ever attach once. CopyableThreadContextElement guarantees each coroutine launched
 * from a guarded scope receives a FRESH instance with its own [startTime].
 *
 * ─── Why also extend AbstractCoroutineContextElement ─────────────────────────────────────
 *
 * AbstractCoroutineContextElement provides the correct [key] override and implements
 * fold/minusKey/plus. Without it we would have to implement those manually.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GuardInterceptor : AbstractCoroutineContextElement(Key), CopyableThreadContextElement<GuardInterceptor> {

    companion object Key : CoroutineContext.Key<GuardInterceptor>

    /**
     * Records the moment this interceptor was assigned to a coroutine.
     * Captured in the constructor so that the clock starts as soon as [copyForChild]
     * creates this instance — before the coroutine body even begins running.
     */
    private val startTime: Long = System.currentTimeMillis()

    /**
     * Guards against attaching the Job observer multiple times.
     *
     * [updateThreadContext] is called on EVERY resume (once per suspension point).
     * A typical coroutine that does network + disk I/O may resume dozens of times.
     * The AtomicBoolean compareAndSet ensures exactly-once semantics without a lock.
     */
    private val listenerAttached = AtomicBoolean(false)

    // ─── CopyableThreadContextElement contract ────────────────────────────────────────────

    /**
     * Called immediately before the coroutine body runs on a thread — including after
     * each suspension point. Returns the "old state" that will be handed back to
     * [restoreThreadContext] when the coroutine suspends again.
     *
     * We exploit the FIRST call to attach a Job completion listener.
     * This is the earliest safe moment: context[Job] is guaranteed non-null here
     * because the coroutine is actively executing.
     *
     * Subsequent calls are no-ops (listenerAttached is already true).
     */
    override fun updateThreadContext(context: CoroutineContext): GuardInterceptor {
        if (listenerAttached.compareAndSet(false, true)) {
            // CoroutineName is set by the developer via:
            //   launch(CoroutineName("myTask")) { ... }
            // Falls back to null for unnamed coroutines.
            val label = context[CoroutineName]?.name

            context[Job]?.let { job -> attachLifecycleObserver(job, label) }
        }
        // Return "this" as the old state. restoreThreadContext will receive it back,
        // but we have nothing to restore — no thread-local is being modified.
        return this
    }

    /**
     * Called when the coroutine suspends (yields the thread back to the pool).
     * [oldState] is whatever [updateThreadContext] returned.
     *
     * We don't modify any thread-local, so there is nothing to restore.
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: GuardInterceptor) = Unit

    /**
     * Called when a CHILD coroutine is launched and inherits this element from the parent.
     *
     * MUST return a NEW instance — sharing the parent's instance would mean the child
     * inherits the parent's startTime and listenerAttached state, making all tracking wrong.
     *
     * This single method is what makes per-coroutine tracking work automatically:
     *   every launch/async → copyForChild() → fresh GuardInterceptor → fresh startTime.
     */
    override fun copyForChild(): GuardInterceptor = GuardInterceptor()

    /**
     * Called when the child's context ALREADY has a GuardInterceptor — for example,
     * the developer manually passed one via launch(guardInterceptor) { }.
     *
     * We honour the explicit element over the inherited one. Returning [overwrittenElement]
     * tells the coroutine runtime to keep the child's version unchanged.
     */
    override fun mergeForChild(overwrittenElement: CoroutineContext.Element): CoroutineContext.Element =
        overwrittenElement

    // ─── Lifecycle observation ────────────────────────────────────────────────────────────

    private fun attachLifecycleObserver(job: Job, label: String?) {
        /**
         * invokeOnCompletion fires ONCE when the Job reaches its terminal state:
         *   • Completed  → cause == null
         *   • Cancelled  → cause is CancellationException
         *   • Failed     → cause is some other Throwable
         *
         * Under structured concurrency, a parent Job only reaches Completed after ALL
         * its children have completed, so this callback is safe from child-still-running
         * races for the hang and silent-cancel checks.
         */
        job.invokeOnCompletion { cause ->
            val config = CoroutineGuard.config ?: return@invokeOnCompletion
            val durationMs = System.currentTimeMillis() - startTime

            when {
                cause == null -> {
                    // ── Hang detection (normal completion) ────────────────────────────
                    // The coroutine finished successfully, but it blocked or ran longer
                    // than the configured threshold. Common culprits: blocking I/O called
                    // on a coroutine dispatcher, CPU-heavy work on Default, etc.
                    if (config.enableHangDetection && durationMs > config.hangThresholdMs) {
                        config.onHang?.invoke(label, durationMs)
                    }
                }

                cause is CancellationException -> {
                    // ── Hang detection (cancelled, but still took too long) ───────────
                    // A coroutine that was cancelled after a long delay is still a hang:
                    // it consumed resources for longer than expected before being killed.
                    if (config.enableHangDetection && durationMs > config.hangThresholdMs) {
                        config.onHang?.invoke(label, durationMs)
                    }

                    // ── Silent cancel detection ───────────────────────────────────────
                    // CancellationException.cause == null means the cancellation carries
                    // no wrapped root cause. This happens when:
                    //   • The parent scope was cancelled (e.g., viewModelScope cleared)
                    //   • job.cancel() was called directly
                    //   • withTimeout expired
                    // These are often silent bugs: the coroutine died with no visible error,
                    // leaving operations unfinished and callers unaware.
                    //
                    // Note: if cause.cause != null, the CancellationException is wrapping a
                    // real error — that is NOT silent, so we skip it.
                    if (config.enableSilentCancelDetection && cause.cause == null) {
                        config.onSilentCancel?.invoke(label, cause)
                    }
                }

                // ── Unhandled exception ───────────────────────────────────────────────
                // The coroutine crashed with a non-cancellation exception. This propagates
                // through the Job hierarchy and is handled by CoroutineExceptionHandler.
                // GuardInterceptor intentionally does not interfere with this path.
                else -> Unit
            }

            // ── Scope leak detection ──────────────────────────────────────────────────
            // Under normal structured concurrency a parent job waits for ALL children
            // before it reaches terminal state — so job.children should be empty here.
            //
            // If any children are still active at this point, they were launched into
            // an EXTERNAL scope (e.g. GlobalScope.launch { }) from within this coroutine,
            // escaping the structured lifetime. That is a scope leak: these orphaned
            // coroutines will outlive their logical owner.
            if (config.enableScopeLeakDetection && job.children.any { it.isActive }) {
                config.onScopeLeak?.invoke(label)
            }
        }
    }
}
