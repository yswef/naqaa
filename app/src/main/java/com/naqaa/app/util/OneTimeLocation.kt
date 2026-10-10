package com.naqaa.app.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gets one best-effort location fix without keeping a listener alive. A recent cached fix is
 * preferred for speed; otherwise Android is asked for one current fix and an older cache is
 * used only as a fallback. The caller is responsible for requesting location permission.
 */
object OneTimeLocation {

    fun request(context: Context, onResult: (Location?) -> Unit) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(LocationManager::class.java) ?: run {
            onResult(null)
            return
        }
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val lastKnown = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        val now = System.currentTimeMillis()
        val age = lastKnown?.let { now - it.time }
        if (lastKnown != null && age != null && age in 0..RECENT_FIX_AGE_MS) {
            onResult(lastKnown)
            return
        }

        val fallback = lastKnown?.takeIf {
            val locationAge = now - it.time
            locationAge in 0..MAX_CACHED_FIX_AGE_MS
        }
        val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
        if (provider == null) {
            onResult(fallback)
            return
        }

        val delivered = AtomicBoolean(false)
        fun finish(location: Location?) {
            if (delivered.compareAndSet(false, true)) onResult(location)
        }

        runCatching {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                android.os.CancellationSignal(),
                ContextCompat.getMainExecutor(appContext)
            ) { location: Location? -> finish(location ?: fallback) }
        }.onFailure {
            finish(fallback)
        }
    }

    private const val RECENT_FIX_AGE_MS = 2 * 60 * 1000L
    private const val MAX_CACHED_FIX_AGE_MS = 6 * 60 * 60 * 1000L
}
