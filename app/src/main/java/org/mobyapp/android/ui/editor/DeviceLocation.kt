package org.mobyapp.android.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mobyapp.android.data.Location
import java.util.Locale
import kotlin.coroutines.resume

/** Finds the device's current location, without Google Play services. */
object DeviceLocation {

    /**
     * Returns the current location, with a place name when one can be found.
     * The caller must already hold a location permission.
     *
     * Accuracy is traded for speed: a place name and map coordinates only need
     * to be roughly right, and GPS can take many seconds, especially indoors.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = providers(manager)
        if (providers.isEmpty()) return null

        // Without a timeout, a device that can't get a fix would wait
        // indefinitely. Fall back to the most recent known location.
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) { currentFix(context, manager, providers.first()) }
            ?: providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            ?: return null

        return Location(
            name = placeName(context, fix.latitude, fix.longitude),
            latitude = round(fix.latitude),
            longitude = round(fix.longitude),
        )
    }

    /**
     * The enabled providers, fastest first. Android 12 and later have a fused
     * provider, which combines Wi-Fi, cell towers and GPS. Before that, the
     * network provider (Wi-Fi and cell towers) is much faster than GPS.
     */
    private fun providers(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { it in manager.allProviders && manager.isProviderEnabled(it) }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(context: Context, manager: LocationManager, provider: String): android.location.Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val callback = java.util.function.Consumer<android.location.Location?> { continuation.resume(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Balanced accuracy: about 100 m, mostly from Wi-Fi and cell
                // towers, so it is quick and saves battery. A recent enough
                // location is returned straight away.
                val request = LocationRequest.Builder(0)
                    .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(FIX_TIMEOUT_MS)
                    .build()
                manager.getCurrentLocation(provider, request, signal, context.mainExecutor, callback)
            } else {
                manager.getCurrentLocation(provider, signal, context.mainExecutor, callback)
            }
        }
    }

    private const val FIX_TIMEOUT_MS = 15_000L

    /** Rounds to 6 decimal places (about 10 cm), which is plenty for a post. */
    private fun round(value: Double): Double = Math.round(value * 1_000_000) / 1_000_000.0

    /** Looks up a name such as "Boulder, Colorado", if the device can. */
    @Suppress("DEPRECATION")
    private suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                val address = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
                listOfNotNull(address?.locality ?: address?.subAdminArea, address?.adminArea)
                    .distinct()
                    .joinToString(", ")
                    .ifEmpty { null }
            }.getOrNull()
        }
}
