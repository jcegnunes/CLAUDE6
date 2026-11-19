package com.example.data.service

import android.util.Log
import com.example.data.model.ClientEntity
import com.example.data.model.CompanyEntity
import com.example.data.model.EquipmentEntity
import com.example.data.model.LabInstrumentEntity
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.SyncStatus
import com.example.data.model.TestRecordEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SupabaseConnectionStatus(
    val isConnected: Boolean,
    val message: String,
    val latencyMs: Long = 0,
    val remoteCompaniesCount: Int = 0,
    val remoteUsersCount: Int = 0,
    val remoteClientsCount: Int = 0,
    val remoteEquipmentCount: Int = 0,
    val remoteOrdersCount: Int = 0,
    val remoteTestsCount: Int = 0,
    val remoteInstrumentsCount: Int = 0
)

data class SupabaseAuthResult(
    val success: Boolean,
    val token: String? = null,
    val refreshToken: String? = null,
    val user: UserEntity? = null,
    val company: CompanyEntity? = null,
    val message: String? = null,
    /** true quando o servidor não respondeu (sem internet): permite o login offline */
    val networkError: Boolean = false
)

data class SupabaseSyncResult(
    val success: Boolean,
    val message: String,
    val companiesPulled: Int = 0,
    val companiesPushed: Int = 0,
    val usersPulled: Int = 0,
    val usersPushed: Int = 0,
    val clientsPulled: Int = 0,
    val clientsPushed: Int = 0,
    val equipmentPulled: Int = 0,
    val equipmentPushed: Int = 0,
    val ordersPulled: Int = 0,
    val ordersPushed: Int = 0,
    val testsPulled: Int = 0,
    val testsPushed: Int = 0,
    val instrumentsPulled: Int = 0,
    val instrumentsPushed: Int = 0,
    val errors: List<String> = emptyList()
)

object SupabaseService {

    private const val TAG = "SupabaseService"
    const val DEFAULT_URL = "https://cdtbzbshylrcprvmjpgc.supabase.co/rest/v1/"
    const val DEFAULT_ANON_KEY = "sb_publishable_j3sUJcAb-zBEQI_S09u2Cg_X1-WJ-8M"

    var currentAuthToken: String? = null
    var currentRefreshToken: String? = null
    var currentUserProfile: UserEntity? = null
    var currentCompanyProfile: CompanyEntity? = null

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Token de acesso renovado: o app salva no aparelho (o Supabase troca o refresh token a cada uso). */
    @Volatile var onTokensRefreshed: ((accessToken: String, refreshToken: String) -> Unit)? = null

    /** A sessão não pôde ser renovada (refresh token inválido): é preciso entrar de novo. */
    @Volatile var onSessionExpired: (() -> Unit)? = null

    private val refreshLock = Any()

    /** Cliente sem o interceptador, só para a chamada de renovação (evita recursão). */
    private val plainClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Renova a sessão em todas as chamadas que usam o token do usuário: antes, se o token vence em
     * menos de 60 s; depois, se o servidor responder 401 ("JWT expired"), repetindo a chamada uma vez.
     */
    private val authRefreshInterceptor = Interceptor { chain ->
        var request = chain.request()
        val apiKey = request.header("apikey")?.trim()
        val bearer = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val usesUserToken = !apiKey.isNullOrBlank() && !bearer.isNullOrBlank() && bearer != apiKey &&
            !request.url.encodedPath.contains("/auth/v1/")
        if (usesUserToken && jwtExpiresWithin(bearer!!, 60)) {
            refreshSessionBlocking(request.url, apiKey!!, bearer)?.let {
                request = request.newBuilder().header("Authorization", "Bearer $it").build()
            }
        }
        var response = chain.proceed(request)
        if (usesUserToken && response.code == 401) {
            val used = request.header("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
            val newToken = refreshSessionBlocking(request.url, apiKey!!, used)
            if (newToken != null) {
                response.close()
                response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $newToken").build())
            }
        }
        response
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(authRefreshInterceptor)
        .build()

    /** true se o JWT vence em menos de [seconds] (ou não dá para ler a validade). */
    private fun jwtExpiresWithin(token: String, seconds: Long): Boolean = try {
        val payload = token.split(".").getOrNull(1) ?: return false
        val json = JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)))
        val exp = json.optLong("exp", 0L)
        exp > 0 && exp - System.currentTimeMillis() / 1000 < seconds
    } catch (_: Exception) {
        false
    }

    /**
     * Troca o refresh token por um novo token de acesso (uma renovação por vez; se outra chamada já
     * renovou, reaproveita). Devolve o token novo ou null se a sessão não puder ser renovada.
     */
    private fun refreshSessionBlocking(url: HttpUrl, apiKey: String, usedToken: String): String? = synchronized(refreshLock) {
        val current = currentAuthToken
        if (!current.isNullOrBlank() && current != usedToken && !jwtExpiresWithin(current, 60)) return current
        val rToken = currentRefreshToken
        if (rToken.isNullOrBlank()) return null
        val req = Request.Builder()
            .url("${url.scheme}://${url.host}/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(JSONObject().put("refresh_token", rToken).toString().toRequestBody(jsonMediaType))
            .build()
        try {
            plainClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val newToken = json.optString("access_token", "")
                    val newRefresh = json.optString("refresh_token", rToken)
                    if (newToken.isNotBlank()) {
                        currentAuthToken = newToken
                        currentRefreshToken = newRefresh
                        onTokensRefreshed?.invoke(newToken, newRefresh)
                        return newToken
                    }
                } else if (response.code == 400 || response.code == 401) {
                    Log.w(TAG, "Sessão não renovada: HTTP ${response.code}")
                    onSessionExpired?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao renovar sessão: ${e.message}")
        }
        null
    }

    fun normalizeUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun getAuthUrl(baseUrl: String): String {
        val norm = normalizeUrl(baseUrl)
        return if (norm.contains("/rest/v1/")) {
            norm.replace("/rest/v1/", "/auth/v1/")
        } else {
            val uri = java.net.URI(norm)
            "${uri.scheme}://${uri.host}/auth/v1/"
        }
    }

    private fun buildHeaders(anonKey: String, authToken: String? = currentAuthToken, isUpsert: Boolean = false): Headers {
        val builder = Headers.Builder()
            .add("apikey", anonKey.trim())
            .add("Authorization", "Bearer ${authToken?.ifBlank { anonKey.trim() } ?: anonKey.trim()}")
            .add("Accept", "application/json")
        if (isUpsert) {
            builder.add("Content-Type", "application/json")
            builder.add("Prefer", "resolution=merge-duplicates,return=representation")
        }
        return builder.build()
    }

    suspend fun resolveLoginEmail(baseUrl: String, anonKey: String, identifier: String): String = withContext(Dispatchers.IO) {
        val trimmed = identifier.trim()
        if (trimmed.contains("@")) return@withContext trimmed.lowercase()

        try {
            val root = normalizeUrl(baseUrl)
            val req = Request.Builder()
                .url("${root}rpc/jvm_login_email")
                .headers(buildHeaders(anonKey, authToken = null))
                .post(JSONObject().put("p_login", trimmed).toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string()?.trim() ?: ""
                    val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) {
                        raw.substring(1, raw.length - 1)
                    } else raw
                    if (unquoted.isNotBlank() && unquoted != "null" && unquoted.contains("@")) {
                        return@withContext unquoted.lowercase()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving login email for '$trimmed': ${e.message}")
        }
        trimmed
    }

    suspend fun signInWithSupabaseAuth(
        baseUrl: String,
        anonKey: String,
        identifier: String,
        password: String
    ): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val email = resolveLoginEmail(baseUrl, anonKey, identifier)
        val authUrl = getAuthUrl(baseUrl)
        val bodyObj = JSONObject()
        bodyObj.put("email", email)
        bodyObj.put("password", password)

        val request = Request.Builder()
            .url("${authUrl}token?grant_type=password")
            .addHeader("apikey", anonKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errObj = JSONObject(respBody)
                        errObj.optString("error_description", errObj.optString("msg", "Usuário ou senha incorretos no banco de dados."))
                    } catch (_: Exception) { "Falha na autenticação (HTTP ${response.code})" }
                    return@withContext SupabaseAuthResult(success = false, message = errorMsg)
                }

                val json = JSONObject(respBody)
                val token = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", "")
                currentAuthToken = token
                currentRefreshToken = refreshToken

                // Busca dados completos do usuário autenticado via RPC jvm_me
                val (user, company) = fetchMeProfile(baseUrl, anonKey, token)
                // Conta de login sem cadastro ativo em public.users (inativa, excluída
                // ou não vinculada): não entra (antes o app inventava um perfil)
                if (user == null) {
                    currentAuthToken = null
                    currentRefreshToken = null
                    return@withContext SupabaseAuthResult(
                        success = false,
                        message = "Usuário sem cadastro ativo na plataforma. Contate o administrador."
                    )
                }
                val resolvedUser = user

                currentUserProfile = resolvedUser
                currentCompanyProfile = company ?: CompanyEntity(id = resolvedUser.companyId, name = resolvedUser.companyName)

                SupabaseAuthResult(
                    success = true,
                    token = token,
                    refreshToken = refreshToken,
                    user = resolvedUser,
                    company = company
                )
            }
        } catch (e: Exception) {
            SupabaseAuthResult(success = false, networkError = true, message = "Erro ao conectar ao banco de dados: ${e.message}")
        }
    }

    fun isServiceRole(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.contains("service_role") || (trimmed.startsWith("eyJ") && trimmed.length > 150)
    }

    suspend fun refreshAuthToken(baseUrl: String, anonKey: String): Boolean = withContext(Dispatchers.IO) {
        val url = getAuthUrl(baseUrl).toHttpUrlOrNull() ?: return@withContext false
        // mesma rotina do interceptador: renova uma vez só e avisa o app para salvar o token novo
        refreshSessionBlocking(url, anonKey.trim(), currentAuthToken.orEmpty()) != null
    }

    suspend fun executeWithAuthRetry(
        baseUrl: String,
        anonKey: String,
        requestBuilder: () -> Request
    ): okhttp3.Response = withContext(Dispatchers.IO) {
        val initialReq = requestBuilder()
        val resp = httpClient.newCall(initialReq).execute()
        if (resp.code == 401 && !currentRefreshToken.isNullOrBlank()) {
            resp.close()
            val refreshed = refreshAuthToken(baseUrl, anonKey)
            if (refreshed) {
                val newReq = requestBuilder()
                return@withContext httpClient.newCall(newReq).execute()
            }
            return@withContext httpClient.newCall(initialReq).execute()
        }
        resp
    }

    suspend fun fetchMeProfile(baseUrl: String, anonKey: String, authToken: String): Pair<UserEntity?, CompanyEntity?> = withContext(Dispatchers.IO) {
        val root = normalizeUrl(baseUrl)
        val req = Request.Builder()
            .url("${root}rpc/jvm_me")
            .headers(buildHeaders(anonKey, authToken))
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    if (json.optBoolean("ok", false) && json.has("user")) {
                        val userObj = json.getJSONObject("user")
                        val user = jsonToUser(userObj)
                        val companyId = user.companyId
                        var company: CompanyEntity? = null
                        if (json.has("company")) {
                            try {
                                company = jsonToCompany(json.getJSONObject("company"))
                            } catch (_: Exception) {}
                        }
                        if (company == null && companyId.isNotBlank()) {
                            try {
                                val compReq = Request.Builder()
                                    .url("${root}companies?id=eq.$companyId&select=*")
                                    .headers(buildHeaders(anonKey, authToken))
                                    .get()
                                    .build()
                                httpClient.newCall(compReq).execute().use { compResp ->
                                    if (compResp.isSuccessful) {
                                        val compArr = JSONArray(compResp.body?.string() ?: "[]")
                                        if (compArr.length() > 0) {
                                            company = jsonToCompany(compArr.getJSONObject(0))
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        return@withContext Pair(user, company)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching jvm_me profile: ${e.message}")
        }
        Pair(null, null)
    }

    suspend fun validateCertificateViaRpc(baseUrl: String, anonKey: String, code: String): Pair<TestRecordEntity?, CompanyEntity?>? = withContext(Dispatchers.IO) {
        val root = normalizeUrl(baseUrl)
        val bodyObj = JSONObject().put("p_code", code.trim().uppercase())
        val req = Request.Builder()
            .url("${root}rpc/jvm_validar_certificado")
            .headers(buildHeaders(anonKey, authToken = null))
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isBlank() || body == "null") return@withContext null
                    val json = JSONObject(body)
                    val testJson = json.optJSONObject("test")
                    val compJson = json.optJSONObject("company")
                    val test = if (testJson != null) publicPayloadToTestRecord(testJson) else null
                    val comp = if (compJson != null) jsonToCompany(compJson) else null
                    return@withContext Pair(test, comp)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "validateCertificateViaRpc error: ${e.message}")
        }
        null
    }

    suspend fun testConnection(baseUrl: String, anonKey: String): SupabaseConnectionStatus = withContext(Dispatchers.IO) {
        val root = normalizeUrl(baseUrl)
        val key = anonKey.trim()
        if (root.isBlank() || key.isBlank()) {
            return@withContext SupabaseConnectionStatus(
                isConnected = false,
                message = "URL ou chave anônima do Supabase não configurados."
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            // Testa conectividade real executando a função pública jvm_login_email (disponível para anon)
            val pingReq = Request.Builder()
                .url("${root}rpc/jvm_login_email")
                .headers(buildHeaders(key, authToken = null))
                .post(JSONObject().put("p_login", "ping").toString().toRequestBody(jsonMediaType))
                .build()

            var isPingOk = false
            var latency: Long = 0
            httpClient.newCall(pingReq).execute().use { response ->
                latency = System.currentTimeMillis() - startTime
                isPingOk = response.isSuccessful
            }

            if (!isPingOk) {
                return@withContext SupabaseConnectionStatus(
                    isConnected = false,
                    message = "Servidor Supabase indisponível ou credenciais inválidas.",
                    latencyMs = latency
                )
            }

            var compCount = 0
            var usersCount = 0
            var clientsCount = 0
            var eqCount = 0
            var ordersCount = 0
            var testsCount = 0
            var instrumentsCount = 0

            // Se o usuário já estiver autenticado no app ou estiver usando service_role, conta os registros da empresa dele
            val isSvc = isServiceRole(key)
            val token = currentAuthToken
            val hasAuth = !token.isNullOrBlank()
            if (hasAuth || isSvc) {
                val headers = buildHeaders(key, if (hasAuth) token else key)
                try {
                    val compReq = Request.Builder().url("${root}companies?select=id").headers(headers).get().build()
                    httpClient.newCall(compReq).execute().use { r -> if (r.isSuccessful) compCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}

                try {
                    val usersReq = Request.Builder().url("${root}users?select=id").headers(headers).get().build()
                    httpClient.newCall(usersReq).execute().use { r -> if (r.isSuccessful) usersCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}

                try {
                    val clientsReq = Request.Builder().url("${root}clients?select=id").headers(headers).get().build()
                    httpClient.newCall(clientsReq).execute().use { r -> if (r.isSuccessful) clientsCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}

                try {
                    val eqReq = Request.Builder().url("${root}equipment?select=id").headers(headers).get().build()
                    httpClient.newCall(eqReq).execute().use { r -> if (r.isSuccessful) eqCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}

                try {
                    val ordersReq = Request.Builder().url("${root}service_orders?select=id").headers(headers).get().build()
                    httpClient.newCall(ordersReq).execute().use { r -> if (r.isSuccessful) ordersCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}

                try {
                    val testsReq = Request.Builder().url("${root}test_records?select=id").headers(headers).get().build()
                    httpClient.newCall(testsReq).execute().use { r -> if (r.isSuccessful) testsCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}

                try {
                    val instReq = Request.Builder().url("${root}lab_instruments?select=id").headers(headers).get().build()
                    httpClient.newCall(instReq).execute().use { r -> if (r.isSuccessful) instrumentsCount = JSONArray(r.body?.string() ?: "[]").length() }
                } catch (_: Exception) {}
            }

            val statusMsg = when {
                hasAuth -> "Conectado ao Supabase (Sessão Autenticada, ${latency}ms)"
                isSvc -> "Conectado com Chave Service Role (${latency}ms)"
                else -> "Servidor Supabase Online (${latency}ms) • Segurança v7 Ativa"
            }

            SupabaseConnectionStatus(
                isConnected = true,
                message = statusMsg,
                latencyMs = latency,
                remoteCompaniesCount = compCount,
                remoteUsersCount = usersCount,
                remoteClientsCount = clientsCount,
                remoteEquipmentCount = eqCount,
                remoteOrdersCount = ordersCount,
                remoteTestsCount = testsCount,
                remoteInstrumentsCount = instrumentsCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            SupabaseConnectionStatus(
                isConnected = false,
                message = "Falha ao conectar no Supabase: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    // -------------------------------------------------------------
    // Formato das tabelas = plataforma web v7.2 (src/services/supabaseMappers.ts)
    //  * somente colunas que existem no schema.sql (PostgREST recusa o lote
    //    inteiro com PGRST204 quando recebe uma coluna desconhecida)
    //  * todas as linhas de um lote com as MESMAS chaves (senão PGRST102)
    //  * datas DATE em yyyy-MM-dd (o app guarda dd/MM/yyyy)
    //  * enums em minúsculas (luva_isolante, em_uso, aberta...)
    //  * exclusão lógica (deleted_at), igual à web
    //  * payload JSONB: a web lê o payload antes das colunas; quando o registro
    //    já tem payload no servidor, os campos do app são mesclados nele
    // -------------------------------------------------------------

    /** Identifica as gravações feitas pelo APK (coluna device_id). */
    const val DEVICE_ID = "android-apk"

    /** Colunas de usuários liberadas ao app (password_hash/auth_user_id não são legíveis). */
    private const val USERS_SELECT_COLUMNS =
        "id,company_id,company_name,name,email,username,role,cargo,registration_number,crea_or_cft,phone,active,is_master_admin,signature_url,deleted_at,created_at,updated_at"

    private const val PAGE_SIZE = 1000
    private const val ID_CHUNK = 100

    private fun str(json: JSONObject, key: String, default: String = ""): String =
        if (!json.has(key) || json.isNull(key)) default else json.optString(key, default)

    private fun JSONObject.putText(key: String, value: String?): JSONObject =
        put(key, value?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)

    /** dd/MM/yyyy (ou qualquer formato aceito pelo app) -> yyyy-MM-dd; vazio -> null. */
    private fun isoDate(value: String?): Any {
        val parsed = com.example.util.DateUtils.parseDate(value) ?: return JSONObject.NULL
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(parsed)
    }

    /** Data do banco (yyyy-MM-dd ou timestamp) -> dd/MM/yyyy usado nas telas do app. */
    private fun appDate(json: JSONObject, key: String): String =
        com.example.util.DateUtils.formatDate(str(json, key).take(10))

    /** EquipmentType.name do app <-> valor da web. */
    private fun webEquipmentType(type: String): String = when (val t = type.trim().lowercase()) {
        "bota_dieletrica" -> "bota_dielétrica"
        "" -> "outro"
        else -> t
    }

    private fun appEquipmentType(type: String): String {
        val plain = java.text.Normalizer.normalize(type, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return plain.trim().uppercase().ifBlank { "OUTRO" }
    }

    /** Perfis do app -> perfis aceitos pelas regras de acesso (jvm_can_write). */
    private fun webRole(role: String): String {
        val r = role.trim().lowercase()
        return when {
            r in setOf("admin", "responsavel_tecnico", "tecnico", "administrativo", "cliente") -> r
            r.contains("admin") -> "admin"
            r.contains("engenheiro") || r.contains("respons") -> "responsavel_tecnico"
            r.contains("cliente") -> "cliente"
            else -> "tecnico"
        }
    }

    // -------------------------------------------------------------
    // Fetch operations (Download / Pull from Cloud)
    // -------------------------------------------------------------

    /** Lê as linhas de uma tabela paginando de 1000 em 1000 (limite padrão do PostgREST). */
    private suspend fun fetchRows(
        baseUrl: String,
        anonKey: String,
        table: String,
        select: String,
        order: String,
        label: String,
        deleted: Boolean = false
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        val root = normalizeUrl(baseUrl)
        val deletedFilter = if (deleted) "deleted_at=not.is.null" else "deleted_at=is.null"
        val rows = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val url = "${root}$table?select=$select&$deletedFilter&order=$order&limit=$PAGE_SIZE&offset=$offset"
            val page = executeWithAuthRetry(baseUrl, anonKey) {
                Request.Builder().url(url).headers(buildHeaders(anonKey)).get().build()
            }.use { response ->
                val body = response.body?.string() ?: "[]"
                if (!response.isSuccessful) {
                    throw IllegalStateException("Erro ao buscar $label no Supabase: HTTP ${response.code} $body")
                }
                JSONArray(body)
            }
            for (i in 0 until page.length()) rows.add(page.getJSONObject(i))
            if (page.length() < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        rows
    }

    /** IDs excluídos (deleted_at preenchido) na nuvem, para remover do aparelho no pull. */
    suspend fun fetchDeletedIds(baseUrl: String, anonKey: String, table: String): List<String> =
        fetchRows(baseUrl, anonKey, table, "id", "id.asc", table, deleted = true).map { str(it, "id") }

    suspend fun fetchCompanies(baseUrl: String, anonKey: String): List<CompanyEntity> =
        fetchRows(baseUrl, anonKey, "companies", "*", "name.asc", "empresas").map { jsonToCompany(it) }

    suspend fun fetchUsers(baseUrl: String, anonKey: String): List<UserEntity> =
        fetchRows(baseUrl, anonKey, "users", USERS_SELECT_COLUMNS, "name.asc", "usuários").map { jsonToUser(it) }

    suspend fun fetchClients(baseUrl: String, anonKey: String): List<ClientEntity> =
        fetchRows(baseUrl, anonKey, "clients", "*", "razao_social.asc,id.asc", "clientes").map { jsonToClient(it) }

    suspend fun fetchEquipment(baseUrl: String, anonKey: String): List<EquipmentEntity> =
        fetchRows(baseUrl, anonKey, "equipment", "*", "tag.asc,id.asc", "equipamentos").map { jsonToEquipment(it) }

    /** OS dos Ensaios de EPI: as OS do módulo SPDA da plataforma não são baixadas (continuam no banco, intactas). */
    suspend fun fetchServiceOrders(baseUrl: String, anonKey: String): List<ServiceOrderEntity> =
        fetchRows(baseUrl, anonKey, "service_orders", "*", "os_number.desc,id.asc", "ordens de serviço")
            .filterNot { isSpdaServiceOrderJson(it) }
            .map { jsonToServiceOrder(it) }

    /**
     * OS do módulo SPDA da plataforma: payload.module = "spda"; as antigas, sem marca,
     * quando a descrição é de SPDA. Ficam fora do app de Ensaios de EPI.
     */
    fun isSpdaServiceOrderJson(json: JSONObject): Boolean {
        val payload: JSONObject? = when (val p = json.opt("payload")) {
            is JSONObject -> p
            is String -> runCatching { JSONObject(p) }.getOrNull()
            else -> null
        }
        val module = payload?.optString("module", "")?.trim().orEmpty()
        if (module.isNotEmpty() && module != "null") return module.equals("spda", ignoreCase = true)
        val text = listOf(str(json, "scope_description"), str(json, "technical_notes"), payload?.optString("notes", "").orEmpty())
            .joinToString(" ")
        return isSpdaDescription(text)
    }

    /** Descrição de OS do SPDA (palavra "SPDA"). */
    fun isSpdaDescription(text: String?): Boolean =
        !text.isNullOrBlank() && Regex("\\bSPDA\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

    suspend fun fetchTestRecords(baseUrl: String, anonKey: String): List<TestRecordEntity> =
        fetchRows(baseUrl, anonKey, "test_records", "*", "test_date.desc,id.asc", "laudos").map { jsonToTestRecord(it) }

    suspend fun fetchLabInstruments(baseUrl: String, anonKey: String): List<LabInstrumentEntity> =
        fetchRows(baseUrl, anonKey, "lab_instruments", "*", "type.asc,id.asc", "instrumentos").map { jsonToLabInstrument(it) }

    // -------------------------------------------------------------
    // Push operations (Upload / Upsert to Cloud)
    // -------------------------------------------------------------

    private data class RemoteMeta(val deviceId: String, val payload: JSONObject?)

    /** device_id e payload atuais no servidor dos registros que serão enviados. */
    private suspend fun fetchRemoteMeta(baseUrl: String, anonKey: String, table: String, ids: List<String>): Map<String, RemoteMeta> =
        withContext(Dispatchers.IO) {
            val root = normalizeUrl(baseUrl)
            val result = mutableMapOf<String, RemoteMeta>()
            ids.distinct().chunked(ID_CHUNK).forEach { chunk ->
                val inList = chunk.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" }
                val url = "${root}$table?select=id,device_id,payload&id=in.(${java.net.URLEncoder.encode(inList, "UTF-8")})"
                executeWithAuthRetry(baseUrl, anonKey) {
                    Request.Builder().url(url).headers(buildHeaders(anonKey)).get().build()
                }.use { response ->
                    val body = response.body?.string() ?: "[]"
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: $body")
                    }
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        result[str(o, "id")] = RemoteMeta(str(o, "device_id"), o.optJSONObject("payload"))
                    }
                }
            }
            result
        }

    /** snake_case da coluna -> chave camelCase do payload da web (glove_length_mm -> gloveLength_mm). */
    private fun payloadKey(column: String): String {
        val units = mapOf("mm" to "mm", "kv" to "kV", "ma" to "mA")
        val parts = column.split("_")
        val unit = parts.last().takeIf { parts.size > 1 && it in units }
        val words = if (unit != null) parts.dropLast(1) else parts
        val camel = words.first() + words.drop(1).joinToString("") { w -> w.replaceFirstChar { it.uppercase() } }
        return if (unit != null) "${camel}_${units.getValue(unit)}" else camel
    }

    /**
     * Base do payload de um ensaio novo: os mesmos valores padrão que a web usa ao
     * montar um ensaio só pelas colunas (rowToTest). As colunas do registro são
     * mescladas por cima (mergePayload), então o resultado equivale ao que a web
     * montaria, mais os campos que não têm coluna (tabela do detector, diâmetro do bastão).
     */
    private fun newTestPayloadBase(): JSONObject = JSONObject()
        .put("visualInspection", JSONArray())
        .put("visualInspectionPassed", true)
        .put("instrumentsUsed", JSONArray())
        .put("syncVersion", 1)
        .put("deviceId", DEVICE_ID)
        .put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))

    /**
     * Mescla os campos do app no payload que a web já gravou (a web lê o payload
     * antes das colunas). Registro sem payload no servidor fica com payload nulo
     * e a web monta o registro pelas colunas.
     */
    private fun mergePayload(row: JSONObject, remote: JSONObject?, renames: Map<String, String>, skip: Set<String>): Any {
        if (remote == null) return JSONObject.NULL
        val merged = JSONObject(remote.toString())
        val baseSkip = setOf("id", "payload", "device_id", "deleted_at")
        val keys = row.keys()
        while (keys.hasNext()) {
            val col = keys.next()
            if (col in baseSkip || col in skip) continue
            val value = row.get(col)
            val key = renames[col] ?: payloadKey(col)
            if (value == JSONObject.NULL) merged.remove(key) else merged.put(key, value)
        }
        merged.put("updatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
        return merged
    }

    /**
     * Envia as linhas em lote (upsert por id).
     * force = false (envio geral): não sobrescreve registros cuja última gravação
     * veio de outro dispositivo (plataforma web), para não apagar edições feitas lá.
     */
    private suspend fun upsertRows(
        baseUrl: String,
        anonKey: String,
        table: String,
        rows: List<JSONObject>,
        force: Boolean,
        renames: Map<String, String> = emptyMap(),
        skipInPayload: Set<String> = emptySet(),
        /** Campos só do payload da web (sem coluna própria), por id do registro. */
        payloadExtras: Map<String, JSONObject> = emptyMap()
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext Result.success(0)
        val root = normalizeUrl(baseUrl)
        try {
            val remote = fetchRemoteMeta(baseUrl, anonKey, table, rows.map { it.getString("id") })
            val toSend = rows.filter { row ->
                val meta = remote[row.getString("id")]
                force || meta == null || meta.deviceId.isBlank() || meta.deviceId == DEVICE_ID
            }
            if (toSend.isEmpty()) return@withContext Result.success(0)
            val jsonArray = JSONArray()
            for (row in toSend) {
                val extras = payloadExtras[row.getString("id")]
                // registro novo com campos só do payload: parte dos mesmos padrões que a web usaria
                val base = remote[row.getString("id")]?.payload ?: if (extras != null) newTestPayloadBase() else null
                val payload = mergePayload(row, base, renames, skipInPayload)
                if (payload is JSONObject) payloadExtras[row.getString("id")]?.let { ex -> ex.keys().forEach { k -> payload.put(k, ex.get(k)) } }
                row.put("payload", payload)
                row.put("device_id", DEVICE_ID)
                row.put("deleted_at", JSONObject.NULL)
                jsonArray.put(row)
            }
            val body = jsonArray.toString().toRequestBody(jsonMediaType)
            executeWithAuthRetry(baseUrl, anonKey) {
                Request.Builder()
                    .url("${root}$table?on_conflict=id")
                    .headers(buildHeaders(anonKey, isUpsert = true))
                    .post(body)
                    .build()
            }.use { r ->
                if (!r.isSuccessful) {
                    val err = r.body?.string() ?: ""
                    Log.e(TAG, "upsert $table falhou: HTTP ${r.code} $err")
                    Result.failure(Exception("HTTP ${r.code}: $err"))
                } else {
                    Result.success(toSend.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "upsert $table error", e)
            Result.failure(e)
        }
    }

    /** Somente a empresa do usuário logado (as regras do banco recusam as demais). */
    suspend fun upsertCompanies(baseUrl: String, anonKey: String, companies: List<CompanyEntity>, force: Boolean = true): Result<Int> {
        val ownId = currentCompanyProfile?.id ?: currentUserProfile?.companyId
        val own = companies.filter { !ownId.isNullOrBlank() && it.id == ownId }
        return upsertRows(baseUrl, anonKey, "companies", own.map { companyToJson(it) }, force,
            renames = mapOf("legal_name" to "legalName"))
    }

    /** A senha nunca é enviada: contas de login são criadas pelo administrador na plataforma. */
    suspend fun upsertUsers(baseUrl: String, anonKey: String, users: List<UserEntity>, force: Boolean = true): Result<Int> =
        upsertRows(baseUrl, anonKey, "users", users.map { userToJson(it) }, force,
            renames = mapOf("crea_or_cft" to "creaOrCft"))

    suspend fun upsertClients(baseUrl: String, anonKey: String, clients: List<ClientEntity>, force: Boolean = true): Result<Int> =
        upsertRows(baseUrl, anonKey, "clients", clients.map { clientToJson(it) }, force,
            renames = mapOf("contato_responsavel" to "responsavel"),
            skipInPayload = setOf("status"))

    suspend fun upsertEquipment(baseUrl: String, anonKey: String, equipment: List<EquipmentEntity>, force: Boolean = true): Result<Int> =
        upsertRows(baseUrl, anonKey, "equipment", equipment.map { equipmentToJson(it) }, force)

    suspend fun upsertServiceOrders(baseUrl: String, anonKey: String, orders: List<ServiceOrderEntity>, force: Boolean = true): Result<Int> =
        upsertRows(baseUrl, anonKey, "service_orders", orders.map { serviceOrderToJson(it) }, force,
            renames = mapOf("scheduled_for" to "scheduledDate", "scope_description" to "notes"),
            skipInPayload = setOf("opened_at", "priority", "total_items"))

    suspend fun upsertTestRecords(baseUrl: String, anonKey: String, tests: List<TestRecordEntity>, force: Boolean = true): Result<Int> =
        upsertRows(baseUrl, anonKey, "test_records", tests.map { testRecordToJson(it) }, force,
            // como na web, fotos e assinaturas ficam só nas colunas próprias
            skipInPayload = setOf("photos", "technician_signature", "tech_responsible_signature"),
            // campos só do registro da web: diâmetro do bastão (NBR 11854) e tabela do detector por indução
            payloadExtras = tests.mapNotNull { t ->
                val ex = JSONObject()
                if (isStickType(t.equipmentType)) {
                    ex.put("poleDiameter_mm", com.example.domain.NormsEngine.closestPoleDiameter(t.gloveLength_mm))
                    ex.put("testPoints", t.stickTestPoints.coerceAtLeast(1))
                }
                // escada: quantidade de pontos ensaiados (mesmo campo da web)
                if (t.equipmentType.equals("ESCADA_ISOLADA", ignoreCase = true)) ex.put("testPoints", t.stickTestPoints.coerceAtLeast(1))
                if (t.equipmentType.equals("DETECTOR_INDUCAO", ignoreCase = true)) {
                    ex.put("inductionPoints", com.example.domain.InductionDetector.toJsonArray(
                        com.example.domain.InductionDetector.normalize(com.example.domain.InductionDetector.parse(t.inductionPointsJson))))
                }
                if (ex.length() > 0) t.id to ex else null
            }.toMap())

    suspend fun upsertLabInstruments(baseUrl: String, anonKey: String, instruments: List<LabInstrumentEntity>, force: Boolean = true): Result<Int> =
        upsertRows(baseUrl, anonKey, "lab_instruments", instruments.map { labInstrumentToJson(it) }, force,
            renames = mapOf("calibration_lab" to "calibratingLab", "resolution" to "measurementRange", "tag" to "assetNumber"),
            skipInPayload = setOf("name", "operational_status"))

    // -------------------------------------------------------------
    // Normas técnicas (public.norms) — mesma base de critérios da web
    // -------------------------------------------------------------
    /**
     * Linhas de norms visíveis à empresa: versão oficial (company_id nulo) e as
     * versões da empresa, inclusive excluídas (a exclusão vale só para ela).
     */
    suspend fun fetchNormRows(baseUrl: String, anonKey: String, companyId: String?): List<JSONObject> = withContext(Dispatchers.IO) {
        val root = normalizeUrl(baseUrl)
        val scope = if (companyId.isNullOrBlank()) "company_id=is.null"
            else "or=" + java.net.URLEncoder.encode("(company_id.is.null,company_id.eq.$companyId)", "UTF-8")
        val rows = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val url = "${root}norms?select=id,norm_id,company_id,payload,deleted_at&$scope&order=id.asc&limit=$PAGE_SIZE&offset=$offset"
            val page = executeWithAuthRetry(baseUrl, anonKey) {
                Request.Builder().url(url).headers(buildHeaders(anonKey)).get().build()
            }.use { r ->
                val body = r.body?.string() ?: "[]"
                if (!r.isSuccessful) throw IllegalStateException("Erro ao buscar normas no Supabase: HTTP ${r.code} $body")
                JSONArray(body)
            }
            for (i in 0 until page.length()) rows.add(page.getJSONObject(i))
            if (page.length() < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        rows
    }

    // -------------------------------------------------------------
    // Configurações da empresa (companies.lab_info) — mesmas da web
    // -------------------------------------------------------------
    /** lab_info da empresa (null se não houver ou falhar). */
    suspend fun fetchCompanyLabInfo(baseUrl: String, anonKey: String, companyId: String): JSONObject? = withContext(Dispatchers.IO) {
        if (companyId.isBlank()) return@withContext null
        val root = normalizeUrl(baseUrl)
        val id = java.net.URLEncoder.encode(companyId, "UTF-8")
        try {
            executeWithAuthRetry(baseUrl, anonKey) {
                Request.Builder().url("${root}companies?select=lab_info&id=eq.$id").headers(buildHeaders(anonKey)).get().build()
            }.use { r ->
                if (!r.isSuccessful) return@withContext null
                val arr = JSONArray(r.body?.string() ?: "[]")
                if (arr.length() == 0) null else arr.getJSONObject(0).optJSONObject("lab_info")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCompanyLabInfo error: ${e.message}")
            null
        }
    }

    /**
     * Grava as configurações na empresa (lab_info + colunas legíveis). Mescla com o
     * lab_info atual do servidor para não apagar campos que só a web usa (logotipo,
     * layout de certificados de treinamento, módulos...).
     */
    suspend fun updateCompanyLabInfo(baseUrl: String, anonKey: String, companyId: String, labInfo: JSONObject): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val root = normalizeUrl(baseUrl)
            try {
                val merged = fetchCompanyLabInfo(baseUrl, anonKey, companyId) ?: JSONObject()
                val keys = labInfo.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = labInfo.get(k)
                    // objetos internos (RT, emissão) também são mesclados campo a campo
                    if (v is JSONObject && merged.optJSONObject(k) != null) {
                        val inner = merged.getJSONObject(k)
                        val ik = v.keys()
                        while (ik.hasNext()) { val x = ik.next(); inner.put(x, v.get(x)) }
                    } else merged.put(k, v)
                }
                val body = JSONObject()
                    .put("lab_info", merged)
                    .put("name", str(labInfo, "name", "Empresa").ifBlank { "Empresa" })
                    .putText("legal_name", str(labInfo, "legalName"))
                    .putText("cnpj", str(labInfo, "cnpj"))
                    .putText("city", str(labInfo, "city"))
                    .putText("state", str(labInfo, "state"))
                    .putText("cep", str(labInfo, "cep"))
                    .putText("phone", str(labInfo, "phone"))
                    .putText("email", str(labInfo, "email"))
                    .put("device_id", DEVICE_ID)
                    .toString().toRequestBody(jsonMediaType)
                val id = java.net.URLEncoder.encode(companyId, "UTF-8")
                executeWithAuthRetry(baseUrl, anonKey) {
                    Request.Builder().url("${root}companies?id=eq.$id").headers(buildHeaders(anonKey))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .patch(body).build()
                }.use { r ->
                    val text = r.body?.string().orEmpty()
                    when {
                        !r.isSuccessful -> Result.failure(Exception("HTTP ${r.code}: $text"))
                        // RLS sem permissão: o PATCH não altera nenhuma linha (resposta vazia)
                        text.trim() == "[]" -> Result.failure(Exception("sem permissão para alterar a empresa (perfil administrador necessário)"))
                        else -> Result.success(true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateCompanyLabInfo error", e)
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------
    // Storage: fotos e assinaturas (bucket público "jvm-evidencias")
    // -------------------------------------------------------------
    const val EVIDENCE_BUCKET = "jvm-evidencias"

    private val uploadClient by lazy {
        httpClient.newBuilder()
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun getStorageUrl(baseUrl: String): String = getAuthUrl(baseUrl).replace("/auth/v1/", "/storage/v1/")

    /** Pasta da empresa no bucket: mesma regra de jvm_storage_folder() no banco. */
    fun storageFolder(companyId: String): String =
        companyId.ifBlank { "sem-empresa" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    /** Resultado do envio de uma imagem: URL pública ou o motivo da falha. */
    data class UploadResult(val url: String?, val error: String?)

    /**
     * O Storage responde HTTP 400 (e não 401) quando o token de login venceu:
     * {"statusCode":"403","error":"Unauthorized","message":"jwt expired"}.
     */
    private fun isStorageAuthError(code: Int, body: String): Boolean =
        code == 401 || code == 403 ||
            (code == 400 && Regex("jwt|unauthorized|\"403\"|\"401\"|invalid signature", RegexOption.IGNORE_CASE).containsMatchIn(body))

    /** Envia (ou substitui) uma imagem e devolve a URL pública, ou o motivo da falha. */
    suspend fun uploadEvidence(baseUrl: String, anonKey: String, path: String, bytes: ByteArray, mime: String): UploadResult =
        withContext(Dispatchers.IO) {
            val storage = getStorageUrl(baseUrl)
            val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            val buildRequest = {
                Request.Builder()
                    .url("${storage}object/$EVIDENCE_BUCKET/$encodedPath")
                    .addHeader("apikey", anonKey.trim())
                    .addHeader("Authorization", "Bearer ${currentAuthToken ?: anonKey.trim()}")
                    .addHeader("x-upsert", "true")
                    .addHeader("cache-control", "max-age=31536000")
                    .post(bytes.toRequestBody(mime.toMediaType()))
                    .build()
            }
            try {
                var attempt = 0
                while (true) {
                    val (code, body) = uploadClient.newCall(buildRequest()).execute().use { r ->
                        r.code to (if (r.isSuccessful) "" else r.body?.string().orEmpty())
                    }
                    if (code in 200..299) {
                        return@withContext UploadResult("${storage}object/public/$EVIDENCE_BUCKET/$encodedPath", null)
                    }
                    if (attempt == 0 && isStorageAuthError(code, body) && refreshAuthToken(baseUrl, anonKey)) {
                        attempt++
                        continue
                    }
                    Log.e(TAG, "upload $path falhou: HTTP $code $body")
                    val reason = when {
                        Regex("bucket not found", RegexOption.IGNORE_CASE).containsMatchIn(body) ->
                            "bucket \"$EVIDENCE_BUCKET\" não existe no Supabase (execute o schema.sql da plataforma)"
                        Regex("row-level security|policy", RegexOption.IGNORE_CASE).containsMatchIn(body) ->
                            "sem permissão na pasta da empresa no Storage"
                        Regex("exceeded the maximum|too large|payload too large", RegexOption.IGNORE_CASE).containsMatchIn(body) || code == 413 ->
                            "imagem maior que o limite do bucket (10 MB)"
                        isStorageAuthError(code, body) -> "sessão expirada — entre novamente"
                        else -> "HTTP $code ${body.take(160)}"
                    }
                    return@withContext UploadResult(null, reason)
                }
                @Suppress("UNREACHABLE_CODE")
                UploadResult(null, "falha desconhecida")
            } catch (e: Exception) {
                Log.e(TAG, "upload $path error", e)
                UploadResult(null, "sem conexão (${e.message})")
            }
        }

    // -------------------------------------------------------------
    // Numeração sem duplicidade entre aparelhos (RPC jvm_reserve_numbers)
    // -------------------------------------------------------------
    /** Reserva [quantity] números do tipo test/report/certificate/os. Devolve (início, fim) ou null. */
    suspend fun reserveNumbers(baseUrl: String, anonKey: String, kind: String, period: String, quantity: Int, localMax: Long): Pair<Long, Long>? =
        withContext(Dispatchers.IO) {
            if (currentAuthToken.isNullOrBlank()) return@withContext null
            val root = normalizeUrl(baseUrl)
            val body = JSONObject()
                .put("p_kind", kind)
                .put("p_period", period)
                .put("p_quantity", quantity)
                .put("p_local_max", localMax)
                .toString().toRequestBody(jsonMediaType)
            try {
                executeWithAuthRetry(baseUrl, anonKey) {
                    Request.Builder().url("${root}rpc/jvm_reserve_numbers").headers(buildHeaders(anonKey))
                        .addHeader("Content-Type", "application/json").post(body).build()
                }.use { r ->
                    val text = r.body?.string() ?: ""
                    if (!r.isSuccessful) {
                        Log.w(TAG, "jvm_reserve_numbers($kind) falhou: HTTP ${r.code} $text")
                        return@withContext null
                    }
                    val json = JSONObject(text)
                    Pair(json.getLong("start"), json.getLong("end"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "jvm_reserve_numbers($kind) error: ${e.message}")
                null
            }
        }

    /** Exclusão lógica (deleted_at), como a web: a exclusão chega a todos os dispositivos. */
    suspend fun deleteEntity(baseUrl: String, anonKey: String, table: String, id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val root = normalizeUrl(baseUrl)
        try {
            val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
            val body = JSONObject().put("deleted_at", nowIso).put("device_id", DEVICE_ID)
                .toString().toRequestBody(jsonMediaType)
            val encodedId = java.net.URLEncoder.encode(id, "UTF-8")
            executeWithAuthRetry(baseUrl, anonKey) {
                Request.Builder()
                    .url("${root}$table?id=eq.$encodedId")
                    .headers(buildHeaders(anonKey))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(body)
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "delete $table/$id falhou: HTTP ${response.code} $err")
                    Result.failure(Exception("HTTP ${response.code}: $err"))
                } else {
                    Result.success(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteEntity error from table $table", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // JSON Mappers (mesmas colunas de supabase/schema.sql da web v7.2)
    // -------------------------------------------------------------

    private fun targetCompanyId(fallback: String): String {
        val target = currentCompanyProfile?.id ?: currentUserProfile?.companyId
        return if (!target.isNullOrBlank()) target else fallback
    }

    private fun companyToJson(c: CompanyEntity): JSONObject {
        // logo_url fica de fora: o app só tem o caminho local da imagem
        return JSONObject()
            .put("id", c.id)
            .put("name", c.name.ifBlank { c.legalName.ifBlank { "Empresa" } })
            .put("legal_name", c.legalName.ifBlank { c.name })
            .put("cnpj", c.cnpj)
            .putText("city", c.city)
            .putText("state", c.state)
            .putText("phone", c.phone)
            .putText("email", c.email)
    }

    private fun jsonToCompany(json: JSONObject): CompanyEntity {
        val p = json.optJSONObject("payload")
        return CompanyEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            name = str(json, "name", "Empresa"),
            legalName = str(json, "legal_name", str(json, "name", "Empresa")),
            cnpj = str(json, "cnpj"),
            creaCompanyRegister = p?.let { str(it, "creaCompanyRegister") } ?: "",
            address = p?.let { str(it, "address") } ?: "",
            city = str(json, "city"),
            state = str(json, "state"),
            phone = str(json, "phone"),
            email = str(json, "email"),
            website = p?.let { str(it, "website") } ?: "",
            logoUri = str(json, "logo_url"),
            createdAt = str(json, "created_at")
        )
    }

    private fun userToJson(u: UserEntity): JSONObject {
        val email = u.email.trim().lowercase()
        return JSONObject()
            .put("id", u.id)
            .put("company_id", targetCompanyId(u.companyId))
            .putText("company_name", currentCompanyProfile?.name ?: u.companyName)
            .put("name", u.name)
            .put("email", email)
            .put("role", webRole(u.role))
            .putText("crea_or_cft", u.registrationCouncil)
            .putText("phone", u.phone)
    }

    private fun jsonToUser(json: JSONObject): UserEntity {
        return UserEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            companyId = str(json, "company_id"),
            companyName = str(json, "company_name"),
            name = str(json, "name", str(json, "username", "Usuário")),
            email = str(json, "email", str(json, "username")),
            // senhas nunca ficam no aparelho (mesma regra da web)
            password = "",
            role = str(json, "role", "tecnico"),
            registrationCouncil = str(json, "crea_or_cft", str(json, "registration_number")),
            phone = str(json, "phone"),
            signatureImageUri = str(json, "signature_url"),
            createdAt = str(json, "created_at")
        )
    }

    private fun clientToJson(c: ClientEntity): JSONObject {
        return JSONObject()
            .put("id", c.id)
            .put("company_id", targetCompanyId(c.companyId))
            .put("razao_social", c.razaoSocial.ifBlank { c.nomeFantasia.ifBlank { "Cliente" } })
            .put("nome_fantasia", c.nomeFantasia.ifBlank { c.razaoSocial })
            .put("cnpj", c.cnpj)
            .putText("inscricao_estadual", c.inscricaoEstadual)
            .putText("email", c.contactEmail)
            .putText("telefone", c.contactPhone)
            .putText("contato_responsavel", c.contactName)
            .putText("endereco", c.address)
            .putText("cidade", c.city)
            .putText("estado", c.state)
            .putText("cep", c.cep)
            .put("status", "ativo")
            .putText("observacoes", c.observacoes)
    }

    private fun jsonToClient(json: JSONObject): ClientEntity {
        return ClientEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            razaoSocial = str(json, "razao_social"),
            nomeFantasia = str(json, "nome_fantasia"),
            cnpj = str(json, "cnpj"),
            inscricaoEstadual = str(json, "inscricao_estadual"),
            address = str(json, "endereco"),
            city = str(json, "cidade"),
            state = str(json, "estado"),
            cep = str(json, "cep"),
            contactPhone = str(json, "telefone"),
            contactEmail = str(json, "email"),
            contactName = str(json, "contato_responsavel"),
            observacoes = str(json, "observacoes"),
            companyId = str(json, "company_id", currentCompanyProfile?.id ?: ""),
            companyName = currentCompanyProfile?.name ?: "",
            createdAt = str(json, "created_at"),
            updatedAt = str(json, "updated_at"),
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun equipmentToJson(eq: EquipmentEntity): JSONObject {
        return JSONObject()
            .put("id", eq.id)
            .put("company_id", targetCompanyId(eq.companyId))
            .put("uuid", eq.uuid.ifBlank { eq.id })
            .putText("client_id", eq.clientId)
            .putText("client_name", eq.clientName)
            .putText("service_order_id", eq.serviceOrderId)
            .putText("service_order_number", eq.serviceOrderNumber)
            .put("type", webEquipmentType(eq.type))
            .put("tag", eq.tag.ifBlank { eq.id })
            .putText("serial_number", eq.serialNumber)
            .putText("ca_number", eq.caNumber)
            .putText("asset_number", eq.assetNumber)
            .putText("manufacturer", eq.manufacturer)
            .putText("model", eq.model)
            .put("dielectric_class", eq.dielectricClass.ifBlank { "0" })
            .putText("size_or_length", eq.sizeOrLength)
            .put("glove_length_mm", if (eq.gloveLength_mm > 0) eq.gloveLength_mm else JSONObject.NULL)
            .put("status", eq.status.lowercase().ifBlank { "em_uso" })
            .putText("collaborator_name", eq.collaboratorName)
            .putText("collaborator_registration", eq.collaboratorRegistration)
            .putText("collaborator_sector", eq.collaboratorSector)
            .put("last_test_date", isoDate(eq.lastTestDate))
            .put("next_test_due_date", isoDate(eq.nextTestDueDate))
            .put("retest_interval_months", if (eq.retestIntervalMonths > 0) eq.retestIntervalMonths else 6)
            .put("qr_code", eq.qrCode.ifBlank { eq.tag })
    }

    private fun jsonToEquipment(json: JSONObject): EquipmentEntity {
        return EquipmentEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            uuid = str(json, "uuid", str(json, "id")),
            clientId = str(json, "client_id"),
            clientName = str(json, "client_name"),
            serviceOrderId = str(json, "service_order_id"),
            serviceOrderNumber = str(json, "service_order_number"),
            type = appEquipmentType(str(json, "type", "outro")),
            tag = str(json, "tag"),
            serialNumber = str(json, "serial_number"),
            caNumber = str(json, "ca_number"),
            assetNumber = str(json, "asset_number"),
            manufacturer = str(json, "manufacturer"),
            model = str(json, "model"),
            dielectricClass = str(json, "dielectric_class", "0"),
            sizeOrLength = str(json, "size_or_length"),
            gloveLength_mm = json.optInt("glove_length_mm", 0),
            status = str(json, "status", "em_uso").uppercase(),
            collaboratorName = str(json, "collaborator_name"),
            collaboratorRegistration = str(json, "collaborator_registration"),
            collaboratorSector = str(json, "collaborator_sector"),
            lastTestDate = appDate(json, "last_test_date"),
            nextTestDueDate = appDate(json, "next_test_due_date"),
            retestIntervalMonths = json.optInt("retest_interval_months", 6),
            qrCode = str(json, "qr_code", str(json, "tag")),
            companyId = str(json, "company_id"),
            companyName = currentCompanyProfile?.name ?: "",
            userId = currentUserProfile?.id ?: "",
            userName = currentUserProfile?.name ?: "",
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun serviceOrderToJson(order: ServiceOrderEntity): JSONObject {
        val opened = isoDate(order.openDate)
        val openedAt = if (opened is String) "${opened}T00:00:00Z" else
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val equipmentIds = JSONArray()
        order.equipmentIds.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { equipmentIds.put(it) }
        return JSONObject()
            .put("id", order.id)
            .put("company_id", targetCompanyId(order.companyId))
            .put("os_number", order.osNumber.ifBlank { "OS-${order.id}" })
            .putText("client_id", order.clientId)
            .put("client_name", order.clientName.ifBlank { "Cliente Geral" })
            .put("status", order.status.lowercase().ifBlank { "aberta" })
            .put("opened_at", openedAt)
            .put("scheduled_for", isoDate(order.scheduledDate))
            .putText("technician_id", order.technicianId)
            .put("technician_name", order.technicianName.ifBlank { "Técnico Responsável" })
            .putText("art_number", order.artNumber)
            .putText("scope_description", order.description)
            .put("equipment_ids", equipmentIds)
            .put("total_items", equipmentIds.length())
    }

    private fun jsonToServiceOrder(json: JSONObject): ServiceOrderEntity {
        val eqIdsList = mutableListOf<String>()
        val arr = json.optJSONArray("equipment_ids")
        if (arr != null) {
            for (i in 0 until arr.length()) eqIdsList.add(arr.optString(i))
        }
        return ServiceOrderEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            osNumber = str(json, "os_number"),
            clientId = str(json, "client_id"),
            clientName = str(json, "client_name"),
            status = str(json, "status", "aberta").uppercase(),
            openDate = appDate(json, "opened_at"),
            scheduledDate = appDate(json, "scheduled_for"),
            technicianId = str(json, "technician_id"),
            technicianName = str(json, "technician_name", "Técnico Responsável"),
            artNumber = str(json, "art_number"),
            description = str(json, "scope_description", str(json, "technical_notes")),
            equipmentIds = eqIdsList.joinToString(","),
            companyId = str(json, "company_id"),
            companyName = currentCompanyProfile?.name ?: "",
            syncStatus = SyncStatus.SYNCED
        )
    }

    // ---- Fotos e assinaturas -------------------------------------------------
    /** Fotos do app: "rótulo::uri" separadas por "|||". */
    fun parsePhotoEntries(photoUris: String): List<Pair<String, String>> =
        photoUris.split("|||").filter { it.isNotBlank() }.map { entry ->
            val parts = entry.split("::", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "Foto do Ensaio" to entry
        }

    /** Arquivo do aparelho (precisa ser enviado ao Storage); o resto já é URL/imagem embutida. */
    fun isLocalMedia(uri: String): Boolean =
        uri.startsWith("/") || uri.startsWith("content://") || uri.startsWith("file://")

    fun parseRemoteMedia(json: String): Map<String, String> = try {
        val obj = JSONObject(json.ifBlank { "{}" })
        obj.keys().asSequence().associateWith { obj.getString(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    /** URL a enviar para a web: a do Storage para arquivos locais; null se ainda não enviada. */
    private fun publicMediaUrl(uri: String, remote: Map<String, String>): String? = when {
        uri.isBlank() -> null
        isLocalMedia(uri) -> remote[uri]
        else -> uri
    }

    private fun signatureJson(uri: String, remote: Map<String, String>, name: String, role: String, document: String, signedAt: String): Any {
        val url = publicMediaUrl(uri, remote) ?: return JSONObject.NULL
        return JSONObject()
            .put("signatureImage", url)
            .put("userName", name)
            .put("userRole", role)
            .putText("documentNumber", document)
            .put("signedAt", signedAt)
    }

    private fun testRecordToJson(t: TestRecordEntity): JSONObject {
        val testDate = isoDate(t.testDate).let { if (it is String) it else isoDate(com.example.util.DateUtils.today()) }
        val signedAt = "${testDate}T${t.testTime.ifBlank { "00:00" }}:00"
        val environmental = JSONObject()
            .put("temperatureC", t.temperatureC)
            .put("relativeHumidityPercent", t.relativeHumidityPercent)
            .put("measurementDateTime", signedAt)
        // Fotos/assinaturas: URLs do bucket jvm-evidencias (enviadas antes pelo
        // repositório e guardadas em remoteMedia); as ainda não enviadas ficam de fora
        val remote = parseRemoteMedia(t.remoteMedia)
        val photos = JSONArray()
        parsePhotoEntries(t.photoUris).forEachIndexed { idx, (label, uri) ->
            val url = publicMediaUrl(uri, remote) ?: return@forEachIndexed
            photos.put(
                JSONObject()
                    .put("id", "foto-${t.id.takeLast(8)}-$idx")
                    .put("category", "durante")
                    .put("url", url)
                    .put("caption", label)
                    .put("timestamp", signedAt)
                    .put("userName", t.technicianName)
            )
        }
        return JSONObject()
            .put("id", t.id)
            .put("company_id", targetCompanyId(t.companyId))
            .put("uuid", t.uuid.ifBlank { "uuid-${t.id}" })
            .put("test_number", t.testNumber.ifBlank { t.id })
            .put("report_number", t.reportNumber.ifBlank { t.testNumber.ifBlank { t.id } })
            .putText("certificate_number", t.certificateNumber)
            .put("validation_code", t.validationCode.ifBlank { "VAL-" + t.id.filter { it.isLetterOrDigit() }.takeLast(10).uppercase() })
            .put("document_hash", t.documentHash.ifBlank { "HASH-" + t.id.takeLast(8).uppercase() })
            .putText("client_id", t.clientId)
            .put("client_name", t.clientName.ifBlank { "Cliente Geral" })
            .putText("equipment_id", t.equipmentId)
            .put("equipment_tag", t.equipmentTag.ifBlank { "S/TAG" })
            .put("equipment_type", webEquipmentType(t.equipmentType))
            .put("equipment_class", t.equipmentClass.ifBlank { "0" })
            .put("applied_class", t.equipmentClass.ifBlank { "0" })
            .putText("equipment_serial", t.equipmentSerial)
            .putText("equipment_ca", t.equipmentCa)
            .putText("collaborator_name", t.collaboratorName)
            .putText("collaborator_registration", t.collaboratorRegistration)
            .putText("collaborator_sector", t.collaboratorSector)
            .putText("service_order_id", t.serviceOrderId)
            .putText("service_order_number", t.serviceOrderNumber)
            .putText("art_number", t.artNumber)
            .putText("technician_id", t.technicianId)
            .put("technician_name", t.technicianName.ifBlank { "Técnico Responsável" })
            .putText("technician_cft_or_crea", t.technicianCftOrCrea)
            .putText("tech_responsible_id", t.techResponsibleId)
            .put("tech_responsible_name", t.techResponsibleName.ifBlank { "Responsável Técnico" })
            .putText("tech_responsible_crea", t.techResponsibleCrea)
            .put("test_date", testDate)
            .putText("test_time", t.testTime)
            .putText("location", t.location)
            .put("norm_code", t.normCode.ifBlank { "N/A" })
            .putText("procedure_code", t.procedureCode)
            .put("applied_voltage_kv", t.appliedVoltage_kV)
            .put("voltage_type", t.voltageType.ifBlank { "AC" })
            .put("application_duration_seconds", if (t.applicationDurationSeconds > 0) t.applicationDurationSeconds else 60)
            // bastões/varas (NBR 11854): corrente em µA com unidade "uA", como a web v7.4 grava
            .put("measured_leakage_current_ma", t.measuredLeakageCurrent_mA)
            .put("leakage_current_limit_ma", t.leakageCurrentLimit_mA)
            .put("current_unit", if (isStickType(t.equipmentType) || isMicroAmp(t.currentUnit)) "uA" else t.currentUnit.ifBlank { "mA" })
            .put("withstand_without_puncture", t.withstandWithoutPuncture)
            // bastões/varas: o app guarda o diâmetro neste campo; na web ele vai em poleDiameter_mm
            .put("glove_length_mm", if (!isStickType(t.equipmentType) && t.gloveLength_mm > 0) t.gloveLength_mm else JSONObject.NULL)
            .put("environmental", environmental)
            .put("result", t.result.uppercase().ifBlank { "PENDENTE" })
            .putText("result_rationale", t.resultRationale)
            .putText("technical_notes", t.technicalNotes)
            .put("retest_due_date", isoDate(t.retestDueDate))
            .put("photos", photos)
            .put("technician_signature", signatureJson(t.technicianSignature, remote, t.technicianName,
                "Técnico Executor", t.technicianCftOrCrea, signedAt))
            .put("tech_responsible_signature", signatureJson(t.techResponsibleSignature, remote, t.techResponsibleName,
                "Responsável Técnico", t.techResponsibleCrea, signedAt))
    }

    /**
     * Resposta de jvm_validar_certificado: {id, company_id, tech_responsible_signature,
     * payload{campos públicos do certificado em camelCase}}.
     */
    private fun publicPayloadToTestRecord(json: JSONObject): TestRecordEntity {
        val p = json.optJSONObject("payload") ?: JSONObject()
        val num = { key: String, default: Double -> if (p.has(key) && !p.isNull(key)) p.optDouble(key, default) else default }
        return TestRecordEntity(
            id = str(json, "id", str(p, "id")),
            uuid = str(p, "uuid", str(json, "id")),
            testNumber = str(p, "testNumber"),
            reportNumber = str(p, "reportNumber"),
            certificateNumber = str(p, "certificateNumber"),
            validationCode = str(p, "validationCode"),
            clientId = "",
            clientName = str(p, "clientName"),
            equipmentId = "",
            equipmentTag = str(p, "equipmentTag"),
            equipmentType = appEquipmentType(str(p, "equipmentType", "outro")),
            equipmentClass = str(p, "equipmentClass", str(p, "appliedClass", "0")),
            equipmentSerial = str(p, "equipmentSerial"),
            equipmentCa = str(p, "equipmentCa"),
            techResponsibleId = str(p, "techResponsibleId"),
            techResponsibleName = str(p, "techResponsibleName"),
            techResponsibleCrea = str(p, "techResponsibleCrea"),
            techResponsibleSignature = signatureUrl(json, "tech_responsible_signature"),
            testDate = com.example.util.DateUtils.formatDate(str(p, "testDate").take(10)),
            normCode = str(p, "normCode"),
            appliedVoltage_kV = num("appliedVoltage_kV", 0.0),
            voltageType = str(p, "voltageType", "AC"),
            applicationDurationSeconds = num("applicationDurationSeconds", 60.0).toInt(),
            gloveLength_mm = num("gloveLength_mm", 0.0).toInt(),
            result = str(p, "result", "PENDENTE"),
            retestDueDate = com.example.util.DateUtils.formatDate(str(p, "retestDueDate").take(10)),
            companyId = str(json, "company_id", str(p, "companyId")),
            companyName = str(p, "companyName"),
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun isMicroAmp(unit: String): Boolean = unit.trim().lowercase().let { it == "µa" || it == "ua" || it == "μa" }

    private fun isStickType(type: String): Boolean = type.trim().lowercase().let { it == "bastao_manobra" || it == "vara_manobra" || it == "aterramento_com_bastao" }

    /**
     * Bastões/varas: o app usa µA. A web v7.4 grava em µA com unidade "uA";
     * registros antigos em "mA" (critério de 100 kV: 0,1 mA = 100 µA) são convertidos.
     * Valores acima de 5 "mA" vieram de versões antigas do app que já gravavam µA.
     */
    private fun stickMicroAmps(json: JSONObject, key: String): Double {
        val v = json.optDouble(key, 0.0).let { if (it.isNaN()) 0.0 else it }
        if (!isStickType(str(json, "equipment_type"))) return v
        if (isMicroAmp(str(json, "current_unit"))) return v
        return if (v > 5.0) v else v * 1000.0
    }

    private fun photosToEntries(json: JSONObject): String {
        val arr = json.optJSONArray("photos") ?: return ""
        val clean = { s: String -> s.replace("|||", " ").replace("::", " ") }
        return (0 until arr.length()).mapNotNull { i ->
            val ph = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = str(ph, "url")
            if (url.isBlank()) null else "${clean(str(ph, "caption", "Foto do Ensaio")).ifBlank { "Foto do Ensaio" }}::$url"
        }.joinToString("|||")
    }

    private fun signatureUrl(json: JSONObject, key: String): String =
        json.optJSONObject(key)?.let { str(it, "signatureImage") } ?: ""

    private fun jsonToTestRecord(json: JSONObject): TestRecordEntity {
        val env = json.optJSONObject("environmental")
        return TestRecordEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            uuid = str(json, "uuid", str(json, "id")),
            testNumber = str(json, "test_number"),
            reportNumber = str(json, "report_number"),
            certificateNumber = str(json, "certificate_number"),
            validationCode = str(json, "validation_code"),
            documentHash = str(json, "document_hash"),
            clientId = str(json, "client_id"),
            clientName = str(json, "client_name"),
            collaboratorName = str(json, "collaborator_name"),
            collaboratorRegistration = str(json, "collaborator_registration"),
            collaboratorSector = str(json, "collaborator_sector"),
            equipmentId = str(json, "equipment_id"),
            equipmentTag = str(json, "equipment_tag"),
            equipmentType = appEquipmentType(str(json, "equipment_type", "outro")),
            equipmentClass = str(json, "equipment_class", str(json, "applied_class", "0")),
            equipmentSerial = str(json, "equipment_serial"),
            equipmentCa = str(json, "equipment_ca"),
            serviceOrderId = str(json, "service_order_id"),
            serviceOrderNumber = str(json, "service_order_number"),
            artNumber = str(json, "art_number"),
            technicianId = str(json, "technician_id"),
            technicianName = str(json, "technician_name"),
            technicianCftOrCrea = str(json, "technician_cft_or_crea"),
            techResponsibleId = str(json, "tech_responsible_id"),
            techResponsibleName = str(json, "tech_responsible_name"),
            techResponsibleCrea = str(json, "tech_responsible_crea"),
            testDate = appDate(json, "test_date"),
            testTime = str(json, "test_time"),
            location = str(json, "location"),
            normCode = str(json, "norm_code"),
            procedureCode = str(json, "procedure_code"),
            appliedVoltage_kV = json.optDouble("applied_voltage_kv", 0.0),
            voltageType = str(json, "voltage_type", "AC"),
            applicationDurationSeconds = json.optInt("application_duration_seconds", 60),
            measuredLeakageCurrent_mA = stickMicroAmps(json, "measured_leakage_current_ma"),
            leakageCurrentLimit_mA = stickMicroAmps(json, "leakage_current_limit_ma"),
            currentUnit = if (isStickType(str(json, "equipment_type"))) "µA" else str(json, "current_unit", "mA"),
            withstandWithoutPuncture = json.optBoolean("withstand_without_puncture", true),
            // bastões/varas: o app guarda o diâmetro (poleDiameter_mm da web) neste campo
            gloveLength_mm = if (isStickType(str(json, "equipment_type"))) json.optJSONObject("payload")?.optInt("poleDiameter_mm", 0)?.takeIf { it > 0 } ?: com.example.domain.NormsEngine.NBR11854_DEFAULT_DIAMETER_MM else json.optInt("glove_length_mm", 0),
            temperatureC = env?.optDouble("temperatureC", 0.0) ?: 0.0,
            relativeHumidityPercent = env?.optDouble("relativeHumidityPercent", 0.0) ?: 0.0,
            result = str(json, "result", "PENDENTE"),
            resultRationale = str(json, "result_rationale"),
            technicalNotes = str(json, "technical_notes"),
            retestDueDate = appDate(json, "retest_due_date"),
            technicianSignature = signatureUrl(json, "technician_signature"),
            techResponsibleSignature = signatureUrl(json, "tech_responsible_signature"),
            photoUris = photosToEntries(json),
            inductionPointsJson = json.optJSONObject("payload")?.optJSONArray("inductionPoints")?.toString() ?: "",
            stickTestPoints = (json.optJSONObject("payload")?.optInt("testPoints", 1) ?: 1).coerceAtLeast(1),
            companyId = str(json, "company_id"),
            companyName = currentCompanyProfile?.name ?: "",
            userId = currentUserProfile?.id ?: "",
            userName = currentUserProfile?.name ?: "",
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun labInstrumentToJson(inst: LabInstrumentEntity): JSONObject {
        return JSONObject()
            .put("id", inst.id)
            .put("company_id", targetCompanyId(""))
            .put("name", "${inst.type} - ${inst.model}".trim())
            .put("type", inst.type.ifBlank { "Instrumento" })
            .putText("manufacturer", inst.manufacturer)
            .putText("model", inst.model)
            .putText("serial_number", inst.serialNumber)
            .putText("tag", inst.tag.ifBlank { inst.assetNumber.ifBlank { inst.serialNumber } })
            .put("calibration_cert_number", inst.calibrationCertNumber)
            .put("calibration_date", isoDate(inst.calibrationDate))
            .put("calibration_expiry_date", isoDate(inst.calibrationExpiryDate.ifBlank { inst.calibrationDueDate }))
            .putText("calibration_lab", inst.calibrationLab)
            .putText("resolution", inst.measurementRange)
            .put("operational_status", if (inst.active) "operacional" else "inativo")
    }

    private fun jsonToLabInstrument(json: JSONObject): LabInstrumentEntity {
        val status = str(json, "operational_status", "operacional").lowercase()
        return LabInstrumentEntity(
            id = str(json, "id", UUID.randomUUID().toString()),
            tag = str(json, "tag"),
            type = str(json, "type", "Instrumento"),
            model = str(json, "model"),
            manufacturer = str(json, "manufacturer"),
            serialNumber = str(json, "serial_number"),
            assetNumber = str(json, "tag"),
            measurementRange = str(json, "resolution"),
            calibrationCertNumber = str(json, "calibration_cert_number"),
            calibrationDate = appDate(json, "calibration_date"),
            calibrationExpiryDate = appDate(json, "calibration_expiry_date"),
            calibrationLab = str(json, "calibration_lab"),
            active = status == "operacional" || status == "ativo",
            syncStatus = SyncStatus.SYNCED
        )
    }
}
