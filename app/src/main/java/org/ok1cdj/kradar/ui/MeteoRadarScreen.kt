package org.ok1cdj.kradar.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import org.ok1cdj.kradar.R
import org.ok1cdj.kradar.map.MapData
import org.ok1cdj.kradar.map.MapProjection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeteoRadarScreen(
    vm: RadarViewModel,
    onRequestLocationPermission: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: frame time centered, About (info) button on the right.
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                TextMMD(text = headerText(state), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                ButtonMMD(
                    onClick = { showAbout = true },
                    modifier = Modifier.size(52.dp).border(1.dp, Color.Black, CircleShape),
                    shape = CircleShape,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = stringRes(R.string.about),
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.permissionDenied -> LocationPrompt(onRequestLocationPermission)
                state.location == null -> TextMMD(text = stringRes(R.string.locating), fontSize = 14.sp)
                else -> RadarMap(state)
            }

            if (state.loading) {
                TextMMD(text = stringRes(R.string.loading), fontSize = 13.sp)
            }
        }

        state.error?.let {
            TextMMD(text = stringRes(R.string.error_generic, it), fontSize = 12.sp)
        }

        Controls(vm, state)

        // Mandatory RainViewer attribution + last-update time.
        Box(modifier = Modifier.padding(top = 4.dp)) {
            TextMMD(text = stringRes(R.string.attribution), fontSize = 11.sp)
        }
        state.lastUpdate?.let { t ->
            if (t > 0L) TextMMD(text = stringRes(R.string.updated, updateFmt.format(Date(t * 1000L))), fontSize = 11.sp)
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun LocationPrompt(onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextMMD(text = stringRes(R.string.need_location), fontSize = 14.sp)
        ButtonMMD(
            onClick = onRequest,
            modifier = Modifier.padding(top = 8.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            TextMMD(text = stringRes(R.string.grant_permission), fontSize = 15.sp)
        }
    }
}

/**
 * Stacked layers: a STATIC vector base map (borders + cities) that only recomposes
 * when location/zoom/size change, a DYNAMIC radar overlay repainted as the
 * animation advances (shown only when it matches the current projection, so map
 * and radar never drift), and a center "you are here" marker on top.
 */
@Composable
private fun RadarMap(state: RadarUiState) {
    val loc = state.location ?: return
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        VectorLayer(lat = loc.lat, lon = loc.lon, zoom = state.zoom, tileSize = state.tileSize)
        // Only overlay the raster while it matches the live zoom/center.
        OverlayLayer(if (state.overlayAligned) state.frames.getOrNull(state.currentIndex) else null)
        CenterMarker()
    }
}

/** Static borders + cities. Args are primitives so it skips recomposition during playback. */
@Composable
private fun VectorLayer(lat: Double, lon: Double, zoom: Int, tileSize: Int) {
    val context = LocalContext.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension
        val scale = side / tileSize
        val proj = MapProjection(lat, lon, zoom, tileSize)
        val bounds = proj.visibleBounds()
        val margin = 0.5

        // --- borders ---
        val path = Path()
        for (ring in MapData.borders(context)) {
            // cheap cull: skip rings with no vertex near the viewport
            var near = false
            var i = 0
            while (i < ring.size) {
                if (bounds.contains(ring[i].toDouble(), ring[i + 1].toDouble(), margin)) {
                    near = true; break
                }
                i += 2
            }
            if (!near) continue
            var first = true
            var j = 0
            while (j < ring.size) {
                val p = proj.project(ring[j].toDouble(), ring[j + 1].toDouble())
                val x = p[0] * scale
                val y = p[1] * scale
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                j += 2
            }
        }
        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // --- cities: dots always; labels hybrid (full name for tier 1, abbr for
        //     smaller) with greedy collision-avoidance so text stays readable ---
        val maxTier = when {
            zoom >= 7 -> 3
            zoom == 6 -> 2
            else -> 1
        }
        val labelPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 11f * density
            isAntiAlias = true
        }
        val textH = (labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent)
        val placed = ArrayList<Rect>()
        val candidate = Rect()
        var labelCount = 0
        val cities = MapData.cities(context).filter { it.tier <= maxTier }.sortedBy { it.tier }
        for (c in cities) {
            if (!bounds.contains(c.lat, c.lon)) continue
            val p = proj.project(c.lat, c.lon)
            val x = p[0] * scale
            val y = p[1] * scale
            if (x < 0f || y < 0f || x > side || y > side) continue
            drawCircle(color = Color.Black, radius = 2.2f, center = Offset(x, y))

            if (labelCount >= MAX_LABELS) continue
            val text = if (c.tier == 1) c.name else c.abbr
            val w = labelPaint.measureText(text)
            val lx = x + 4f
            val ly = y - 4f
            candidate.set(lx.toInt(), (ly - textH).toInt(), (lx + w).toInt(), ly.toInt())
            var collides = false
            for (r in placed) {
                if (Rect.intersects(r, candidate)) { collides = true; break }
            }
            if (collides) continue
            placed.add(Rect(candidate))
            drawContext.canvas.nativeCanvas.drawText(text, lx, ly, labelPaint)
            labelCount++
        }
    }
}

/** Dynamic radar overlay for the current frame; repaints when [bmp] changes. */
@Composable
private fun OverlayLayer(bmp: android.graphics.Bitmap?) {
    if (bmp == null || bmp.isRecycled) return
    val image = remember(bmp) { bmp.asImageBitmap() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension.toInt()
        drawImage(image = image, dstSize = androidx.compose.ui.unit.IntSize(side, side))
    }
}

/** "You are here" crosshair at the box center (the map is centered on the user). */
@Composable
private fun CenterMarker() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // white halo so the marker reads over dark rain, then a black ring + cross
        drawCircle(Color.White, radius = 6.5f, center = Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(Color.Black, radius = 6.5f, center = Offset(cx, cy), style = Stroke(width = 1.6f))
        drawLine(Color.Black, Offset(cx - 10f, cy), Offset(cx + 10f, cy), strokeWidth = 1.6f)
        drawLine(Color.Black, Offset(cx, cy - 10f), Offset(cx, cy + 10f), strokeWidth = 1.6f)
    }
}

@Composable
private fun Controls(vm: RadarViewModel, state: RadarUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolButton(stringRes(R.string.zoom_out)) { vm.zoomOut() }
        IconRoundButton(R.drawable.ic_step_back, stringRes(R.string.step_back)) { vm.stepBack() }
        IconRoundButton(
            iconRes = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            desc = if (state.isPlaying) stringRes(R.string.pause) else stringRes(R.string.play),
        ) { vm.togglePlay() }
        IconRoundButton(R.drawable.ic_step_fwd, stringRes(R.string.step_fwd)) { vm.stepForward() }
        SymbolButton(stringRes(R.string.zoom_in)) { vm.zoomIn() }
    }
}

private val ROUND_SIZE = 46.dp

@Composable
private fun SymbolButton(label: String, onClick: () -> Unit) {
    ButtonMMD(
        onClick = onClick,
        modifier = Modifier.size(ROUND_SIZE).border(1.dp, Color.Black, CircleShape),
        shape = CircleShape,
    ) {
        TextMMD(text = label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconRoundButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    ButtonMMD(
        onClick = onClick,
        modifier = Modifier.size(ROUND_SIZE).border(1.dp, Color.Black, CircleShape),
        shape = CircleShape,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = Color.Black,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val MAX_LABELS = 40

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val updateFmt = SimpleDateFormat("d. M. HH:mm", Locale.getDefault())

@Composable
private fun headerText(state: RadarUiState): String {
    val t = state.currentTime ?: return "kRadar"
    if (t <= 0L) return "kRadar"
    val time = timeFmt.format(Date(t * 1000L))
    // Latest past frame shows the "now" tag; nowcast frames get a leading marker.
    val isLatestPast = !state.currentIsNowcast && state.currentIndex == lastPastIndex(state)
    return when {
        state.currentIsNowcast -> "+$time"
        isLatestPast -> "$time (${stringRes(R.string.now)})"
        else -> time
    }
}

private fun lastPastIndex(state: RadarUiState): Int {
    var idx = -1
    state.frameNowcast.forEachIndexed { i, now -> if (!now) idx = i }
    return idx
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, arg: Any): String = androidx.compose.ui.res.stringResource(id, arg)
