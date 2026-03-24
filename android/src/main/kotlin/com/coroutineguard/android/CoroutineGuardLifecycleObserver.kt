package com.coroutineguard.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.coroutineguard.core.CoroutineGuardConfig
import kotlinx.coroutines.Job
import java.lang.ref.WeakReference

/**
 * Hooks into the Activity lifecycle to detect coroutines that outlive their host scope.
 *
 * ─── WHY ActivityLifecycleCallbacks is the right hook ────────────────────────────────────
 *
 * onActivityDestroyed fires AFTER Activity.onDestroy() has returned. By that point:
 *   1. The Android framework has called LifecycleRegistry.handleLifecycleEvent(ON_DESTROY),
 *      which moves the Lifecycle to DESTROYED and cancels the activity's lifecycleScope.
 *   2. All coroutines properly launched into lifecycleScope have received a cancellation
 *      signal and should be winding down.
 *
 * Any coroutine still isActive AFTER cancellation was issued either:
 *   a) is blocking on non-cancellable code (e.g., a blocking I/O call on Dispatchers.IO
 *      that ignores the cancellation signal), or
 *   b) was launched into an external scope (GlobalScope, a ViewModel's custom scope, etc.)
 *      and was never tied to this Activity's lifetime at all — a genuine scope leak.
 *
 * Alternative hooks and why they're worse:
 *   • LifecycleObserver on each Activity  → requires developer opt-in; defeats the purpose
 *   • onActivityStopped                   → too early; background activities may have
 *                                           intentional ongoing work (e.g., music playback)
 *   • Process.addShutdownHook             → too late; no per-component attribution possible
 *
 * ─── WHY WeakReference<Activity> ─────────────────────────────────────────────────────────
 *
 * After onActivityDestroyed, the Activity object should be eligible for GC. If we capture
 * it directly in the postDelayed lambda below, the Runnable holds a strong reference for
 * the full SCOPE_LEAK_CHECK_DELAY_MS window, keeping the Activity alive in the heap even
 * though the framework has already discarded it — we would be CAUSING the memory leak we
 * are trying to detect.
 *
 * WeakReference says: "I want to use this object if it still exists, but I am not the
 * reason it stays alive." If the GC reclaims the Activity before the delayed check runs,
 * weakRef.get() returns null and we skip the check — a missed detection is acceptable;
 * a library-induced memory leak is not.
 */
internal class CoroutineGuardLifecycleObserver(
    private val config: CoroutineGuardConfig,
) : Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onActivityDestroyed(activity: Activity) {
        if (!config.enableScopeLeakDetection) return

        // Capture a WeakReference before handing `activity` off to the delayed lambda.
        // See class-level KDoc for why a strong reference would be harmful here.
        val weakActivity = WeakReference(activity)

        // Delay the check to give coroutines a reasonable window to respond to the
        // cancellation signal that was issued during onDestroy(). A coroutine doing a
        // short suspension (e.g., withContext or a yield) will be done well within this
        // window. One that is genuinely stuck will still be active after the delay.
        mainHandler.postDelayed(
            { checkForScopeLeak(weakActivity) },
            SCOPE_LEAK_CHECK_DELAY_MS,
        )
    }

    private fun checkForScopeLeak(weakActivity: WeakReference<Activity>) {
        // get() returns null if the Activity was GC'd — nothing to check, nothing leaking.
        val activity = weakActivity.get() ?: return

        // Only LifecycleOwner activities have a managed coroutine scope.
        // Plain Activity (non-AndroidX) has no lifecycleScope and is not our concern.
        if (activity !is LifecycleOwner) return

        val scopeJob = activity.lifecycleScope.coroutineContext[Job] ?: return

        // Under structured concurrency, scopeJob.children are empty after the scope is
        // cancelled and all children have responded. Any child still isActive here is
        // either blocking cancellation or leaked into a parent scope.
        val leakedChildren = scopeJob.children.filter { it.isActive }.toList()
        if (leakedChildren.isNotEmpty()) {
            val label = activity::class.simpleName
            config.onScopeLeak?.invoke(label)
        }
    }

    // ─── Unused callbacks — required by the interface ────────────────────────────────────

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        // 500 ms is long enough for cooperating coroutines to complete cancellation
        // (a typical suspension point responds in < 1 frame ≈ 16 ms) but short enough
        // to surface a stuck coroutine before the developer moves to a new screen.
        const val SCOPE_LEAK_CHECK_DELAY_MS = 500L
    }
}
