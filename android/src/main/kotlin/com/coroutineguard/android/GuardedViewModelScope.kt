package com.coroutineguard.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coroutineguard.core.guarded
import kotlinx.coroutines.CoroutineScope
import java.lang.ref.WeakReference

/**
 * Marks a [ViewModel] as having a CoroutineGuard-monitored scope, available via [guardedScope].
 *
 * Implement this interface on any ViewModel — or on a class that already extends a
 * BaseViewModel — using Kotlin's `by` delegation syntax. All coroutines launched from
 * [guardedScope] are automatically monitored for hangs, silent cancellations, and scope leaks.
 *
 * ─── Usage ───────────────────────────────────────────────────────────────────────────────
 *
 * Zero-configuration (label auto-derived from class name):
 *
 *   class OrdersViewModel : ViewModel(), GuardedViewModelScope
 *       by GuardedViewModelScopeDelegate(this) {
 *
 *       fun loadOrders() = guardedScope.launch { ... }
 *   }
 *
 * Works alongside any existing BaseViewModel — no forced hierarchy change:
 *
 *   class OrdersViewModel : BaseViewModel(), GuardedViewModelScope
 *       by GuardedViewModelScopeDelegate(this) {
 *       ...
 *   }
 *
 * ─── Why Delegate over BaseViewModel ─────────────────────────────────────────────────────
 *
 * A BaseViewModel approach would require every team to:
 *   1. Change their existing base class (breaking if sealed or from a third-party SDK)
 *   2. Accept a single-inheritance constraint (Kotlin classes can only extend one class)
 *   3. Migrate all existing ViewModels at once
 *
 * Kotlin interface delegation allows composing behaviour without touching the class
 * hierarchy. A ViewModel that already extends BaseViewModel can implement this interface
 * without any inheritance conflict. The delegation wiring is one line at the class header.
 */
interface GuardedViewModelScope {
    /**
     * A [CoroutineScope] backed by [ViewModel.viewModelScope] with [GuardInterceptor]
     * injected. All child coroutines are monitored automatically via
     * [kotlinx.coroutines.CopyableThreadContextElement.copyForChild].
     */
    val guardedScope: CoroutineScope
}

/**
 * Concrete delegate that implements [GuardedViewModelScope] for a given [ViewModel].
 *
 * Instantiate with `by GuardedViewModelScopeDelegate(this)` at the ViewModel class header.
 *
 * ─── Why WeakReference ────────────────────────────────────────────────────────────────────
 *
 * The ViewModel instance owns this delegate (Kotlin stores the delegate as a backing field).
 * If the delegate held a STRONG reference back to the ViewModel, we would have a circular
 * strong reference: ViewModel → delegate → ViewModel. While the JVM GC can collect circular
 * references that are unreachable from GC roots, the pattern is fragile and confusing.
 *
 * [WeakReference] makes the ownership direction unambiguous: the ViewModel owns the delegate,
 * not the other way around. If — in some edge case (e.g., reflection, test framework) — the
 * delegate outlives the ViewModel, [WeakReference.get] returns null and we fail fast with a
 * clear error message instead of silently retaining a zombie ViewModel.
 *
 * ─── Why lazy ─────────────────────────────────────────────────────────────────────────────
 *
 * [ViewModel.viewModelScope] is itself lazy: it creates its [kotlinx.coroutines.Job] on
 * first access. Accessing it inside the constructor body of [GuardedViewModelScopeDelegate]
 * would be safe but is unnecessarily eager. Using [lazy] here defers the allocation to the
 * first call to [guardedScope], consistent with the pattern set by [viewModelScope] itself.
 * It also means that if a ViewModel is constructed but never launches any coroutines, no
 * scope allocation overhead occurs.
 *
 * ─── Why simpleName and not a hardcoded string ────────────────────────────────────────────
 *
 * A hardcoded label like `"OrdersViewModel"` becomes stale when the class is renamed during
 * refactoring. IDE rename refactors do not update string literals. [KClass.simpleName]
 * always reflects the CURRENT class name at runtime — it is impossible for it to drift.
 * The fallback `"UnknownViewModel"` covers anonymous classes or obfuscated release builds
 * where [simpleName] may return null.
 *
 * @param viewModel The ViewModel instance that owns this delegate. Pass `this` from the
 *                  `by` clause: `by GuardedViewModelScopeDelegate(this)`.
 */
class GuardedViewModelScopeDelegate(viewModel: ViewModel) : GuardedViewModelScope {

    private val viewModelRef = WeakReference(viewModel)

    override val guardedScope: CoroutineScope by lazy {
        // viewModelRef.get() returns null only if the ViewModel was garbage-collected.
        // This cannot happen during normal operation because the ViewModel owns this
        // delegate — the delegate is unreachable if the ViewModel is unreachable.
        // The null branch exists as a defensive guard against misuse (e.g., storing the
        // delegate in a static field and accessing it after the ViewModel is cleared).
        val vm = checkNotNull(viewModelRef.get()) {
            "GuardedViewModelScopeDelegate: ViewModel was garbage collected before " +
                "guardedScope was accessed. Do not store the delegate beyond the ViewModel's lifetime."
        }
        // simpleName → refactor-safe label; null → anonymous/obfuscated class
        vm.viewModelScope.guarded(vm::class.simpleName ?: "UnknownViewModel")
    }
}
