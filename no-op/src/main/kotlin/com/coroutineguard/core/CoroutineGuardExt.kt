// ── No-op implementation ──────────────────────────────────────────────────────────────────
// This is a no-op stub. It exists solely to provide the same API surface as
// CoroutineScope.guarded() in :core with zero runtime cost.
//
// The real implementation in :core adds GuardInterceptor to the scope's CoroutineContext.
// This stub returns the receiver unchanged — no element is added, no allocation occurs,
// no monitoring is performed.
//
// Use releaseImplementation("com.coroutineguard:no-op") to activate this stub.
// ─────────────────────────────────────────────────────────────────────────────────────────
package com.coroutineguard.core

import kotlinx.coroutines.CoroutineScope

/**
 * No-op stub for [com.coroutineguard.core.guarded].
 *
 * Returns the receiver unchanged. The [label] parameter is accepted to match the real
 * API surface but is immediately discarded — no [kotlinx.coroutines.CoroutineName] is
 * added and no [GuardInterceptor] is inserted into the context.
 */
@Suppress("UNUSED_PARAMETER")
fun CoroutineScope.guarded(label: String? = null): CoroutineScope = this
