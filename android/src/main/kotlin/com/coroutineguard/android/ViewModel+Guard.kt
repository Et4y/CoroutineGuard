package com.coroutineguard.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coroutineguard.core.guarded
import kotlinx.coroutines.CoroutineScope

/**
 * A [CoroutineScope] backed by [ViewModel.viewModelScope] with [GuardInterceptor] injected.
 * All coroutines launched from this scope are automatically monitored for hangs,
 * silent cancellations, and scope leaks.
 *
 * ─── Usage ───────────────────────────────────────────────────────────────────────────────
 *
 * Zero boilerplate — use directly inside any ViewModel:
 *
 *   class OrdersViewModel : ViewModel() {
 *       fun loadOrders() = guardedScope.launch { ... }
 *   }
 *
 * Works alongside any BaseViewModel without conflict:
 *
 *   class OrdersViewModel : BaseViewModelV2<OrdersState>() {
 *       fun loadOrders() = guardedScope.launch { ... }
 *   }
 *
 * ─── Why extension property over delegate ────────────────────────────────────────────────
 *
 * The Kotlin delegate syntax (`by GuardedViewModelScopeDelegate(this)`) requires `this` in
 * the class header delegation clause. However, `this` is not available at that point in
 * compilation — the instance does not exist yet. The compiler rejects it with:
 * "This is not available in class header delegation expressions."
 *
 * An extension property on [ViewModel] sidesteps this entirely: there is no class header,
 * no interface to implement, no backing field, and no extra import. Any ViewModel gets
 * [guardedScope] for free by virtue of being a [ViewModel].
 *
 * ─── Why get() and not lazy ──────────────────────────────────────────────────────────────
 *
 * [guarded] creates a new [CoroutineScope] by wrapping [viewModelScope] with a fresh
 * [GuardInterceptor] added to the coroutine context. This is a lightweight object
 * allocation — no threads, no channels, no background work. The scope is structurally
 * linked to [viewModelScope], so its lifetime is already bounded by the ViewModel.
 *
 * A new scope per call is perfectly fine: every [launch] or [async] from it becomes a
 * child job of [viewModelScope] and is cancelled when the ViewModel is cleared, exactly
 * as expected. Caching with `lazy` would require a backing property in every ViewModel,
 * bringing back the boilerplate this extension exists to eliminate.
 *
 * ─── Why simpleName ──────────────────────────────────────────────────────────────────────
 *
 * A hardcoded label like `"OrdersViewModel"` becomes stale when the class is renamed during
 * refactoring. IDE rename refactors do not update string literals. [KClass.simpleName]
 * always reflects the CURRENT class name at runtime — it is impossible for it to drift.
 * The fallback `"UnknownViewModel"` covers anonymous classes or obfuscated release builds
 * where [simpleName] may return null.
 */
val ViewModel.guardedScope: CoroutineScope
    get() = viewModelScope.guarded(this::class.simpleName ?: "UnknownViewModel")
