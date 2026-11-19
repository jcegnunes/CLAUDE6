package com.example.util

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Endereço impresso no QR Code dos documentos — mesma regra da plataforma web
 * (src/config/validationPortalConfig.ts). A URL vem das Configurações
 * ("Portal Público de Validação", campo validationBaseUrl da empresa) e é
 * sincronizada com a web. Aceita dois formatos:
 *  - modelo com {codigo}: https://www.jvmengenharia.com.br/validar?codigo={codigo}
 *  - endereço base: https://validador.jvmlab.com.br  (vira .../validar/CODIGO)
 */
object ValidationPortal {

    const val DEFAULT_BASE_URL = "https://www.jvmengenharia.com.br/validar?codigo={codigo}"
    private const val CODE_PLACEHOLDER = "{codigo}"

    /** Endereços antigos (desativados): trocados automaticamente pelo atual. */
    private val legacyDomains = listOf("mediumturquoise-giraffe-910043", "mediumvioletred-bison-595566")
    private val legacyExact = listOf("https://jvmlab.com.br", "https://www.jvmlab.com.br")

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) { field = normalize(value) }

    fun normalize(raw: String?): String {
        val trimmed = (raw ?: "").trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.lowercase() in legacyExact || legacyDomains.any { trimmed.contains(it) }) {
            return DEFAULT_BASE_URL
        }
        return trimmed
    }

    /** Link de validação de um documento (o que vai no QR Code). */
    fun url(code: String, base: String? = null): String {
        val b = normalize(base ?: baseUrl)
        val enc = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
        return if (b.contains(CODE_PLACEHOLDER)) b.replace(CODE_PLACEHOLDER, enc) else "$b/validar/$enc"
    }

    /** Código de validação contido num link lido do QR Code (ou null). */
    fun extractCode(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        Regex("/validar/([^/?#\\s]+)", RegexOption.IGNORE_CASE).find(clean)?.let {
            return URLDecoder.decode(it.groupValues[1], "UTF-8")
        }
        Regex("[?&]codigo=([^&#\\s]+)", RegexOption.IGNORE_CASE).find(clean)?.let {
            return URLDecoder.decode(it.groupValues[1], "UTF-8")
        }
        return null
    }
}
