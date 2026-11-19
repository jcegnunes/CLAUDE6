package com.example.util

import com.example.data.model.AppSettingEntity
import org.json.JSONObject

/**
 * Configurações da empresa no mesmo formato da plataforma web (CompanyLabInfo,
 * gravado em companies.lab_info). O APK guarda o JSON em AppSettingEntity.labInfoJson
 * e espelha os campos que já existiam nas suas telas (nome, CNPJ, RT, validade...).
 */
object CompanyLabInfo {

    /** Marca local de "alterado no aparelho e ainda não enviado" (nunca vai para a nuvem). */
    const val PENDING_KEY = "_apkPendingSync"

    /** Chaves da conexão de cada aparelho: nunca vêm nem vão para a nuvem. */
    private val deviceOnlyKeys = listOf("supabaseAnonKey", "supabaseUrl", "supabaseEnabled", "supabaseAutoSync", "lastSupabaseSyncTime")

    const val DEFAULT_APPROVAL_TEXT = "Equipamento APROVADO nos ensaios de rigidez dielétrica e corrente de fuga, atendendo integralmente aos requisitos normativos da NR-10 e normas aplicáveis (NBR / ASTM / IEC). Apto para operação em instalações elétricas."
    const val DEFAULT_REJECTION_TEXT = "Equipamento REPROVADO nos ensaios dielétricos por não atender aos limites normativos (perfuração por disrupção e/ou corrente de fuga excessiva). INAPTO para uso em intervenções elétricas, devendo ser descaracterizado e descartado."
    const val DEFAULT_OBSERVATION = "Os resultados são válidos exclusivamente para o item identificado sob as condições de ensaio descritas. A periodicidade de reensaio recomendada pela NR-10 e NBR 10622 é de no máximo 6 meses."
    const val DEFAULT_CERT_TITLE = "CERTIFICADO DE CONFORMIDADE DIELÉTRICA DE EPI/EPC"

    fun parse(json: String?): JSONObject = try {
        JSONObject(json?.ifBlank { "{}" } ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    fun isPending(settings: AppSettingEntity?): Boolean = parse(settings?.labInfoJson).optBoolean(PENDING_KEY, false)

    fun str(obj: JSONObject?, key: String, default: String = ""): String =
        if (obj == null || !obj.has(key) || obj.isNull(key)) default else obj.optString(key, default)

    fun technical(lab: JSONObject): JSONObject = lab.optJSONObject("technicalResponsible") ?: JSONObject()

    fun emission(lab: JSONObject): JSONObject = lab.optJSONObject("certificateEmissionSettings") ?: JSONObject()

    /** Configurações completas: JSON salvo + campos que o APK mantém em colunas próprias. */
    fun fromSettings(s: AppSettingEntity): JSONObject {
        val lab = parse(s.labInfoJson)
        lab.put("name", s.name)
        lab.put("legalName", s.legalName)
        lab.put("cnpj", s.cnpj)
        lab.put("creaCompanyRegister", s.creaCompanyRegister)
        lab.put("phone", s.phone)
        lab.put("email", s.email)
        lab.put("website", s.website)
        if (!lab.has("address")) lab.put("address", s.address)
        val tech = technical(lab)
        tech.put("name", s.technicalResponsibleName)
        tech.put("creaNumber", s.technicalResponsibleCrea)
        if (!tech.has("title")) tech.put("title", "Responsável Técnico")
        if (!tech.has("rnp")) tech.put("rnp", "")
        lab.put("technicalResponsible", tech)
        val em = emission(lab)
        em.put("defaultValidityMonths", s.defaultRetestMonths)
        if (!em.has("defaultEmissionMode")) em.put("defaultEmissionMode", "laudo_e_certificado")
        if (!em.has("reportPrefix")) em.put("reportPrefix", "LAU-")
        if (!em.has("certificatePrefix")) em.put("certificatePrefix", "CERT-")
        if (!em.has("defaultApprovalText")) em.put("defaultApprovalText", DEFAULT_APPROVAL_TEXT)
        if (!em.has("defaultRejectionText")) em.put("defaultRejectionText", DEFAULT_REJECTION_TEXT)
        if (!em.has("requireDigitalSignature")) em.put("requireDigitalSignature", true)
        if (!em.has("enableQrCodeValidation")) em.put("enableQrCodeValidation", true)
        if (!em.has("standardObservationNote")) em.put("standardObservationNote", DEFAULT_OBSERVATION)
        if (!em.has("headerCustomTitle")) em.put("headerCustomTitle", DEFAULT_CERT_TITLE)
        lab.put("certificateEmissionSettings", em)
        if (!lab.has("validationBaseUrl")) lab.put("validationBaseUrl", ValidationPortal.DEFAULT_BASE_URL)
        return lab
    }

    /** Título do certificado configurado em Parâmetros de Emissão. */
    fun certificateTitle(s: AppSettingEntity): String =
        str(emission(parse(s.labInfoJson)), "headerCustomTitle").trim().ifBlank { DEFAULT_CERT_TITLE }

    /** Endereço completo (usado no cabeçalho dos documentos do APK). */
    fun fullAddress(lab: JSONObject): String {
        val street = listOf(str(lab, "address"), str(lab, "number")).filter { it.isNotBlank() }.joinToString(", ")
        val cityUf = listOf(str(lab, "city"), str(lab, "state")).filter { it.isNotBlank() }.joinToString("/")
        val cep = str(lab, "cep").let { if (it.isNotBlank()) "CEP $it" else "" }
        return listOf(street, str(lab, "complement"), str(lab, "neighborhood"), cityUf, cep)
            .filter { it.isNotBlank() }.joinToString(" - ")
    }

    /** Aplica o lab_info (da nuvem ou editado) às configurações do APK. */
    fun applyToSettings(s: AppSettingEntity, lab: JSONObject, pending: Boolean): AppSettingEntity {
        val clean = JSONObject(lab.toString())
        deviceOnlyKeys.forEach { clean.remove(it) }
        if (pending) clean.put(PENDING_KEY, true) else clean.remove(PENDING_KEY)
        val tech = technical(clean)
        val em = emission(clean)
        ValidationPortal.baseUrl = str(clean, "validationBaseUrl")
        return s.copy(
            name = str(clean, "name", s.name),
            legalName = str(clean, "legalName", s.legalName),
            cnpj = str(clean, "cnpj", s.cnpj),
            creaCompanyRegister = str(clean, "creaCompanyRegister", s.creaCompanyRegister),
            address = fullAddress(clean).ifBlank { s.address },
            phone = str(clean, "phone", s.phone),
            email = str(clean, "email", s.email),
            website = str(clean, "website", s.website),
            technicalResponsibleName = str(tech, "name", s.technicalResponsibleName),
            technicalResponsibleCrea = str(tech, "creaNumber", s.technicalResponsibleCrea),
            defaultRetestMonths = em.optInt("defaultValidityMonths", s.defaultRetestMonths).takeIf { it > 0 } ?: s.defaultRetestMonths,
            labInfoJson = clean.toString()
        )
    }

    /** JSON enviado para companies.lab_info (sem a marca local nem dados do aparelho). */
    fun forCloud(lab: JSONObject): JSONObject {
        val out = JSONObject(lab.toString())
        out.remove(PENDING_KEY)
        deviceOnlyKeys.forEach { out.remove(it) }
        // a web compõe "Cidade/UF" para documentos antigos
        val cityUf = listOf(str(out, "city"), str(out, "state")).filter { it.isNotBlank() }.joinToString("/")
        if (cityUf.isNotBlank()) out.put("cityState", cityUf)
        return out
    }
}
