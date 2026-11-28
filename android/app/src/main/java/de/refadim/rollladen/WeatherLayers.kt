package de.refadim.rollladen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Quelle für das Regenradar */
@kotlinx.serialization.Serializable
enum class RadarSource(val label: String) { OFF("Aus"), DWD("DWD"), RAINVIEWER("RainViewer") }

/** Grundkarte unter Radar und Wolken */
@kotlinx.serialization.Serializable
enum class BaseMap(val label: String) { DARK("Dunkel"), LIGHT("Hell (grau)"), OSM("OpenStreetMap") }

object WeatherSources {
    const val DWD_WMS = "https://maps.dwd.de/geoserver/dwd/wms"
    const val EUMETSAT_WMS = "https://view.eumetsat.int/geoserver/wms"
    private const val RAINVIEWER_JSON = "https://api.rainviewer.com/public/weather-maps.json"

    /** RainViewer liefert kostenlos nur bis Zoomstufe 7 */
    const val RAINVIEWER_MAX_ZOOM = 7

    /**
     * Grundkarte ist immer OpenStreetMap; „Dunkel“ und „Hell (grau)“ entstehen per Farbfilter
     * (kein zusätzlicher Kartendienst, kein API-Schlüssel).
     */
    fun baseFilter(b: BaseMap): ColorMatrixColorFilter? {
        val r = 0.299f
        val g = 0.587f
        val bl = 0.114f
        return when (b) {
            BaseMap.OSM -> null
            // Graustufen, invertiert und abgedunkelt: helle Flächen werden dunkelgrau, Straßen fast schwarz
            BaseMap.DARK -> {
                val k = -0.85f
                ColorMatrixColorFilter(
                    floatArrayOf(
                        r * k, g * k, bl * k, 0f, 255f,
                        r * k, g * k, bl * k, 0f, 255f,
                        r * k, g * k, bl * k, 0f, 262f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
            // Helle Graustufen
            BaseMap.LIGHT -> {
                val k = 0.85f
                ColorMatrixColorFilter(
                    floatArrayOf(
                        r * k, g * k, bl * k, 0f, 35f,
                        r * k, g * k, bl * k, 0f, 35f,
                        r * k, g * k, bl * k, 0f, 35f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
        }
    }

    fun baseAttribution(b: BaseMap) = "Karte © OpenStreetMap-Mitwirkende"

    data class RainFrame(val host: String, val path: String, val time: Long)

    /** Neuester Radar-Zeitpunkt von RainViewer */
    suspend fun rainViewerFrame(): RainFrame = withContext(Dispatchers.IO) {
        val j = JSONObject(get(RAINVIEWER_JSON).second)
        val past = j.getJSONObject("radar").getJSONArray("past")
        val last = past.getJSONObject(past.length() - 1)
        RainFrame(j.getString("host"), last.getString("path"), last.getLong("time"))
    }

    /** Lädt eine Kachel zur Probe; null = in Ordnung, sonst Fehlertext */
    suspend fun probe(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val (type, body) = get(url)
            when {
                type.startsWith("image") -> null
                body.contains("LayerNotDefined") || body.contains("not defined", ignoreCase = true) -> "Ebene unbekannt"
                else -> "keine Bilddaten"
            }
        } catch (e: Exception) {
            e.message ?: "nicht erreichbar"
        }
    }

    /** Alle Ebenen eines WMS (Name, Titel) */
    suspend fun capabilities(base: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val xml = get("$base?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetCapabilities").second
        Regex("""<Layer[^>]*>\s*<Name>([^<]+)</Name>\s*<Title>([^<]*)</Title>""")
            .findAll(xml)
            .map { it.groupValues[1].trim() to unescape(it.groupValues[2].trim()) }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .toList()
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    /** Kachelindex des Standorts bei einer Zoomstufe (für die Probe) */
    fun tileIndexAt(lat: Double, lon: Double, zoom: Int): Long {
        val n = 1 shl zoom
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val y = floor((1.0 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return MapTileIndex.getTileIndex(zoom, x, y)
    }

    /** HTTP-GET: (Content-Type, Text) – Text nur bei Nicht-Bildern */
    private fun get(url: String): Pair<String, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "de.refadim.rollladen")
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val type = conn.contentType ?: ""
            val body = if (type.startsWith("image")) {
                conn.inputStream.use { it.readBytes() }
                ""
            } else conn.inputStream.bufferedReader().use { it.readText() }
            return type to body
        } finally {
            conn.disconnect()
        }
    }
}

/** WMS-Kacheln in Web-Mercator (EPSG:3857) */
class WmsTileSource(name: String, private val base: String, private val layer: String, private val stamp: Long) :
    OnlineTileSourceBase(name, 0, 18, 256, ".png", arrayOf(base)) {

    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val world = 2 * PI * 6378137.0
        val half = world / 2
        val tile = world / (1 shl z)
        val minX = -half + x * tile
        val maxX = minX + tile
        val maxY = half - y * tile
        val minY = maxY - tile
        return base + "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap" +
            "&LAYERS=" + URLEncoder.encode(layer, "UTF-8") + "&STYLES=&SRS=EPSG:3857" +
            "&BBOX=$minX,$minY,$maxX,$maxY&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE&_t=$stamp"
    }
}

/** RainViewer-Radarkacheln (Farbschema „Universal Blue“ = 2, geglättet, mit Schnee) */
class RainViewerTileSource(name: String, private val frame: WeatherSources.RainFrame) :
    OnlineTileSourceBase(name, 0, WeatherSources.RAINVIEWER_MAX_ZOOM, 256, ".png", arrayOf(frame.host)) {

    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "${frame.host}${frame.path}/256/$z/$x/$y/2/1_1.png"
    }
}
