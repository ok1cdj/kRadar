package org.ok1cdj.kradar.map

import android.content.Context
import org.json.JSONArray
import java.nio.charset.StandardCharsets

/**
 * A city label: full [name] + short [abbr] + position + tier
 * (1 = >=300k pop, 2 = >=150k, 3 = >=50k).
 */
data class City(val name: String, val abbr: String, val lat: Double, val lon: Double, val tier: Int)

/**
 * Static vector base map, loaded once from bundled JSON assets converted from
 * MeteoPlaneRadar (Natural Earth borders, GeoNames cities). Loaded off the main
 * thread by the caller; cached process-wide.
 *
 * Each ring is a flat FloatArray [lat0,lon0,lat1,lon1,...] to keep ~30k points
 * compact and cheap to iterate while drawing.
 */
object MapData {
    @Volatile private var borders: List<FloatArray>? = null
    @Volatile private var cities: List<City>? = null

    fun borders(context: Context): List<FloatArray> =
        borders ?: synchronized(this) {
            borders ?: loadBorders(context).also { borders = it }
        }

    fun cities(context: Context): List<City> =
        cities ?: synchronized(this) {
            cities ?: loadCities(context).also { cities = it }
        }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).use { it.readBytes().toString(StandardCharsets.UTF_8) }

    private fun loadBorders(context: Context): List<FloatArray> {
        val root = JSONArray(readAsset(context, "borders.json"))
        val out = ArrayList<FloatArray>(root.length())
        for (r in 0 until root.length()) {
            val ring = root.getJSONArray(r)
            val flat = FloatArray(ring.length() * 2)
            for (p in 0 until ring.length()) {
                val pt = ring.getJSONArray(p) // [lat, lon]
                flat[p * 2] = pt.getDouble(0).toFloat()
                flat[p * 2 + 1] = pt.getDouble(1).toFloat()
            }
            out.add(flat)
        }
        return out
    }

    private fun loadCities(context: Context): List<City> {
        val arr = JSONArray(readAsset(context, "cities.json"))
        val out = ArrayList<City>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                City(
                    name = o.getString("name"),
                    abbr = o.optString("abbr", o.getString("name")),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    tier = o.optInt("tier", 3),
                )
            )
        }
        return out
    }
}
