package org.ok1cdj.kradar.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** A geographic position (degrees). */
data class LatLon(val lat: Double, val lon: Double)

/**
 * Last-known location via AOSP [LocationManager] — deliberately NOT
 * FusedLocationProviderClient, which needs Google Play Services (absent on the
 * Mudita Kompakt). Tries GPS first, then network provider. Returns null when
 * permission is missing or no fix is cached.
 */
object LocationProvider {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun lastKnown(context: Context): LatLon? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        return loc?.let { LatLon(it.latitude, it.longitude) }
    }
}
