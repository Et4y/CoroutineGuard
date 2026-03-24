package com.coroutineguard.core

/**
 * Thrown (and passed to crash reporters) when a coroutine runs longer than
 * [com.coroutineguard.core.CoroutineGuardConfig.hangThresholdMs].
 *
 * Extends [RuntimeException] so crash tools (Firebase Crashlytics, Sentry, etc.) treat it
 * as an unchecked exception and group it by class name + message in their dashboards.
 * Using a dedicated subclass — rather than a plain RuntimeException — ensures hang events
 * form their own issue group and are not merged with unrelated crashes.
 */
class CoroutineHangException(
    val label: String?,
    val durationMs: Long,
) : RuntimeException("Coroutine '${label ?: "unnamed"}' exceeded hang threshold: ran for ${durationMs}ms")

/**
 * Thrown when a coroutine is cancelled via a [kotlinx.coroutines.CancellationException]
 * that carries no wrapped root cause — indicating the cancellation was "silent" (no error
 * context for the caller).
 *
 * [cause] is propagated as the Java exception cause so crash tools display the full chain.
 * It may be null when the CancellationException itself carried no cause.
 */
class SilentCancellationException(
    val label: String?,
    cause: Throwable?,
) : RuntimeException("Coroutine '${label ?: "unnamed"}' was silently cancelled with no cause", cause)

/**
 * Thrown when coroutines are found still active after their host scope (e.g., an Activity's
 * lifecycleScope) has been destroyed — indicating they escaped structured concurrency.
 */
class ScopeLeakException(
    val label: String?,
) : RuntimeException("Coroutine '${label ?: "unnamed"}' outlived its scope: still active after scope destruction")
