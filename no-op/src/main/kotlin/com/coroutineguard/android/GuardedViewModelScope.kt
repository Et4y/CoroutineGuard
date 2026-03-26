// ── No-op implementation ──────────────────────────────────────────────────────────────────
// This is a no-op stub. It exists solely to provide the same API surface as
// GuardedViewModelScope and GuardedViewModelScopeDelegate in :android with zero runtime cost.
//
// guardedScope returns the ViewModel's viewModelScope UNCHANGED — no GuardInterceptor is
// added, no monitoring occurs, no allocations beyond the lazy initialization of viewModelScope
// itself (which would happen anyway when the first coroutine is launched).
//
// Use releaseImplementation("com.coroutineguard:no-op") to activate this stub.
// ─────────────────────────────────────────────────────────────────────────────────────────
package com.coroutineguard.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import java.lang.ref.WeakReference

/**
 * No-op stub for [com.coroutineguard.android.GuardedViewModelScope].
 *
 * Identical interface surface — ViewModels that implement this interface compile and run
 * unchanged against either the :android (debug) or :no-op (release) artifact.
 */
interface GuardedViewModelScope {
    val guardedScope: CoroutineScope
}

/**
 * No-op stub for [com.coroutineguard.android.GuardedViewModelScopeDelegate].
 *
 * Returns [ViewModel.viewModelScope] directly. No [com.coroutineguard.core.GuardInterceptor]
 * is injected; no monitoring is performed. The [WeakReference] and [lazy] patterns are
 * preserved to keep the runtime behaviour identical to the real implementation — the JIT
 * will eliminate both after inlining the trivially short lazy block.
 */
class GuardedViewModelScopeDelegate(viewModel: ViewModel) : GuardedViewModelScope {

    private val viewModelRef = WeakReference(viewModel)

    override val guardedScope: CoroutineScope by lazy {
        val vm = checkNotNull(viewModelRef.get()) {
            "GuardedViewModelScopeDelegate: ViewModel was garbage collected before guardedScope was accessed."
        }
        vm.viewModelScope // no-op: return the unmodified viewModelScope
    }
}
