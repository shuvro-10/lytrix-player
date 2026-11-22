package com.musicplayer.lytrix.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.eq.audio.BiquadCoeffs
import com.musicplayer.lytrix.eq.audio.BiquadMath
import com.musicplayer.lytrix.eq.data.ParametricEQBand
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private const val GRAPH_SAMPLE_RATE = 48000
private const val MIN_FREQ = 20.0
private const val MAX_FREQ = 20000.0
private const val GRAPH_PADDING_LEFT = 42f
private const val GRAPH_PADDING_RIGHT = 10f
private const val GRAPH_PADDING_TOP = 10f
private const val GRAPH_PADDING_BOTTOM = 24f

private data class EqGraphData(
    val points: List<Pair<Double, Double>>,
    val dbTop: Double,
    val dbBottom: Double,
    val dbRange: Double,
    val dbStep: Double
)

@Composable
fun EqFrequencyResponseGraph(
    bands: List<ParametricEQBand>,
    preamp: Double,
    accentColor: Color,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val activeStrokeColor = if (isEnabled) accentColor else accentColor.copy(alpha = 0.35f)
    val fillColor = activeStrokeColor.copy(alpha = if (isEnabled) 0.18f else 0.06f)
    val zeroLineColor = activeStrokeColor.copy(alpha = 0.5f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    val graphData = remember(bands, preamp) {
        computeEqGraphData(bands, preamp)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(6.dp)
        ) {
            val plotLeft = GRAPH_PADDING_LEFT
            val plotRight = size.width - GRAPH_PADDING_RIGHT
            val plotTop = GRAPH_PADDING_TOP
            val plotBottom = size.height - GRAPH_PADDING_BOTTOM
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            val logMin = log10(MIN_FREQ)
            val logMax = log10(MAX_FREQ)

            fun freqToX(freq: Double): Float {
                val logF = log10(freq.coerceIn(MIN_FREQ, MAX_FREQ))
                return plotLeft + ((logF - logMin) / (logMax - logMin) * plotWidth).toFloat()
            }

            fun dbToY(db: Double): Float {
                return plotTop + ((graphData.dbTop - db) / graphData.dbRange * plotHeight).toFloat()
            }

            clipRect(0f, 0f, size.width, size.height) {
                var db = graphData.dbBottom
                while (db <= graphData.dbTop + 0.001) {
                    val y = dbToY(db)
                    val isZero = abs(db) < 0.01
                    drawLine(
                        color = if (isZero) zeroLineColor else gridColor,
                        start = Offset(plotLeft, y),
                        end = Offset(plotRight, y),
                        strokeWidth = if (isZero) 1.5f else 0.75f
                    )
                    val labelText = when {
                        isZero -> "0"
                        db == db.toLong().toDouble() -> db.toLong().toString()
                        else -> String.format("%.1f", db)
                    }
                    drawDbLabel(textMeasurer, labelText, labelStyle, y, plotLeft)
                    db += graphData.dbStep
                }

                val freqLandmarks = listOf(100.0, 1000.0, 10000.0)
                val freqLabels = listOf("100", "1k", "10k")
                for (i in freqLandmarks.indices) {
                    val x = freqToX(freqLandmarks[i])
                    drawLine(
                        color = gridColor,
                        start = Offset(x, plotTop),
                        end = Offset(x, plotBottom),
                        strokeWidth = 0.75f
                    )
                    val measured = textMeasurer.measure(freqLabels[i], labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(x - measured.size.width / 2f, plotBottom + 4f)
                    )
                }

                if (graphData.points.isNotEmpty()) {
                    val curvePath = Path()
                    val fillPath = Path()

                    val firstX = freqToX(graphData.points.first().first)
                    val firstY = dbToY(graphData.points.first().second)
                    curvePath.moveTo(firstX, firstY)
                    fillPath.moveTo(firstX, plotBottom)
                    fillPath.lineTo(firstX, firstY)

                    for (i in 1 until graphData.points.size) {
                        val (freq, dbVal) = graphData.points[i]
                        val x = freqToX(freq)
                        val y = dbToY(dbVal)
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }

                    val lastX = freqToX(graphData.points.last().first)
                    fillPath.lineTo(lastX, plotBottom)
                    fillPath.close()

                    drawPath(fillPath, color = fillColor)
                    drawPath(curvePath, color = activeStrokeColor, style = Stroke(width = 2.2.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawDbLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    y: Float,
    plotLeft: Float
) {
    val measured = textMeasurer.measure(text, style)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(plotLeft - measured.size.width - 5f, y - measured.size.height / 2f)
    )
}

private fun computeEqGraphData(bands: List<ParametricEQBand>, preamp: Double): EqGraphData {
    val enabledBands = bands.filter { it.enabled }
    val coeffsList: List<BiquadCoeffs> = enabledBands.map { band ->
        BiquadMath.computeCoefficients(
            sampleRate = GRAPH_SAMPLE_RATE,
            frequency = band.frequency,
            gainDb = band.gain,
            q = band.q,
            filterType = band.filterType
        )
    }

    val numPoints = 180
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val points = ArrayList<Pair<Double, Double>>(numPoints)

    var minDb = 0.0
    var maxDb = 0.0
    for (i in 0 until numPoints) {
        val logF = logMin + (logMax - logMin) * i / (numPoints - 1)
        val freq = 10.0.pow(logF)
        var totalDb = 0.0
        for (coeffs in coeffsList) {
            totalDb += BiquadMath.magnitudeResponseDb(coeffs, freq, GRAPH_SAMPLE_RATE)
        }
        points.add(freq to totalDb)
        if (totalDb < minDb) minDb = totalDb
        if (totalDb > maxDb) maxDb = totalDb
    }

    val peakAbs = max(abs(minDb), abs(maxDb))
    val dbStep = when {
        peakAbs > 10.0 -> 5.0
        peakAbs > 5.0 -> 3.0
        else -> 2.5
    }
    val halfRange = max(ceil((peakAbs + 1.0) / dbStep) * dbStep, dbStep)

    return EqGraphData(
        points = points,
        dbTop = halfRange,
        dbBottom = -halfRange,
        dbRange = 2.0 * halfRange,
        dbStep = dbStep
    )
}
