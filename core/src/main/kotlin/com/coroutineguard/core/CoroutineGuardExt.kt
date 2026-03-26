package com.coroutineguard.core

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Returns a [CoroutineScope] with [GuardInterceptor] injected into its context.
 *
 * All coroutines launched from the returned scope — and ALL of their children,
 * transitively — are automatically monitored for:
 *   • hangs          (duration > [CoroutineGuardConfig.hangThresholdMs])
 *   • silent cancels (bare [kotlinx.coroutines.CancellationException] with no root cause)
 *   • scope leaks    (children still active after the scope's Job is cancelled)
 *
 * ─── How child propagation works ─────────────────────────────────────────────────────────
 *
 * [GuardInterceptor] implements [kotlinx.coroutines.CopyableThreadContextElement].
 * Every `launch {}` or `async {}` call on the returned scope triggers
 * [GuardInterceptor.copyForChild], which creates a FRESH interceptor instance for the child
 * with its own [startTime]. The developer does not need to call [guarded] on child launches.
 *
 * ─── Structured concurrency is preserved ─────────────────────────────────────────────────
 *
 * The returned scope reuses the receiver's [kotlinx.coroutines.Job] — it is not a new
 * isolated scope. Cancellation of the original scope cancels all coroutines launched
 * through the guarded wrapper, exactly as if [guarded] were never called.
 *
 * ─── Usage ───────────────────────────────────────────────────────────────────────────────
 *
 * One-time wrapping (recommended — cache the guarded scope):
 *   private val scope = viewModelScope.guarded("OrdersViewModel")
 *   fun loadOrders() = scope.launch { ... }
 *
 * Inline wrapping:
 *   viewModelScope.guarded().launch { ... }
 *
 * With [CoroutineName] for per-child labelling (label is inherited by all children):
 *   viewModelScope.guarded("Checkout").launch { ... }
 *
 * ─── No-op if not installed ──────────────────────────────────────────────────────────────
 *
 * If [CoroutineGuard.install] has not been called, [GuardInterceptor] is still added to
 * the context but its [invokeOnCompletion] callback reads [CoroutineGuard.config] == null
 * and returns immediately. No callbacks fire, no overhead beyond a null-check per coroutine.
 *
 * @param label Optional name attached as [CoroutineName] to the scope's context.
 *              Appears in all [CoroutineGuardConfig.onHang], [CoroutineGuardConfig.onSilentCancel],
 *              and [CoroutineGuardConfig.onScopeLeak] callbacks for coroutines in this scope.
 *              Falls back to null if not set.
 */
fun CoroutineScope.guarded(label: String? = null): CoroutineScope {
    // CoroutineName is a regular CoroutineContext.Element — it is inherited by all child
    // coroutines automatically. Setting it here means every launch/async from the guarded
    // scope reports the same label unless the child explicitly overrides it with its own
    // CoroutineName(). EmptyCoroutineContext is a no-op in the + operator.
    val nameContext = if (label != null) CoroutineName(label) else EmptyCoroutineContext

    // coroutineContext already contains the receiver's Job and Dispatcher.
    // Adding GuardInterceptor places it alongside them. If one already exists (double
    // guarded() call), the + operator replaces it by Key — no duplicate monitoring.
    return CoroutineScope(coroutineContext + GuardInterceptor() + nameContext)
}
