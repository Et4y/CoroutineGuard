// ── No-op implementation ──────────────────────────────────────────────────────────────────
// This is a no-op stub. It exists solely to provide the same API surface as
// ViewModel+Guard.kt in :android with zero runtime cost.
//
// guardedScope returns the ViewModel's viewModelScope UNCHANGED — no GuardInterceptor is
// added, no monitoring occurs, no allocations beyond viewModelScope itself.
//
// Use releaseImplementation("com.coroutineguard:no-op") to activate this stub.
// ─────────────────────────────────────────────────────────────────────────────────────────
package com.coroutineguard.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/**
 * No-op stub for [com.coroutineguard.android.guardedScope].
 *
 * Returns [ViewModel.viewModelScope] directly. No [com.coroutineguard.core.GuardInterceptor]
 * is injected; no monitoring is performed. The extension property signature is identical to
 * the real implementation so app code compiles unchanged against either artifact.
 */
val ViewModel.guardedScope: CoroutineScope
    get() = viewModelScope
