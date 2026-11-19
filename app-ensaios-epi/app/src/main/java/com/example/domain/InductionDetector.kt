package com.example.domain

import com.example.data.model.TestResult
import org.json.JSONArray
import org.json.JSONObject

/** Ponto do ensaio do detector por indução (mesmo formato da web: inductionPoints). */
data class InductionPoint(
    val voltage_kV: Double,
    val distance_cm: Double? = null,
    val detected: Boolean = true
)

/**
 * Detector de tensão por indução — mesma regra da plataforma web
 * (src/services/inductionDetectorService.ts): em cada tensão da tabela registra-se
 * a distância (cm) em que o detector sinaliza; aprovado quando sinaliza em todas.
 */
object InductionDetector {

    const val DETECTOR_CLASS = "1 kV a 50 kV"
    val VOLTAGES_KV = listOf(1.0, 2.0, 5.0, 8.0, 13.8, 19.0, 24.0, 34.5, 43.0, 50.0)

    data class Evaluation(
        val result: TestResult,
        val rationale: String,
        val missing: List<Double>,
        val notDetected: List<Double>,
        val warnings: List<String>
    )

    fun fmtBr1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v).replace(".", ",")

    /** Tabela completa na ordem padrão (ou a tabela salva, se usar outras tensões). */
    fun normalize(points: List<InductionPoint>?): List<InductionPoint> {
        val byVoltage = (points ?: emptyList()).associateBy { it.voltage_kV }
        val saved = byVoltage.keys.filter { it.isFinite() && it > 0 }
        val usesOtherTable = saved.any { it !in VOLTAGES_KV }
        val voltages = if (usesOtherTable) saved.sorted() else VOLTAGES_KV
        return voltages.map { v ->
            val p = byVoltage[v]
            val d = p?.distance_cm
            InductionPoint(v, if (d != null && d.isFinite() && d > 0) d else null, p?.detected != false)
        }
    }

    fun evaluate(points: List<InductionPoint>?, visualFailedItems: List<String> = emptyList()): Evaluation {
        val list = normalize(points)
        val notDetected = list.filter { !it.detected }.map { it.voltage_kV }
        val missing = list.filter { it.detected && it.distance_cm == null }.map { it.voltage_kV }
        val filled = list.filter { it.detected && it.distance_cm != null }
        val warnings = mutableListOf<String>()
        for (i in 1 until filled.size) {
            if ((filled[i].distance_cm ?: 0.0) < (filled[i - 1].distance_cm ?: 0.0)) {
                warnings.add("Distância em ${fmtBr1(filled[i].voltage_kV)} kV (${fmtBr1(filled[i].distance_cm!!)} cm) menor que em ${fmtBr1(filled[i - 1].voltage_kV)} kV (${fmtBr1(filled[i - 1].distance_cm!!)} cm): confira a medição.")
            }
        }
        val kvList = { vs: List<Double> -> vs.joinToString(", ") { "${fmtBr1(it)} kV" } }
        val parts = mutableListOf<String>()
        var result = TestResult.APROVADO
        if (visualFailedItems.isNotEmpty()) {
            result = TestResult.REPROVADO
            parts.add("Reprovado na inspeção visual: ${visualFailedItems.joinToString("; ")}.")
        }
        if (notDetected.isNotEmpty()) {
            result = TestResult.REPROVADO
            parts.add("O detector NÃO sinalizou a presença de tensão em ${kvList(notDetected)}: não atende às especificações do fabricante para a classe $DETECTOR_CLASS.")
        }
        if (result == TestResult.APROVADO && missing.isNotEmpty()) {
            return Evaluation(TestResult.PENDENTE, "Preencha a distância de detecção obtida (cm) em: ${kvList(missing)}.", missing, notDetected, warnings)
        }
        if (result == TestResult.APROVADO) {
            val ds = filled.map { it.distance_cm ?: 0.0 }
            parts.add(
                "Detector de tensão por indução (classe $DETECTOR_CLASS) CONFORME: sinalizou a presença de tensão nas ${list.size} tensões ensaiadas (${fmtBr1(list.first().voltage_kV)} a ${fmtBr1(list.last().voltage_kV)} kV), nas distâncias obtidas de ${fmtBr1(ds.minOrNull() ?: 0.0)} a ${fmtBr1(ds.maxOrNull() ?: 0.0)} cm. Resultados satisfatórios conforme especificações do fabricante."
            )
        }
        if (warnings.isNotEmpty()) parts.add("Observação: ${warnings.joinToString(" ")}")
        return Evaluation(result, parts.joinToString("\n\n"), missing, notDetected, warnings)
    }

    fun parse(json: String?): List<InductionPoint> = try {
        val arr = JSONArray(json?.ifBlank { "[]" } ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val v = o.optDouble("voltage_kV", Double.NaN)
            if (v.isNaN()) null else InductionPoint(
                voltage_kV = v,
                distance_cm = o.optDouble("distance_cm", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                detected = o.optBoolean("detected", true)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun toJsonArray(points: List<InductionPoint>): JSONArray = JSONArray().apply {
        points.forEach { p ->
            put(JSONObject().put("voltage_kV", p.voltage_kV).put("detected", p.detected).apply {
                p.distance_cm?.let { put("distance_cm", it) }
            })
        }
    }

    fun toJson(points: List<InductionPoint>): String = toJsonArray(points).toString()
}
