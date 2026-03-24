package com.coroutineguard.core

import kotlin.coroutines.CoroutineContext

internal class GuardElement(
    val startTime: Long,
    val label: String?,
    val thresholdMs: Long,
    val onHang: ((label: String?, durationMs: Long) -> Unit)?,
    val onSilentCancel: ((label: String?, cause: Throwable) -> Unit)?,
    val onScopeLeak: ((label: String?) -> Unit)?,
) : CoroutineContext.Element {

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<GuardElement>
}
