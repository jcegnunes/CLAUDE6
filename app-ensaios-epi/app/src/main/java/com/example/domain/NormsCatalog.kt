package com.example.domain

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Critério normativo no mesmo formato da plataforma web (NormCriterion,
 * tabela public.norms → payload). É a base de cálculo dos ensaios.
 */
data class WebNormCriterion(
    val id: String,
    val companyId: String = "",
    val normCode: String,
    val normName: String,
    val editionOrVersion: String = "",
    val applicableEquipmentTypes: List<String>,
    val dielectricClass: String,
    val testVoltage_kV: Double,
    val voltageType: String = "AC",
    val testDurationSeconds: Int,
    val maxLeakageCurrent: Double,
    val currentUnit: String = "mA",
    val defaultRetestMonths: Int = 6,
    val approvalCriterion: String = "visual_and_leakage",
    val notes: String = "",
    val status: String = "active",
    val deleted: Boolean = false
) {
    val isActive: Boolean get() = status == "active" && !deleted

    companion object {
        private fun str(o: JSONObject, k: String, d: String = "") = if (!o.has(k) || o.isNull(k)) d else o.optString(k, d)
        private fun num(o: JSONObject, k: String, d: Double): Double {
            if (!o.has(k) || o.isNull(k)) return d
            val v = o.opt(k)
            return when (v) {
                is Number -> v.toDouble()
                is String -> v.replace(",", ".").toDoubleOrNull() ?: d
                else -> d
            }
        }

        /** Mesma limpeza da web (sanitizeNorm): números válidos e lista de tipos. */
        fun fromJson(o: JSONObject, companyId: String = "", deleted: Boolean = false): WebNormCriterion? {
            val id = str(o, "id")
            if (id.isBlank()) return null
            val types = o.optJSONArray("applicableEquipmentTypes")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.opt(i) as? String }
            } ?: emptyList()
            var dielectricClass = str(o, "dielectricClass")
            var normName = str(o, "normName", str(o, "normCode", "Norma"))
            // Capacete Classe B usa a classe dielétrica 2 (mesma correção da web)
            if (types.contains("capacete_classe_b") && (dielectricClass == "B" || dielectricClass.isBlank())) {
                dielectricClass = "2"
                normName = "Capacete de Segurança Classe B (Classe 2 - Até 20.000V)"
            }
            return WebNormCriterion(
                id = id,
                companyId = companyId.ifBlank { str(o, "companyId") },
                normCode = str(o, "normCode", "Norma"),
                normName = normName,
                editionOrVersion = str(o, "editionOrVersion"),
                applicableEquipmentTypes = types,
                dielectricClass = dielectricClass,
                testVoltage_kV = num(o, "testVoltage_kV", 0.0),
                voltageType = str(o, "voltageType", "AC").ifBlank { "AC" },
                testDurationSeconds = num(o, "testDurationSeconds", 60.0).toInt(),
                maxLeakageCurrent = num(o, "maxLeakageCurrent", 0.0),
                currentUnit = str(o, "currentUnit", "mA").ifBlank { "mA" },
                defaultRetestMonths = num(o, "defaultRetestMonths", 6.0).toInt(),
                approvalCriterion = str(o, "approvalCriterion", "visual_and_leakage"),
                notes = str(o, "notes"),
                status = str(o, "status", "active").ifBlank { "active" },
                deleted = deleted || (o.has("deletedAt") && !o.isNull("deletedAt") && str(o, "deletedAt").isNotBlank())
            )
        }
    }
}

/**
 * Lista de critérios em uso no aparelho — a mesma da plataforma web:
 * versão oficial (company_id nulo) + versões da empresa (substituem a oficial),
 * baixadas da tabela public.norms. Sem internet, usa a última lista baixada;
 * na primeira vez, os critérios oficiais da web embutidos no APK
 * (assets/norms_official.json, gerado de src/data/seedData.ts → INITIAL_NORMS).
 */
object NormsCatalog {

    private const val TAG = "NormsCatalog"
    private const val ASSET = "norms_official.json"
    private const val CACHE = "norms_catalog.json"

    @Volatile
    private var official: List<WebNormCriterion> = emptyList()

    /** Lista completa (inclui as excluídas pela empresa, para não ressuscitá-las). */
    @Volatile
    private var all: List<WebNormCriterion> = emptyList()

    /** Muda a cada carga/download: as telas que leem a lista se atualizam. */
    val revision = androidx.compose.runtime.mutableIntStateOf(0)

    /** Critérios ativos, na mesma ordem da web (a busca devolve o primeiro que serve). */
    val criteria: List<WebNormCriterion> get() = all.filter { it.isActive }

    private fun parseArray(text: String): List<WebNormCriterion> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { WebNormCriterion.fromJson(it) } }
    } catch (e: Exception) {
        Log.w(TAG, "Lista de normas inválida: ${e.message}")
        emptyList()
    }

    private fun toJsonArray(list: List<WebNormCriterion>): JSONArray = JSONArray().apply {
        list.forEach { n ->
            put(JSONObject()
                .put("id", n.id)
                .put("companyId", n.companyId)
                .put("normCode", n.normCode)
                .put("normName", n.normName)
                .put("editionOrVersion", n.editionOrVersion)
                .put("applicableEquipmentTypes", JSONArray(n.applicableEquipmentTypes))
                .put("dielectricClass", n.dielectricClass)
                .put("testVoltage_kV", n.testVoltage_kV)
                .put("voltageType", n.voltageType)
                .put("testDurationSeconds", n.testDurationSeconds)
                .put("maxLeakageCurrent", n.maxLeakageCurrent)
                .put("currentUnit", n.currentUnit)
                .put("defaultRetestMonths", n.defaultRetestMonths)
                .put("approvalCriterion", n.approvalCriterion)
                .put("notes", n.notes)
                .put("status", n.status)
                .put("deletedAt", if (n.deleted) "deleted" else JSONObject.NULL))
        }
    }

    /** Carrega os critérios oficiais embutidos e a última lista baixada da nuvem. */
    fun load(context: Context) {
        official = try {
            parseArray(context.assets.open(ASSET).bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.e(TAG, "Critérios oficiais embutidos não encontrados", e)
            emptyList()
        }
        val cacheFile = File(context.filesDir, CACHE)
        val cached = if (cacheFile.exists()) parseArray(cacheFile.readText()) else emptyList()
        all = withMissingOfficial(cached.ifEmpty { official }).map { upgradeOldPoleNorm(it) }
        revision.intValue++
    }

    /** Norma oficial de bastões/varas (id mantido das versões anteriores da web). */
    const val POLE_NORM_ID = "norm-bastao-100kv"

    /**
     * Mesma migração da web (isOldPoleNorm/poleNormFields): norma de bastão/vara
     * ainda com os valores antigos (100 kV, limite em mA) passa para a ABNT NBR 11854
     * a 50 kV / µA. Norma que a empresa já ajustou de outra forma fica como está.
     */
    private fun upgradeOldPoleNorm(n: WebNormCriterion): WebNormCriterion = upgradeOtherOldNorms(upgradePole(n))

    const val FOOTWEAR_NORM_ID = "norm-calcado-dieletrico"
    const val PROBE_NORM_ID = "norm-ponteira-nbr-6881"
    const val INDUCTION_DETECTOR_NORM_ID = "norm-detector-inducao"

    /**
     * Demais migrações da web (syncEngine.sanitizeNorm):
     *  - calçado ainda com 3,5 mA → 0,5 mA
     *  - ponteiras: IEC 61010-031 a 5 kV ou NBR 6881 a 2,5 kV/1 mA → NBR 6881 a 1,2 kV / 5,0 mA
     *  - detector por indução: tabela de 60 kV → 50 kV
     */
    private fun upgradeOtherOldNorms(n: WebNormCriterion): WebNormCriterion {
        if ("bota_dielétrica" in n.applicableEquipmentTypes && n.maxLeakageCurrent == 3.5 && n.currentUnit.ifBlank { "mA" } == "mA") {
            val seed = official.find { it.id == FOOTWEAR_NORM_ID } ?: return n
            return n.copy(maxLeakageCurrent = seed.maxLeakageCurrent, notes = seed.notes)
        }
        if ("ponteira_prova" in n.applicableEquipmentTypes &&
            ((n.normCode.contains("61010") && n.testVoltage_kV == 5.0) ||
                (n.normCode.contains("6881") && n.testVoltage_kV == 2.5 && n.maxLeakageCurrent == 1.0))
        ) {
            val seed = official.find { it.id == PROBE_NORM_ID } ?: return n
            return n.copy(normCode = seed.normCode, normName = seed.normName, testVoltage_kV = seed.testVoltage_kV,
                maxLeakageCurrent = seed.maxLeakageCurrent, notes = seed.notes)
        }
        if (n.id == INDUCTION_DETECTOR_NORM_ID && n.testVoltage_kV == 60.0) {
            val seed = official.find { it.id == INDUCTION_DETECTOR_NORM_ID } ?: return n
            return n.copy(normName = seed.normName, testVoltage_kV = seed.testVoltage_kV, notes = seed.notes)
        }
        return n
    }

    private fun upgradePole(n: WebNormCriterion): WebNormCriterion {
        val isPole = n.applicableEquipmentTypes.any { it == "bastao_manobra" || it == "vara_manobra" }
        if (!isPole || n.testVoltage_kV != 100.0 || n.currentUnit.ifBlank { "mA" } != "mA") return n
        val seed = official.find { it.id == POLE_NORM_ID } ?: return n
        return n.copy(
            normCode = seed.normCode,
            normName = seed.normName,
            editionOrVersion = seed.editionOrVersion,
            testVoltage_kV = seed.testVoltage_kV,
            voltageType = seed.voltageType,
            testDurationSeconds = seed.testDurationSeconds,
            maxLeakageCurrent = seed.maxLeakageCurrent,
            currentUnit = seed.currentUnit,
            notes = seed.notes
        )
    }

    /** Como a web (getNorms): critérios oficiais que faltarem na lista são acrescentados. */
    private fun withMissingOfficial(list: List<WebNormCriterion>): List<WebNormCriterion> {
        val ids = list.map { it.id }.toMutableSet()
        val out = list.toMutableList()
        official.forEach { if (ids.add(it.id)) out.add(it) }
        return out
    }

    /**
     * Aplica as linhas da tabela norms (oficiais + da empresa, incluindo as
     * excluídas) com a mesma regra da web: a versão da empresa (id
     * "<empresa>::<norma>", norm_id = id no app) prevalece sobre a oficial.
     */
    fun applyRemoteRows(context: Context, rows: List<JSONObject>, companyId: String): Int {
        val officialRows = mutableMapOf<String, WebNormCriterion>()
        val companyRows = mutableMapOf<String, WebNormCriterion>()
        for (row in rows) {
            val rowCompany = if (row.isNull("company_id")) "" else row.optString("company_id", "")
            if (rowCompany.isNotBlank() && rowCompany != companyId) continue
            val payload = row.optJSONObject("payload") ?: continue
            val normId = (if (row.isNull("norm_id")) "" else row.optString("norm_id", "")).ifBlank { payload.optString("id", row.optString("id")) }
            payload.put("id", normId)
            val deleted = !row.isNull("deleted_at") && row.optString("deleted_at").isNotBlank()
            val norm = WebNormCriterion.fromJson(payload, rowCompany, deleted) ?: continue
            if (rowCompany.isBlank()) officialRows[normId] = norm else companyRows[normId] = norm
        }
        if (officialRows.isEmpty() && companyRows.isEmpty()) return 0
        // Ordem: a da lista oficial embutida (igual à web); depois as novas
        val merged = linkedMapOf<String, WebNormCriterion>()
        official.forEach { o -> merged[o.id] = companyRows[o.id] ?: officialRows[o.id] ?: o }
        officialRows.forEach { (id, n) -> if (!merged.containsKey(id)) merged[id] = companyRows[id] ?: n }
        companyRows.forEach { (id, n) -> if (!merged.containsKey(id)) merged[id] = n }
        all = merged.values.map { upgradeOldPoleNorm(it) }
        revision.intValue++
        try {
            File(context.filesDir, CACHE).writeText(toJsonArray(all).toString())
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível guardar as normas: ${e.message}")
        }
        return officialRows.size + companyRows.size
    }
}
