package com.example.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CnpjCompanyData(
    val cnpjFormatted: String,
    val razaoSocial: String,
    val nomeFantasia: String,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String,
    val address: String,
    val city: String,
    val state: String,
    val source: String
)

object CnpjLookupService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun cleanDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    fun formatCnpj(input: String): String {
        val digits = cleanDigits(input).take(14)
        val sb = StringBuilder()
        for (i in digits.indices) {
            sb.append(digits[i])
            when (i) {
                1 -> if (digits.length > 2) sb.append(".")
                4 -> if (digits.length > 5) sb.append(".")
                7 -> if (digits.length > 8) sb.append("/")
                11 -> if (digits.length > 12) sb.append("-")
            }
        }
        return sb.toString()
    }

    suspend fun lookupCnpj(rawCnpj: String): Result<CnpjCompanyData> = withContext(Dispatchers.IO) {
        val digits = cleanDigits(rawCnpj)
        if (digits.length != 14) {
            return@withContext Result.failure(IllegalArgumentException("CNPJ deve conter exatamente 14 dígitos numéricos."))
        }

        // 1. Try BrasilAPI first
        try {
            val result = fetchFromBrasilApi(digits)
            if (result != null) {
                return@withContext Result.success(result)
            }
        } catch (_: Exception) {
            // fallback to ReceitaWS
        }

        // 2. Try ReceitaWS fallback
        try {
            val result = fetchFromReceitaWs(digits)
            if (result != null) {
                return@withContext Result.success(result)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Não foi possível consultar os dados do CNPJ: ${e.localizedMessage}"))
        }

        Result.failure(Exception("Nenhuma informação encontrada para o CNPJ informado."))
    }

    private fun fetchFromBrasilApi(digits: String): CnpjCompanyData? {
        val url = "https://brasilapi.com.br/api/cnpj/v1/$digits"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JVM-Ensaios-App")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val razaoSocial = json.optString("razao_social", "").trim()
            val nomeFantasiaRaw = json.optString("nome_fantasia", "").trim()
            val nomeFantasia = if (nomeFantasiaRaw.isNotEmpty()) nomeFantasiaRaw else razaoSocial

            val logradouro = json.optString("logradouro", "").trim()
            val numero = json.optString("numero", "").trim()
            val complemento = json.optString("complemento", "").trim()
            val bairro = json.optString("bairro", "").trim()
            val cep = json.optString("cep", "").trim()
            val municipio = json.optString("municipio", "").trim()
            val uf = json.optString("uf", "").trim()

            val dddTelefone1 = json.optString("ddd_telefone_1", "").trim()
            val email = json.optString("email", "").trim().lowercase()

            // Construct readable address
            val addressParts = mutableListOf<String>()
            if (logradouro.isNotEmpty()) {
                val streetAndNum = if (numero.isNotEmpty() && numero != "S/N") "$logradouro, $numero" else logradouro
                addressParts.add(streetAndNum)
            }
            if (complemento.isNotEmpty()) addressParts.add(complemento)
            if (bairro.isNotEmpty()) addressParts.add(bairro)
            if (cep.isNotEmpty()) addressParts.add("CEP: $cep")

            val fullAddress = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else "$municipio - $uf"

            // Format phone: if like "1199999999" -> "(11) 9999-9999"
            val formattedPhone = formatPhone(dddTelefone1)

            // Get QSA / socio for contact name if available
            var contactName = ""
            val qsaArray = json.optJSONArray("qsa")
            if (qsaArray != null && qsaArray.length() > 0) {
                val firstSocio = qsaArray.optJSONObject(0)
                contactName = firstSocio?.optString("nome_socio", "")?.trim() ?: ""
            }

            return CnpjCompanyData(
                cnpjFormatted = formatCnpj(digits),
                razaoSocial = razaoSocial,
                nomeFantasia = nomeFantasia,
                contactName = contactName,
                contactEmail = email,
                contactPhone = formattedPhone,
                address = fullAddress,
                city = municipio,
                state = uf,
                source = "Receita Federal (BrasilAPI)"
            )
        }
    }

    private fun fetchFromReceitaWs(digits: String): CnpjCompanyData? {
        val url = "https://receitaws.com.br/v1/cnpj/$digits"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JVM-Ensaios-App")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optString("status") == "ERROR") return null

            val razaoSocial = json.optString("nome", "").trim()
            val nomeFantasiaRaw = json.optString("fantasia", "").trim()
            val nomeFantasia = if (nomeFantasiaRaw.isNotEmpty()) nomeFantasiaRaw else razaoSocial

            val logradouro = json.optString("logradouro", "").trim()
            val numero = json.optString("numero", "").trim()
            val complemento = json.optString("complemento", "").trim()
            val bairro = json.optString("bairro", "").trim()
            val cep = json.optString("cep", "").trim()
            val municipio = json.optString("municipio", "").trim()
            val uf = json.optString("uf", "").trim()

            val telefone = json.optString("telefone", "").trim()
            val email = json.optString("email", "").trim().lowercase()

            val addressParts = mutableListOf<String>()
            if (logradouro.isNotEmpty()) {
                val streetAndNum = if (numero.isNotEmpty() && numero != "S/N") "$logradouro, $numero" else logradouro
                addressParts.add(streetAndNum)
            }
            if (complemento.isNotEmpty()) addressParts.add(complemento)
            if (bairro.isNotEmpty()) addressParts.add(bairro)
            if (cep.isNotEmpty()) addressParts.add("CEP: $cep")

            val fullAddress = if (addressParts.isNotEmpty()) addressParts.joinToString(", ") else "$municipio - $uf"

            var contactName = ""
            val qsaArray = json.optJSONArray("qsa")
            if (qsaArray != null && qsaArray.length() > 0) {
                val firstSocio = qsaArray.optJSONObject(0)
                contactName = firstSocio?.optString("nome", "")?.trim() ?: ""
            }

            return CnpjCompanyData(
                cnpjFormatted = formatCnpj(digits),
                razaoSocial = razaoSocial,
                nomeFantasia = nomeFantasia,
                contactName = contactName,
                contactEmail = email,
                contactPhone = telefone,
                address = fullAddress,
                city = municipio,
                state = uf,
                source = "ReceitaWS"
            )
        }
    }

    private fun formatPhone(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when (d.length) {
            10 -> "(${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
            11 -> "(${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
            else -> raw
        }
    }
}
