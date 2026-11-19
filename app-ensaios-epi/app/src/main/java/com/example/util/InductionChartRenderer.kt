package com.example.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.domain.InductionPoint
import kotlin.math.ceil

/**
 * Gráfico "Detector de Tensão" (Tensão kV × Distância cm) do ensaio do detector
 * por indução — mesma geometria da plataforma web (src/utils/inductionChart.ts):
 * área 560 × 300, eixo X até 140 cm (ou mais), eixo Y até 60 kV, linha laranja.
 * Desenha em qualquer Canvas (tela do ensaio e laudo em PDF).
 */
object InductionChartRenderer {

    const val BASE_W = 560f
    const val BASE_H = 300f
    private const val PAD_L = 52f
    private const val PAD_R = 18f
    private const val PAD_T = 36f
    private const val PAD_B = 40f
    private const val X_MAX = 140f
    private const val X_STEP = 20f
    private const val Y_MAX = 60f
    private const val Y_STEP = 10f
    private const val LINE = 0xFFF4A259.toInt()
    private const val GRID = 0xFFE2E8F0.toInt()
    private const val MINOR = 0xFFF1F5F9.toInt()
    private const val AXIS_TEXT = 0xFF64748B.toInt()

    /** Escala do eixo X: 140 cm ou mais, se alguma distância passar. */
    fun xMax(points: List<InductionPoint>): Float {
        val max = points.maxOfOrNull { it.distance_cm ?: 0.0 }?.toFloat() ?: 0f
        return maxOf(X_MAX, ceil((max * 1.1f) / X_STEP) * X_STEP)
    }

    /** Desenha o gráfico no retângulo (left, top, width, height) do canvas. */
    fun draw(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, points: List<InductionPoint>) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(width / BASE_W, height / BASE_H)

        val xMax = xMax(points)
        val plotW = BASE_W - PAD_L - PAD_R
        val plotH = BASE_H - PAD_T - PAD_B
        fun px(x: Float) = PAD_L + (x / xMax) * plotW
        fun py(y: Float) = PAD_T + plotH - (y / Y_MAX) * plotH

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            hinting = Paint.HINTING_OFF
        }

        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(0f, 0f, BASE_W, BASE_H, fill)
        stroke.color = 0xFFCBD5E1.toInt()
        stroke.strokeWidth = 0.8f
        canvas.drawRect(RectF(0.5f, 0.5f, BASE_W - 0.5f, BASE_H - 0.5f), stroke)

        // título
        text.color = 0xFF475569.toInt()
        text.textSize = 15f
        text.textAlign = Paint.Align.CENTER
        canvas.drawText("Detector de Tensão", BASE_W / 2, 18f + text.textSize * 0.35f, text)

        // grade fina (1/5 da divisão) e principal
        var x = 0f
        while (x <= xMax + 0.001f) {
            val major = (x % X_STEP) < 0.001f
            stroke.color = if (major) GRID else MINOR
            stroke.strokeWidth = if (major) 0.8f else 0.5f
            canvas.drawLine(px(x), py(0f), px(x), py(Y_MAX), stroke)
            x += X_STEP / 5
        }
        var y = 0f
        while (y <= Y_MAX + 0.001f) {
            val major = (y % Y_STEP) < 0.001f
            stroke.color = if (major) GRID else MINOR
            stroke.strokeWidth = if (major) 0.8f else 0.5f
            canvas.drawLine(px(0f), py(y), px(xMax), py(y), stroke)
            y += Y_STEP / 5
        }

        // rótulos dos eixos
        text.color = AXIS_TEXT
        text.textSize = 9f
        text.textAlign = Paint.Align.CENTER
        x = 0f
        while (x <= xMax + 0.001f) {
            canvas.drawText(x.toInt().toString().padStart(2, '0'), px(x), py(0f) + 5f + 9f, text)
            x += X_STEP
        }
        text.textAlign = Paint.Align.RIGHT
        y = 0f
        while (y <= Y_MAX + 0.001f) {
            canvas.drawText(y.toInt().toString().padStart(2, '0'), px(0f) - 6f, py(y) + 3f, text)
            y += Y_STEP
        }
        text.textSize = 10f
        text.textAlign = Paint.Align.CENTER
        canvas.drawText("Distância (cm)", PAD_L + plotW / 2, BASE_H - 8f, text)
        canvas.save()
        canvas.translate(14f, PAD_T + plotH / 2)
        canvas.rotate(-90f)
        canvas.drawText("Tensão (KV)", 0f, 3f, text)
        canvas.restore()

        // curva: só os pontos com distância obtida, na ordem da tensão
        val pts = points
            .filter { it.detected && (it.distance_cm ?: 0.0) > 0 }
            .sortedBy { it.voltage_kV }
            .map { px(it.distance_cm!!.toFloat()) to py(it.voltage_kV.toFloat()) }
        if (pts.isNotEmpty()) {
            stroke.color = LINE
            stroke.strokeWidth = 1.8f
            stroke.strokeJoin = Paint.Join.ROUND
            val path = Path()
            pts.forEachIndexed { i, (cx, cy) -> if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy) }
            canvas.drawPath(path, stroke)
            fill.color = LINE
            pts.forEach { (cx, cy) -> canvas.drawCircle(cx, cy, 3f, fill) }
        }
        canvas.restore()
    }
}
