package com.example.util

import android.graphics.Canvas
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR Code real (ZXing), igual ao da web (biblioteca "qrcode": correção M, margem 1).
 * Usado no laudo, no certificado, na etiqueta Niimbot e nas telas — todos apontam para
 * o mesmo endereço do validador ([ValidationPortal.url]).
 */
object QrCode {

    /** Matriz de módulos (true = preto), já com a margem de 1 módulo. */
    fun matrix(data: String, margin: Int = 1): Array<BooleanArray> {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to margin,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        // tamanho 0: a ZXing devolve 1 pixel por módulo
        val bits = QRCodeWriter().encode(data.ifBlank { " " }, BarcodeFormat.QR_CODE, 0, 0, hints)
        return Array(bits.height) { y -> BooleanArray(bits.width) { x -> bits[x, y] } }
    }

    /**
     * Desenha o QR no quadrado (x, y, size) do canvas, com fundo branco.
     * [snapToPixels]: módulos com número inteiro de pontos, centralizados (impressora térmica).
     * Devolve o lado efetivamente desenhado.
     */
    fun draw(
        canvas: Canvas, x: Float, y: Float, size: Float, data: String,
        color: Int = android.graphics.Color.BLACK, snapToPixels: Boolean = false
    ): Float {
        val m = matrix(data)
        val cell = if (snapToPixels) kotlin.math.floor(size / m.size).coerceAtLeast(1f) else size / m.size
        val side = cell * m.size
        val ox = x + ((size - side) / 2f).let { if (snapToPixels) kotlin.math.floor(it) else it }
        val oy = y + ((size - side) / 2f).let { if (snapToPixels) kotlin.math.floor(it) else it }
        val white = Paint().apply { this.color = android.graphics.Color.WHITE; isAntiAlias = false }
        val dark = Paint().apply { this.color = color; isAntiAlias = false }
        canvas.drawRect(x, y, x + size, y + size, white)
        for (r in m.indices) for (c in m[r].indices) {
            if (m[r][c]) canvas.drawRect(ox + c * cell, oy + r * cell, ox + (c + 1) * cell, oy + (r + 1) * cell, dark)
        }
        return side
    }
}
