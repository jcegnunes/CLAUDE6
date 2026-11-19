package com.example.util

import com.example.data.model.TestRecordEntity
import com.example.data.service.LabelPaperSize

/**
 * Modelo da etiqueta Niimbot — o mesmo da plataforma web (src/services/niimbotLabelModel.ts).
 *
 * Coordenadas em pontos da impressora (8 pontos/mm, 203 DPI):
 *  - 30x20 mm: 240 x 160 (compacto);
 *  - 50x30 mm: 384 x 240 (padrão; a cabeça da B1 imprime 384 pontos = 48 mm);
 *  - 50x80 mm: 384 x 640 (em pé).
 * Sem bordas laterais: a faixa do título vai até o topo e a do status até a base, de ponta a ponta.
 * Uma única lista de elementos desenha a etiqueta (pré-visualização e impressão) e
 * gera os elementos editáveis do arquivo JCPS.
 */
sealed class LabelPrimitive {
    abstract val id: String

    data class Rect(
        override val id: String, val x: Float, val y: Float, val w: Float, val h: Float,
        val fill: Boolean = false, val stroke: Float = 1f
    ) : LabelPrimitive()

    data class Text(
        override val id: String, val x: Float, val y: Float, val size: Float, val content: String,
        val bold: Boolean = false, val mono: Boolean = false, val white: Boolean = false,
        val align: Align = Align.LEFT,
        /** largura máxima: a fonte diminui só se o texto passar dela */
        val maxWidth: Float? = null,
        /** -90 = texto na vertical, de baixo para cima */
        val rotate: Float = 0f,
        /** y no meio da altura do texto (faixa de status); senão, linha de base */
        val middle: Boolean = false
    ) : LabelPrimitive()

    data class Qr(override val id: String, val x: Float, val y: Float, val size: Float, val content: String) : LabelPrimitive()

    enum class Align { LEFT, CENTER, RIGHT }
}

data class LabelModel(val width: Int, val height: Int, val primitives: List<LabelPrimitive>)

object LabelModels {
    const val TITLE = "JVM ENGENHARIA"
    const val PHONE = "61 98530-0679"

    private fun reportNumber(t: TestRecordEntity) = t.reportNumber.trim().ifBlank { "—" }

    fun build(test: TestRecordEntity, size: LabelPaperSize): LabelModel {
        val url = ValidationPortal.url(test.validationCode)
        val status = "STATUS: ${test.result.uppercase()}"
        val testDate = DateUtils.formatDate(test.testDate)
        val retest = DateUtils.formatDate(test.retestDueDate)
        val L = LabelPrimitive.Align.LEFT
        val C = LabelPrimitive.Align.CENTER
        val R = LabelPrimitive.Align.RIGHT
        fun rect(id: String, x: Number, y: Number, w: Number, h: Number, fill: Boolean = false, stroke: Number = 1) =
            LabelPrimitive.Rect(id, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), fill, stroke.toFloat())
        fun text(
            id: String, x: Number, y: Number, size: Number, content: String, bold: Boolean = true, mono: Boolean = false,
            white: Boolean = false, align: LabelPrimitive.Align = L, maxWidth: Number? = null, rotate: Number = 0, middle: Boolean = false
        ) = LabelPrimitive.Text(id, x.toFloat(), y.toFloat(), size.toFloat(), content, bold, mono, white, align, maxWidth?.toFloat(), rotate.toFloat(), middle)
        fun qr(x: Number, y: Number, s: Number) = LabelPrimitive.Qr("qr", x.toFloat(), y.toFloat(), s.toFloat(), url)

        return when (size) {
            LabelPaperSize.SIZE_30X20 -> LabelModel(240, 160, listOf(
                rect("header_bg", 0, 0, 240, 22, fill = true),
                text("title", 6, 16, 10, TITLE, white = true),
                text("phone", 234, 16, 9, PHONE, white = true, align = R),
                text("tag_label", 6, 37, 8, "TAG:"),
                text("tag", 40, 37, 12, test.equipmentTag, mono = true, maxWidth = 112),
                text("report_label", 6, 52, 8, "LAUDO:"),
                text("report", 40, 52, 12, reportNumber(test), mono = true, maxWidth = 112),
                text("test_date_label", 6, 64, 8, "ENSAIO:"),
                text("test_date", 6, 78, 13, testDate, mono = true),
                text("retest_label", 6, 90, 8, "VALIDADE:"),
                text("retest", 6, 104, 13, retest, mono = true),
                qr(160, 26, 76),
                text("validation_code", 157, 64, 10, test.validationCode, mono = true, align = C, rotate = -90, maxWidth = 72),
                text("validator", 198, 110, 7, "VALIDADOR", align = C),
                rect("status_bg", 0, 124, 240, 36, fill = true),
                text("status", 120, 142, 14, status, white = true, align = C, middle = true)
            ))
            LabelPaperSize.SIZE_50X80 -> LabelModel(384, 640, listOf(
                rect("header_bg", 0, 0, 384, 67, fill = true),
                text("title", 192, 31, 24, TITLE, white = true, align = C),
                text("phone", 192, 57, 18, PHONE, white = true, align = C),
                text("norm", 358, 57, 13, "NR-10", mono = true, white = true, align = R),
                text("tag_label", 12, 104, 15, "TAG:"),
                text("tag", 92, 104, 26, test.equipmentTag, mono = true, maxWidth = 280),
                text("report_label", 12, 142, 15, "LAUDO:"),
                text("report", 92, 142, 26, reportNumber(test), mono = true, maxWidth = 280),
                rect("test_date_box", 8, 158, 368, 66, stroke = 2),
                text("test_date_label", 16, 177, 13, "DATA DO ENSAIO:"),
                text("test_date", 16, 213, 30, testDate, mono = true),
                rect("retest_box", 8, 232, 368, 66, stroke = 2),
                text("retest_label", 16, 251, 13, "PRÓXIMO REENSAIO / VALIDADE:"),
                text("retest", 16, 287, 30, retest, mono = true),
                qr(99, 310, 210),
                text("validation_code", 94, 415, 20, test.validationCode, mono = true, align = C, rotate = -90, maxWidth = 202),
                text("validator", 204, 542, 17, "VALIDADOR", align = C),
                rect("status_bg", 0, 558, 384, 82, fill = true),
                text("status", 192, 599, 30, status, white = true, align = C, middle = true)
            ))
            LabelPaperSize.SIZE_50X30 -> LabelModel(384, 240, listOf(
                rect("header_bg", 0, 0, 384, 34, fill = true),
                text("title", 10, 24, 14, TITLE, white = true),
                text("phone", 236, 24, 12, PHONE, white = true, align = C),
                text("norm", 360, 24, 12, "NR-10", mono = true, white = true, align = R),
                text("tag_label", 10, 56, 11, "TAG:"),
                text("tag", 62, 56, 18, test.equipmentTag, mono = true, maxWidth = 160),
                text("report_label", 10, 80, 11, "LAUDO:"),
                text("report", 62, 80, 18, reportNumber(test), mono = true, maxWidth = 160),
                rect("test_date_box", 8, 90, 218, 47, stroke = 2),
                text("test_date_label", 14, 103, 9, "DATA DO ENSAIO:"),
                text("test_date", 14, 128, 19, testDate, mono = true),
                rect("retest_box", 8, 142, 218, 47, stroke = 2),
                text("retest_label", 14, 155, 9, "PRÓXIMO REENSAIO / VALIDADE:"),
                text("retest", 14, 180, 19, retest, mono = true),
                qr(252, 40, 120),
                text("validation_code", 249, 100, 14, test.validationCode, mono = true, align = C, rotate = -90, maxWidth = 112),
                text("validator", 312, 172, 11, "VALIDADOR", align = C),
                rect("status_bg", 0, 196, 384, 44, fill = true),
                text("status", 192, 218, 20, status, white = true, align = C, middle = true)
            ))
        }
    }
}
