package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClientEntity
import com.example.data.model.CompanyEntity
import com.example.data.model.EquipmentEntity
import com.example.data.model.LabInstrumentEntity
import com.example.data.model.NormCriterionEntity
import com.example.data.model.NumberBlockEntity
import com.example.data.model.PendingDeleteEntity
import com.example.data.model.SyncStatus
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.TestRecordEntity
import com.example.data.model.UserEntity
import com.example.data.seed.SeedData
import com.example.data.service.SupabaseConnectionStatus
import com.example.data.service.SupabaseService
import com.example.data.service.SupabaseSyncResult
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.util.CompanyLabInfo
import com.example.util.PasswordHasher
import com.example.util.ValidationPortal
import org.json.JSONObject
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DielectricRepository(
    private val database: AppDatabase,
    /** Necessário para ler fotos/assinaturas (content://) e enviá-las ao Storage */
    private val appContext: Context? = null
) {

    val allCompanies: Flow<List<CompanyEntity>> = database.companyDao().getAllCompanies()
    val allUsers: Flow<List<UserEntity>> = database.userDao().getAllUsers()
    val allClients: Flow<List<ClientEntity>> = database.clientDao().getAllClients()
    val allEquipment: Flow<List<EquipmentEntity>> = database.equipmentDao().getAllEquipment()
    // OS do módulo SPDA da plataforma (já baixadas antes desta versão) ficam ocultas: não são apagadas do aparelho
    val allServiceOrders: Flow<List<ServiceOrderEntity>> = database.serviceOrderDao().getAllServiceOrders()
        .map { list -> list.filterNot { SupabaseService.isSpdaDescription(it.description) } }
    val allTests: Flow<List<TestRecordEntity>> = database.testRecordDao().getAllTests()
    val allInstruments: Flow<List<LabInstrumentEntity>> = database.labInstrumentDao().getAllInstruments()
    val allNorms: Flow<List<NormCriterionEntity>> = database.normDao().getAllNorms()
    val allAuditLogs: Flow<List<AuditLogEntity>> = database.auditLogDao().getAllAuditLogs()
    val appSettings: Flow<AppSettingEntity?> = database.settingsDao().getSettings()

    suspend fun clearLocalBusinessData() = withContext(Dispatchers.IO) {
        database.clientDao().deleteAll()
        database.equipmentDao().deleteAll()
        database.serviceOrderDao().deleteAll()
        database.testRecordDao().deleteAll()
        database.labInstrumentDao().deleteAll()
    }

    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        clearLocalBusinessData()
        database.companyDao().deleteAll()
        database.userDao().deleteAll()
    }

    suspend fun seedDatabaseIfEmpty(defaultSignaturePath: String = "") = withContext(Dispatchers.IO) {
        val currentSetting = database.settingsDao().getSettingsOnce()
        if (currentSetting == null) {
            val initialSetting = AppSettingEntity(
                id = "company_singleton",
                name = "",
                legalName = "",
                cnpj = "",
                creaCompanyRegister = "",
                address = "",
                phone = "",
                email = "",
                website = "",
                defaultTechnicianSignatureImage = defaultSignaturePath,
                technicalResponsibleSignatureImage = defaultSignaturePath,
                supabaseUrl = SupabaseService.DEFAULT_URL,
                supabaseAnonKey = SupabaseService.DEFAULT_ANON_KEY,
                supabaseAutoSync = true
            )
            database.settingsDao().saveSettings(initialSetting)
        }

        // Keep official technical reference standards (ABNT NBR 16295, ASTM D120, etc.) for test calculations
        val existingNorms = database.normDao().getAllNorms().first()
        if (existingNorms.isEmpty()) {
            database.normDao().insertAll(SeedData.standardNorms)
        }

        // Os dados locais NÃO são apagados na abertura: ensaios feitos offline e
        // ainda não enviados ficariam perdidos. O download no login os atualiza.

        // Fetch registered users and companies from Supabase cloud database if authenticated or using service role
        try {
            val (url, key, _) = getSupabaseCredentials()
            val hasAuth = !SupabaseService.currentAuthToken.isNullOrBlank()
            val isSvc = SupabaseService.isServiceRole(key)
            if (hasAuth || isSvc) {
                saveRemoteUsers(SupabaseService.fetchUsers(url, key))
                val remoteCompanies = SupabaseService.fetchCompanies(url, key)
                if (remoteCompanies.isNotEmpty()) {
                    database.companyDao().insertAll(remoteCompanies)
                }
            }
        } catch (e: Exception) {
            Log.w("DielectricRepo", "Initial Supabase user/company sync: ${e.message}")
        }

        // Critérios normativos (os mesmos da web): última lista baixada ou os oficiais embutidos
        appContext?.let { com.example.domain.NormsCatalog.load(it) }

        // Endereço do QR Code configurado no Portal Público de Validação
        database.settingsDao().getSettingsOnce()?.let {
            ValidationPortal.baseUrl = CompanyLabInfo.str(CompanyLabInfo.parse(it.labInfoJson), "validationBaseUrl")
        }

        // Usuários de demonstração (SeedData) não são mais criados: o primeiro
        // acesso exige login online no Supabase Auth.
    }

    /** Grava usuários baixados mantendo a senha protegida já guardada no aparelho. */
    private suspend fun saveRemoteUsers(remoteUsers: List<UserEntity>) {
        if (remoteUsers.isEmpty()) return
        val merged = remoteUsers.map { remote ->
            val local = database.userDao().getUserById(remote.id)
            if (local != null && PasswordHasher.isHashed(local.password)) remote.copy(password = local.password) else remote
        }
        database.userDao().insertAll(merged)
    }

    suspend fun resetToSeedData(defaultSignaturePath: String = "") = withContext(Dispatchers.IO) {
        // Resynchronize completely from the Supabase database
        pullFromSupabase()
    }

    // Companies
    suspend fun saveCompany(company: CompanyEntity) = withContext(Dispatchers.IO) {
        database.companyDao().insertCompany(company)
        addAuditLog("CADASTRO", "Company", company.id, "Empresa cadastrada/atualizada: ${company.name}")
        autoSyncAsync { url, key -> SupabaseService.upsertCompanies(url, key, listOf(company)) }
    }

    suspend fun deleteCompany(id: String) = withContext(Dispatchers.IO) {
        database.companyDao().deleteCompanyById(id)
        addAuditLog("EXCLUSAO", "Company", id, "Empresa excluída: $id")
        autoSyncAsync { url, key -> SupabaseService.deleteEntity(url, key, "companies", id) }
    }

    suspend fun getCompanyById(id: String): CompanyEntity? = withContext(Dispatchers.IO) {
        database.companyDao().getCompanyById(id)
    }

    // Users
    suspend fun saveUser(user: UserEntity) = withContext(Dispatchers.IO) {
        // A senha nunca fica em texto no aparelho (e nunca é enviada à nuvem)
        val stored = when {
            user.password.isBlank() || PasswordHasher.isHashed(user.password) -> user
            else -> user.copy(password = PasswordHasher.hash(user.password))
        }
        database.userDao().insertUser(stored)
        addAuditLog("CADASTRO", "User", user.id, "Usuário cadastrado/atualizado: ${user.name} (${user.email})")
        autoSyncAsync { url, key -> SupabaseService.upsertUsers(url, key, listOf(user)) }
    }

    suspend fun deleteUser(id: String) = withContext(Dispatchers.IO) {
        database.userDao().deleteUserById(id)
        addAuditLog("EXCLUSAO", "User", id, "Usuário excluído: $id")
        autoSyncAsync { url, key -> SupabaseService.deleteEntity(url, key, "users", id) }
    }

    suspend fun getUserById(id: String): UserEntity? = withContext(Dispatchers.IO) {
        database.userDao().getUserById(id)
    }

    suspend fun getUserByEmail(email: String): UserEntity? = withContext(Dispatchers.IO) {
        database.userDao().getUserByEmail(email.trim().lowercase())
    }

    suspend fun authenticateUser(identifier: String, pass: String): UserEntity? = withContext(Dispatchers.IO) {
        val cleanIdentifier = identifier.trim()
        val cleanPass = pass.trim()
        if (cleanIdentifier.isBlank() || cleanPass.isBlank()) return@withContext null

        val (url, key, _) = getSupabaseCredentials()

        // 1. Supabase Auth (online). Guarda a senha protegida (PBKDF2) para o login offline.
        val authRes = try {
            SupabaseService.signInWithSupabaseAuth(url, key, cleanIdentifier, cleanPass)
        } catch (e: Exception) {
            Log.w("DielectricRepo", "Supabase Auth login attempt: ${e.message}")
            null
        }
        if (authRes != null && authRes.success && authRes.user != null) {
            val user = authRes.user.copy(password = PasswordHasher.hash(cleanPass))
            database.userDao().insertUser(user)
            if (authRes.company != null) {
                database.companyDao().insertCompany(authRes.company)
            }
            SupabaseService.currentUserProfile = user
            return@withContext user
        }
        // Servidor respondeu "senha incorreta": não tenta o login offline
        // (uma senha antiga ou trocada pelo administrador não pode continuar valendo)
        if (authRes != null && !authRes.networkError) return@withContext null

        // 2. Sem internet: só quem já entrou online neste aparelho, pelo e-mail
        // (ou nome de usuário) exato e com a mesma senha
        val wanted = cleanIdentifier.lowercase()
        val localUser = database.userDao().getAllUsers().first().firstOrNull { u ->
            u.email.equals(wanted, ignoreCase = true) ||
                (!wanted.contains("@") && u.email.substringBefore("@").equals(wanted, ignoreCase = true))
        } ?: return@withContext null

        if (PasswordHasher.isHashed(localUser.password) && PasswordHasher.verify(cleanPass, localUser.password)) {
            return@withContext localUser
        }
        null
    }

    suspend fun validateCertificateRemote(code: String): Pair<TestRecordEntity?, CompanyEntity?>? = withContext(Dispatchers.IO) {
        val (url, key, _) = getSupabaseCredentials()
        SupabaseService.validateCertificateViaRpc(url, key, code)
    }

    // -------------------------------------------------------------
    // Controle de envio: cada gravação fica PENDING até chegar ao Supabase
    // -------------------------------------------------------------

    private fun currentTargets(): Triple<String?, String?, UserEntity?> {
        val companyId = SupabaseService.currentCompanyProfile?.id ?: SupabaseService.currentUserProfile?.companyId
        val companyName = SupabaseService.currentCompanyProfile?.name ?: SupabaseService.currentUserProfile?.companyName
        return Triple(companyId, companyName, SupabaseService.currentUserProfile)
    }

    private fun pick(target: String?, current: String, placeholder: String, fallback: String = placeholder): String = when {
        !target.isNullOrBlank() -> target
        current.isNotBlank() && current != placeholder -> current
        else -> fallback
    }

    private suspend fun markSynced(table: String, ids: List<String>) {
        if (ids.isEmpty()) return
        ids.chunked(500).forEach { chunk ->
            when (table) {
                "clients" -> database.clientDao().setSyncStatus(chunk, SyncStatus.SYNCED)
                "equipment" -> database.equipmentDao().setSyncStatus(chunk, SyncStatus.SYNCED)
                "service_orders" -> database.serviceOrderDao().setSyncStatus(chunk, SyncStatus.SYNCED)
                "test_records" -> database.testRecordDao().setSyncStatus(chunk, SyncStatus.SYNCED)
                "lab_instruments" -> database.labInstrumentDao().setSyncStatus(chunk, SyncStatus.SYNCED)
            }
        }
    }

    /** Exclusão local + registro na fila (enviada agora se houver internet, senão no próximo envio). */
    private suspend fun queueDelete(table: String, ids: List<String>) {
        ids.forEach { id -> database.pendingDeleteDao().insert(PendingDeleteEntity("$table:$id", table, id)) }
        autoSyncAsync { url, key -> flushPendingDeletes(url, key) }
    }

    /** Envia as exclusões da fila; devolve a lista de erros. */
    private suspend fun flushPendingDeletes(url: String, key: String): List<String> {
        val errors = mutableListOf<String>()
        for (item in database.pendingDeleteDao().getAll()) {
            val res = SupabaseService.deleteEntity(url, key, item.tableName, item.entityId)
            if (res.isSuccess) database.pendingDeleteDao().deleteByKey(item.key)
            else errors.add("Erro ao excluir ${item.tableName}/${item.entityId}: ${res.exceptionOrNull()?.message}")
        }
        return errors
    }

    // -------------------------------------------------------------
    // Fotos e assinaturas -> Storage (bucket jvm-evidencias)
    // -------------------------------------------------------------

    /** Leitura de um arquivo de mídia do aparelho: bytes, ou o motivo da falha. */
    private class MediaRead(val bytes: ByteArray?, val error: String?, val missing: Boolean = false)

    private fun readLocalMedia(uri: String): MediaRead = try {
        if (uri.startsWith("/")) {
            val file = java.io.File(uri)
            if (!file.exists()) MediaRead(null, "arquivo não existe mais no aparelho", missing = true)
            else MediaRead(file.readBytes(), null)
        } else {
            val bytes = appContext?.contentResolver?.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            if (bytes == null) MediaRead(null, "arquivo não encontrado", missing = true) else MediaRead(bytes, null)
        }
    } catch (e: java.io.FileNotFoundException) {
        Log.w("DielectricRepo", "Mídia inacessível $uri: ${e.message}")
        val denied = e.message?.contains("EACCES", ignoreCase = true) == true || e.message?.contains("denied", ignoreCase = true) == true
        if (denied) MediaRead(null, "sem permissão para ler a foto (app reinstalado?)")
        else MediaRead(null, "arquivo não encontrado", missing = true)
    } catch (e: Exception) {
        Log.w("DielectricRepo", "Não foi possível ler a mídia $uri: ${e.message}")
        MediaRead(null, e.message ?: "erro de leitura")
    }

    /**
     * Fotos de câmera chegam a 8–15 MB (o bucket aceita até 10 MB): reduz para no
     * máximo 2048 px em JPEG 85%, aplicando a rotação EXIF da câmera. Imagens
     * pequenas (assinaturas, PNG) seguem como estão.
     */
    private fun prepareImage(uri: String, bytes: ByteArray): Triple<ByteArray, String, String> {
        val isPng = uri.lowercase().endsWith(".png") || (bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte())
        val original = Triple(bytes, if (isPng) "image/png" else "image/jpeg", if (isPng) "png" else "jpg")
        val maxSide = 2048
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val largest = maxOf(bounds.outWidth, bounds.outHeight)
            if (largest <= 0) return original
            if (isPng && bytes.size <= 2_000_000) return original
            if (!isPng && largest <= maxSide && bytes.size <= 1_500_000) return original

            var sample = 1
            while (largest / (sample * 2) >= maxSide) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return original
            val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
            val rotation = if (isPng) 0f else try {
                when (ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) { 0f }
            val matrix = Matrix().apply {
                postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val stream = java.io.ByteArrayOutputStream()
            output.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            if (output !== decoded) output.recycle()
            decoded.recycle()
            return Triple(stream.toByteArray(), "image/jpeg", "jpg")
        } catch (e: Throwable) {
            Log.w("DielectricRepo", "Redução da imagem $uri falhou: ${e.message}")
            return original
        }
    }

    /**
     * Envia ao Storage as fotos/assinaturas do laudo que ainda são arquivos do
     * aparelho e guarda as URLs em remoteMedia. Devolve o laudo atualizado e se
     * TODAS as mídias enviáveis já estão na nuvem (senão o laudo continua pendente
     * e é reenviado na próxima sincronização). Falhas vão para [errors].
     */
    private suspend fun uploadTestMedia(
        url: String,
        key: String,
        test: TestRecordEntity,
        errors: MutableList<String>?
    ): Pair<TestRecordEntity, Boolean> {
        val localUris = (SupabaseService.parsePhotoEntries(test.photoUris).map { it.second } +
            listOf(test.technicianSignature, test.techResponsibleSignature))
            .filter { it.isNotBlank() && SupabaseService.isLocalMedia(it) }
            .distinct()
        if (localUris.isEmpty()) return test to true
        val remote = SupabaseService.parseRemoteMedia(test.remoteMedia).toMutableMap()
        var complete = true
        val folder = "${SupabaseService.storageFolder(test.companyId)}/${test.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
        val label = test.reportNumber.ifBlank { test.testNumber }
        for (uri in localUris) {
            if (remote.containsKey(uri)) continue
            val read = readLocalMedia(uri)
            val raw = read.bytes
            if (raw == null) {
                // arquivo apagado do aparelho: não há como enviar, não segura o laudo pendente
                if (!read.missing) complete = false
                errors?.add("Laudo $label: foto não enviada (${read.error}) - ${uri.substringAfterLast('/')}")
                continue
            }
            val (bytes, mime, ext) = prepareImage(uri, raw)
            val name = "apk-" + Integer.toHexString(uri.hashCode()).padStart(8, '0') + ".$ext"
            val upload = SupabaseService.uploadEvidence(url, key, "$folder/$name", bytes, mime)
            val publicUrl = upload.url
            if (publicUrl != null) {
                remote[uri] = publicUrl
            } else {
                complete = false
                errors?.add("Laudo $label: foto não enviada (${upload.error})")
            }
        }
        val json = org.json.JSONObject().apply { remote.forEach { (k, v) -> put(k, v) } }.toString()
        if (json == test.remoteMedia) return test to complete
        val updated = test.copy(remoteMedia = json)
        // grava só remoteMedia, sem mexer no syncStatus atual
        database.testRecordDao().getTestById(test.id)?.let { current ->
            database.testRecordDao().insertTest(current.copy(remoteMedia = json))
        }
        return updated to complete
    }

    /** Foto/assinatura que ainda é arquivo do aparelho (existente) e não tem URL na nuvem. */
    private fun hasUnsentMedia(test: TestRecordEntity): Boolean {
        val remote = SupabaseService.parseRemoteMedia(test.remoteMedia)
        return (SupabaseService.parsePhotoEntries(test.photoUris).map { it.second } +
            listOf(test.technicianSignature, test.techResponsibleSignature))
            .any { uri ->
                uri.isNotBlank() && SupabaseService.isLocalMedia(uri) && !remote.containsKey(uri) &&
                    (!uri.startsWith("/") || java.io.File(uri).exists())
            }
    }

    /** Envia laudos (com mídias) e marca como sincronizados os que subiram completos. */
    private suspend fun pushTests(
        url: String,
        key: String,
        tests: List<TestRecordEntity>,
        force: Boolean,
        errors: MutableList<String>? = null
    ): Result<Int> {
        if (tests.isEmpty()) return Result.success(0)
        // garante um token válido antes do Storage (que responde 400 com token vencido)
        if (!SupabaseService.currentRefreshToken.isNullOrBlank()) SupabaseService.refreshAuthToken(url, key)
        val prepared = tests.map { uploadTestMedia(url, key, it, errors) }
        val res = SupabaseService.upsertTestRecords(url, key, prepared.map { it.first }, force)
        if (res.isSuccess) markSynced("test_records", prepared.filter { it.second }.map { it.first.id })
        return res
    }

    // -------------------------------------------------------------
    // Numeração (ENS/LAU/CERT/OS) reservada no servidor
    // -------------------------------------------------------------

    private val numberLock = kotlinx.coroutines.sync.Mutex()

    private fun currentPeriod(): String = SimpleDateFormat("yyMM", Locale.US).format(Date())

    /** Mesma regra de jvm_number_sequence(): número após o período, ou dígitos finais. */
    private fun numberSequence(number: String, period: String): Long? {
        val afterPeriod = Regex("(?:20$period|$period)[-_]?(\\d{1,9})").find(number)?.groupValues?.get(1)
        val trailing = Regex("(\\d{1,9})$").find(number)?.groupValues?.get(1)
        return (afterPeriod ?: trailing)?.toLongOrNull()
    }

    private suspend fun localNumbers(kind: String): List<String> = when (kind) {
        "os" -> database.serviceOrderDao().getAllServiceOrders().first().map { it.osNumber }
        "test" -> database.testRecordDao().getAllTests().first().map { it.testNumber }
        "report" -> database.testRecordDao().getAllTests().first().map { it.reportNumber }
        else -> database.testRecordDao().getAllTests().first().map { it.certificateNumber }
    }

    /** Garante uma faixa reservada com pelo menos [minimum] números livres (quando online). */
    private suspend fun ensureNumberBlock(kind: String, minimum: Int = 5, quantity: Int = 20) {
        val block = database.numberBlockDao().get(kind)
        if (block != null && block.endValue - block.nextValue + 1 >= minimum) return
        val (url, key, _) = getSupabaseCredentials()
        val period = currentPeriod()
        val localMax = localNumbers(kind).mapNotNull { numberSequence(it, period) }.maxOrNull() ?: 0L
        val reserved = SupabaseService.reserveNumbers(url, key, kind, period, quantity, localMax) ?: return
        database.numberBlockDao().save(NumberBlockEntity(kind, reserved.first, reserved.second))
    }

    /** Reserva antecipada (ao entrar/sincronizar) para o uso sem internet. */
    suspend fun refillNumberBlocks() = withContext(Dispatchers.IO) {
        numberLock.withLock {
            listOf("test", "report", "certificate", "os").forEach { kind ->
                try { ensureNumberBlock(kind) } catch (e: Exception) { Log.w("DielectricRepo", "Reserva de números ($kind): ${e.message}") }
            }
        }
    }

    /**
     * Próximo número do tipo: da faixa reservada no servidor (sem repetir números
     * de outros aparelhos/web). Sem faixa e sem internet, segue a contagem local.
     */
    private suspend fun nextNumber(kind: String, prefix: String): String = numberLock.withLock {
        val period = currentPeriod()
        val localMax = localNumbers(kind).mapNotNull { numberSequence(it, period) }.maxOrNull() ?: 0L
        try { ensureNumberBlock(kind, minimum = 1) } catch (_: Exception) {}
        val block = database.numberBlockDao().get(kind)
        val value = if (block != null && block.nextValue <= block.endValue) {
            database.numberBlockDao().save(block.copy(nextValue = block.nextValue + 1))
            block.nextValue
        } else {
            Log.w("DielectricRepo", "Numeração $kind sem faixa reservada (offline): usando contagem local")
            localMax + 1
        }
        // prefixo como na web: "LAU-" ou "LAUDO-JVM" (o hífen final é acrescentado)
        val p = prefix.trim().ifBlank { "DOC" }.let { if (it.endsWith("-")) it else "$it-" }
        "$p$period-${String.format(Locale.US, "%04d", value)}"
    }

    // Clients
    suspend fun saveClient(client: ClientEntity) = withContext(Dispatchers.IO) {
        val (companyId, companyName, _) = currentTargets()
        val enriched = client.copy(
            companyId = pick(companyId, client.companyId, "comp-jvm-01"),
            companyName = pick(companyName, client.companyName, "JVM Engenharia"),
            syncStatus = SyncStatus.PENDING
        )
        database.clientDao().insertClient(enriched)
        addAuditLog("CADASTRO", "Client", enriched.id, "Cliente cadastrado/atualizado: ${enriched.razaoSocial}")
        autoSyncAsync { url, key ->
            SupabaseService.upsertClients(url, key, listOf(enriched)).onSuccess { markSynced("clients", listOf(enriched.id)) }
        }
    }

    suspend fun deleteClient(id: String) = withContext(Dispatchers.IO) {
        database.clientDao().deleteClientById(id)
        addAuditLog("EXCLUSAO", "Client", id, "Cliente excluído: $id")
        queueDelete("clients", listOf(id))
    }

    // Equipment
    suspend fun saveEquipment(equipment: EquipmentEntity) = withContext(Dispatchers.IO) {
        val (companyId, companyName, user) = currentTargets()
        val enriched = equipment.copy(
            companyId = pick(companyId, equipment.companyId, "comp-jvm-01"),
            companyName = pick(companyName, equipment.companyName, "JVM Engenharia"),
            userId = pick(user?.id, equipment.userId, "user-jvm-01"),
            userName = pick(user?.name, equipment.userName, "Eng. João Carlos"),
            syncStatus = SyncStatus.PENDING
        )
        database.equipmentDao().insertEquipment(enriched)
        addAuditLog("CADASTRO", "Equipment", enriched.id, "Equipamento cadastrado/atualizado: ${enriched.tag}")
        autoSyncAsync { url, key ->
            SupabaseService.upsertEquipment(url, key, listOf(enriched)).onSuccess { markSynced("equipment", listOf(enriched.id)) }
        }
    }

    suspend fun deleteEquipment(id: String) = withContext(Dispatchers.IO) {
        database.equipmentDao().deleteEquipmentById(id)
        addAuditLog("EXCLUSAO", "Equipment", id, "Equipamento excluído: $id")
        queueDelete("equipment", listOf(id))
    }

    suspend fun deleteMultipleEquipment(ids: List<String>) = withContext(Dispatchers.IO) {
        database.equipmentDao().deleteMultipleEquipment(ids)
        addAuditLog("EXCLUSAO_EM_LOTE", "Equipment", ids.joinToString(","), "${ids.size} equipamentos excluídos em lote")
        queueDelete("equipment", ids)
    }

    // Service Orders
    suspend fun saveServiceOrder(order: ServiceOrderEntity) = withContext(Dispatchers.IO) {
        val (companyId, companyName, user) = currentTargets()
        val enriched = order.copy(
            companyId = pick(companyId, order.companyId, "comp-jvm-01"),
            companyName = pick(companyName, order.companyName, "JVM Engenharia"),
            technicianId = pick(user?.id, order.technicianId, "user-jvm-01", ""),
            technicianName = pick(user?.name, order.technicianName, "Analista Executor", "Engenheiro Responsável"),
            syncStatus = SyncStatus.PENDING
        )
        database.serviceOrderDao().insertServiceOrder(enriched)
        addAuditLog("CADASTRO", "ServiceOrder", enriched.id, "Ordem de Serviço salva: ${enriched.osNumber}")
        autoSyncAsync { url, key ->
            SupabaseService.upsertServiceOrders(url, key, listOf(enriched)).onSuccess { markSynced("service_orders", listOf(enriched.id)) }
        }
    }

    suspend fun deleteServiceOrder(id: String) = withContext(Dispatchers.IO) {
        database.serviceOrderDao().deleteServiceOrderById(id)
        addAuditLog("EXCLUSAO", "ServiceOrder", id, "Ordem de Serviço excluída: $id")
        queueDelete("service_orders", listOf(id))
    }

    // Test Records
    suspend fun saveTestRecord(test: TestRecordEntity) = withContext(Dispatchers.IO) {
        val (companyId, companyName, user) = currentTargets()
        val enriched = test.copy(
            companyId = pick(companyId, test.companyId, "comp-jvm-01"),
            companyName = pick(companyName, test.companyName, "JVM Engenharia"),
            userId = pick(user?.id, test.userId, "user-jvm-01"),
            userName = pick(user?.name, test.userName, "Eng. João Carlos"),
            technicianId = pick(user?.id, test.technicianId, "user-jvm-01", ""),
            technicianName = pick(user?.name, test.technicianName, "Engenheiro"),
            // registro do executor como informado no ensaio (em branco = não aparece no laudo)
            technicianCftOrCrea = test.technicianCftOrCrea.trim(),
            // mantém as URLs das mídias já enviadas ao Storage
            remoteMedia = (database.testRecordDao().getTestById(test.id)?.remoteMedia ?: test.remoteMedia),
            syncStatus = SyncStatus.PENDING
        )
        database.testRecordDao().insertTest(enriched)

        // Update equipment status and dates
        val existingEq = database.equipmentDao().getEquipmentById(enriched.equipmentId)
        if (existingEq != null) {
            val updatedStatus = if (enriched.result == "APROVADO") "EM_USO" else "REPROVADO"
            val updatedEq = existingEq.copy(
                status = updatedStatus,
                lastTestDate = enriched.testDate,
                nextTestDueDate = enriched.retestDueDate,
                notes = "Último Laudo: ${enriched.reportNumber} ($updatedStatus)",
                companyId = enriched.companyId,
                companyName = enriched.companyName,
                userId = enriched.userId,
                userName = enriched.userName,
                syncStatus = SyncStatus.PENDING
            )
            database.equipmentDao().insertEquipment(updatedEq)
        }
        val syncedEq = database.equipmentDao().getEquipmentById(enriched.equipmentId)

        addAuditLog("EMISSAO_LAUDO", "TestRecord", enriched.id, "Laudo ${enriched.reportNumber} registrado: ${enriched.result}")
        // Equipamento antes do laudo: test_records.equipment_id referencia equipment.id
        autoSyncAsync { url, key ->
            if (syncedEq != null) {
                SupabaseService.upsertEquipment(url, key, listOf(syncedEq)).onSuccess { markSynced("equipment", listOf(syncedEq.id)) }
            }
            pushTests(url, key, listOf(enriched), force = true)
        }
    }

    suspend fun deleteTest(id: String) = withContext(Dispatchers.IO) {
        database.testRecordDao().deleteTestById(id)
        addAuditLog("EXCLUSAO", "TestRecord", id, "Laudo excluído: $id")
        queueDelete("test_records", listOf(id))
    }

    suspend fun deleteTestRecord(id: String) = deleteTest(id)

    suspend fun deleteMultipleTests(ids: List<String>) = withContext(Dispatchers.IO) {
        database.testRecordDao().deleteMultipleTests(ids)
        addAuditLog("EXCLUSAO_EM_LOTE", "TestRecord", ids.joinToString(","), "${ids.size} laudos excluídos em lote")
        queueDelete("test_records", ids)
    }

    suspend fun getTestByValidationCode(code: String): TestRecordEntity? = withContext(Dispatchers.IO) {
        database.testRecordDao().getTestByValidationCode(code)
    }

    // Lab Instruments
    suspend fun saveInstrument(instrument: LabInstrumentEntity) = withContext(Dispatchers.IO) {
        val pending = instrument.copy(syncStatus = SyncStatus.PENDING)
        database.labInstrumentDao().insertInstrument(pending)
        addAuditLog("CADASTRO", "LabInstrument", pending.id, "Instrumento salvo: ${pending.type} - ${pending.model}")
        autoSyncAsync { url, key ->
            SupabaseService.upsertLabInstruments(url, key, listOf(pending)).onSuccess { markSynced("lab_instruments", listOf(pending.id)) }
        }
    }

    suspend fun deleteInstrument(id: String) = withContext(Dispatchers.IO) {
        database.labInstrumentDao().deleteInstrumentById(id)
        addAuditLog("EXCLUSAO", "LabInstrument", id, "Instrumento excluído: $id")
        queueDelete("lab_instruments", listOf(id))
    }

    // Norms
    suspend fun saveNorm(norm: NormCriterionEntity) = withContext(Dispatchers.IO) {
        database.normDao().insertNorm(norm)
        addAuditLog("ALTERACAO_CRITERIOS", "Norm", norm.id, "Norma salva: ${norm.code} - ${norm.title}")
    }

    suspend fun deleteNorm(id: String) = withContext(Dispatchers.IO) {
        database.normDao().deleteNormById(id)
        addAuditLog("EXCLUSAO", "Norm", id, "Norma excluída: $id")
    }

    // Settings
    /** Administrador/RT da empresa: mesmos perfis que acessam as Configurações na web. */
    fun isCompanyManager(): Boolean {
        val role = SupabaseService.currentUserProfile?.role?.trim()?.lowercase() ?: return false
        return role == "admin" || role == "responsavel_tecnico"
    }

    suspend fun saveSettings(settings: AppSettingEntity) = withContext(Dispatchers.IO) {
        if (isCompanyManager() && SupabaseService.currentCompanyProfile != null) {
            // dados da empresa também vão para a plataforma web (companies.lab_info)
            saveCompanySettings(settings, CompanyLabInfo.fromSettings(settings))
        } else {
            database.settingsDao().saveSettings(settings)
            addAuditLog("ALTERACAO", "Settings", settings.id, "Configurações do aparelho atualizadas")
        }
    }

    /** Configurações da empresa no formato da web: grava, e envia à nuvem (administrador/RT). */
    suspend fun saveCompanySettings(settings: AppSettingEntity, labInfo: JSONObject) = withContext(Dispatchers.IO) {
        val manager = isCompanyManager()
        val applied = CompanyLabInfo.applyToSettings(settings, labInfo, pending = manager)
        database.settingsDao().saveSettings(applied)
        addAuditLog("ALTERACAO", "Settings", settings.id, "Configurações da empresa atualizadas")
        if (manager) autoSyncAsync { url, key -> pushCompanySettings(url, key) }
    }

    /** Envia as configurações pendentes da empresa. Devolve o erro, ou null. */
    private suspend fun pushCompanySettings(url: String, key: String): String? {
        val settings = database.settingsDao().getSettingsOnce() ?: return null
        if (!CompanyLabInfo.isPending(settings)) return null
        val companyId = SupabaseService.currentCompanyProfile?.id ?: SupabaseService.currentUserProfile?.companyId
        if (companyId.isNullOrBlank() || !isCompanyManager()) return null
        val lab = CompanyLabInfo.parse(settings.labInfoJson)
        val res = SupabaseService.updateCompanyLabInfo(url, key, companyId, CompanyLabInfo.forCloud(lab))
        return if (res.isSuccess) {
            database.settingsDao().getSettingsOnce()?.let { current ->
                // só limpa a marca se nada mudou durante o envio
                if (current.labInfoJson == settings.labInfoJson) {
                    database.settingsDao().saveSettings(CompanyLabInfo.applyToSettings(current, lab, pending = false))
                }
            }
            null
        } else {
            "Erro ao enviar configurações da empresa: ${res.exceptionOrNull()?.message}"
        }
    }

    /** Baixa as configurações da empresa feitas na web (exceto se houver alteração local pendente). */
    private suspend fun pullCompanySettings(url: String, key: String) {
        val settings = database.settingsDao().getSettingsOnce() ?: return
        if (CompanyLabInfo.isPending(settings)) return
        val companyId = SupabaseService.currentCompanyProfile?.id ?: SupabaseService.currentUserProfile?.companyId ?: return
        val remote = SupabaseService.fetchCompanyLabInfo(url, key, companyId) ?: return
        val merged = CompanyLabInfo.fromSettings(settings)
        val keys = remote.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = remote.get(k)
            if (v is JSONObject && merged.optJSONObject(k) != null) {
                val inner = merged.getJSONObject(k)
                val ik = v.keys()
                while (ik.hasNext()) { val x = ik.next(); inner.put(x, v.get(x)) }
            } else merged.put(k, v)
        }
        database.settingsDao().saveSettings(CompanyLabInfo.applyToSettings(settings, merged, pending = false))
    }

    /** Prefixo de numeração configurado (Parâmetros de Emissão), como na web. */
    private suspend fun emissionPrefix(key: String, default: String): String {
        val lab = CompanyLabInfo.parse(database.settingsDao().getSettingsOnce()?.labInfoJson)
        return CompanyLabInfo.str(CompanyLabInfo.emission(lab), key).trim().ifBlank { default }
    }

    // Audit logs
    suspend fun addAuditLog(action: String, entityType: String, entityId: String, description: String) = withContext(Dispatchers.IO) {
        val nowStr = com.example.util.DateUtils.now()
        val log = AuditLogEntity(
            action = action,
            userName = "Operador do Sistema",
            userRole = "responsavel_tecnico",
            timestamp = nowStr,
            dateTime = nowStr,
            entityType = entityType,
            entityId = entityId,
            details = description,
            description = description
        )
        database.auditLogDao().insertAuditLog(log)
    }

    // Sequence Number Generators
    // Numeração contínua por empresa, reservada no servidor (jvm_reserve_numbers),
    // no mesmo formato da web: ENS-2610-0012, LAU-..., CERT-..., OS-...
    suspend fun generateNextTestNumber(): String = withContext(Dispatchers.IO) { nextNumber("test", "ENS") }

    suspend fun generateNextReportNumber(): String = withContext(Dispatchers.IO) { nextNumber("report", emissionPrefix("reportPrefix", "LAU-")) }

    suspend fun generateNextCertificateNumber(): String = withContext(Dispatchers.IO) { nextNumber("certificate", emissionPrefix("certificatePrefix", "CERT-")) }

    suspend fun generateNextOSNumber(): String = withContext(Dispatchers.IO) { nextNumber("os", "OS") }

    fun generateValidationCode(): String {
        val yearMonth = SimpleDateFormat("yyMM", Locale.getDefault()).format(Date())
        // 10 caracteres aleatórios: o código impresso no QR Code é a chave da consulta pública
        val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(10).uppercase(Locale.US)
        return "VAL-JVM-$yearMonth-$randomSuffix"
    }

    // -------------------------------------------------------------
    // Supabase Cloud Integration & Synchronization
    // -------------------------------------------------------------

    private suspend fun getSupabaseCredentials(): Triple<String, String, Boolean> {
        val settings = database.settingsDao().getSettingsOnce()
        val url = settings?.supabaseUrl?.ifBlank { SupabaseService.DEFAULT_URL } ?: SupabaseService.DEFAULT_URL
        val key = settings?.supabaseAnonKey?.ifBlank { SupabaseService.DEFAULT_ANON_KEY } ?: SupabaseService.DEFAULT_ANON_KEY
        val autoSync = settings?.supabaseAutoSync ?: true
        return Triple(url, key, autoSync)
    }

    private fun autoSyncAsync(block: suspend (url: String, key: String) -> Any?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (url, key, autoSync) = getSupabaseCredentials()
                val hasAuth = !SupabaseService.currentAuthToken.isNullOrBlank()
                val isSvc = SupabaseService.isServiceRole(key)
                if (autoSync && url.isNotBlank() && key.isNotBlank() && (hasAuth || isSvc)) {
                    val result = block(url, key)
                    if (result is Result<*> && result.isFailure) {
                        Log.e("DielectricRepo", "Auto-sync Supabase falhou: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("DielectricRepo", "Auto-sync async error: ${e.message}")
            }
        }
    }

    suspend fun testSupabaseConnection(customUrl: String? = null, customKey: String? = null): SupabaseConnectionStatus = withContext(Dispatchers.IO) {
        val (defUrl, defKey, _) = getSupabaseCredentials()
        val url = customUrl?.ifBlank { defUrl } ?: defUrl
        val key = customKey?.ifBlank { defKey } ?: defKey
        SupabaseService.testConnection(url, key)
    }

    /**
     * Download da nuvem. Registros alterados no aparelho e ainda não enviados
     * (syncStatus != synced) e exclusões ainda na fila NÃO são substituídos.
     */
    suspend fun pullFromSupabase(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        val (url, key, _) = getSupabaseCredentials()
        val hasAuth = !SupabaseService.currentAuthToken.isNullOrBlank()
        val isSvc = SupabaseService.isServiceRole(key)
        if (!hasAuth && !isSvc) {
            return@withContext SupabaseSyncResult(
                success = true,
                message = "Modo local pronto. Para sincronizar as tabelas protegidas da nuvem, conecte com seu login do Supabase Auth ou insira a chave Service Role.",
                errors = emptyList()
            )
        }

        val errors = mutableListOf<String>()
        val queuedDeletes = database.pendingDeleteDao().getAll().map { it.key }.toSet()
        fun keep(table: String, id: String, unsynced: Set<String>) = id !in unsynced && "$table:$id" !in queuedDeletes

        var compCount = 0
        var usersCount = 0
        var clientsCount = 0
        var eqCount = 0
        var ordersCount = 0
        var testsCount = 0
        var instCount = 0

        try {
            val companies = SupabaseService.fetchCompanies(url, key)
            if (companies.isNotEmpty()) database.companyDao().insertAll(companies)
            compCount = companies.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar empresas: ${e.message}")
        }

        // Normas técnicas: mesma lista de critérios da web (oficiais + versões da empresa)
        try {
            val companyId = SupabaseService.currentCompanyProfile?.id ?: SupabaseService.currentUserProfile?.companyId
            val rows = SupabaseService.fetchNormRows(url, key, companyId)
            appContext?.let { com.example.domain.NormsCatalog.applyRemoteRows(it, rows, companyId ?: "") }
        } catch (e: Exception) {
            errors.add("Erro ao baixar normas técnicas: ${e.message}")
        }

        // Configurações da empresa feitas na web (Parâmetros de Emissão, Portal de Validação...)
        try { pullCompanySettings(url, key) } catch (e: Exception) { errors.add("Erro ao baixar configurações da empresa: ${e.message}") }

        try {
            val users = SupabaseService.fetchUsers(url, key)
            saveRemoteUsers(users)
            usersCount = users.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar usuários: ${e.message}")
        }

        try {
            val unsynced = database.clientDao().getUnsyncedIds().toSet()
            val clients = SupabaseService.fetchClients(url, key).filter { keep("clients", it.id, unsynced) }
            if (clients.isNotEmpty()) database.clientDao().insertAll(clients)
            clientsCount = clients.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar clientes: ${e.message}")
        }

        try {
            val unsynced = database.equipmentDao().getUnsyncedIds().toSet()
            val equipment = SupabaseService.fetchEquipment(url, key).filter { keep("equipment", it.id, unsynced) }
            if (equipment.isNotEmpty()) database.equipmentDao().insertAll(equipment)
            eqCount = equipment.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar equipamentos: ${e.message}")
        }

        try {
            val unsynced = database.serviceOrderDao().getUnsyncedIds().toSet()
            val orders = SupabaseService.fetchServiceOrders(url, key).filter { keep("service_orders", it.id, unsynced) }
            if (orders.isNotEmpty()) database.serviceOrderDao().insertAll(orders)
            ordersCount = orders.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar ordens de serviço: ${e.message}")
        }

        try {
            val unsynced = database.testRecordDao().getUnsyncedIds().toSet()
            val tests = SupabaseService.fetchTestRecords(url, key).filter { keep("test_records", it.id, unsynced) }.map { remote ->
                // mantém o vínculo arquivo local -> URL das mídias enviadas por este aparelho
                val local = database.testRecordDao().getTestById(remote.id)
                if (local != null && local.remoteMedia.isNotBlank()) {
                    val remoteUrls = SupabaseService.parseRemoteMedia(local.remoteMedia)
                    val byUrl = remoteUrls.entries.associate { (file, publicUrl) -> publicUrl to file }
                    remote.copy(
                        photoUris = SupabaseService.parsePhotoEntries(remote.photoUris)
                            .joinToString("|||") { (label, uri) -> "$label::${byUrl[uri] ?: uri}" },
                        technicianSignature = byUrl[remote.technicianSignature] ?: remote.technicianSignature,
                        techResponsibleSignature = byUrl[remote.techResponsibleSignature] ?: remote.techResponsibleSignature,
                        remoteMedia = local.remoteMedia
                    )
                } else remote
            }
            if (tests.isNotEmpty()) database.testRecordDao().insertAll(tests)
            testsCount = tests.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar laudos: ${e.message}")
        }

        try {
            val unsynced = database.labInstrumentDao().getUnsyncedIds().toSet()
            val instruments = SupabaseService.fetchLabInstruments(url, key).filter { keep("lab_instruments", it.id, unsynced) }
            if (instruments.isNotEmpty()) database.labInstrumentDao().insertAll(instruments)
            instCount = instruments.size
        } catch (e: Exception) {
            errors.add("Erro ao baixar instrumentos: ${e.message}")
        }

        // Exclusões feitas em outros dispositivos (deleted_at preenchido na nuvem)
        try {
            SupabaseService.fetchDeletedIds(url, key, "clients").forEach { database.clientDao().deleteClientById(it) }
            SupabaseService.fetchDeletedIds(url, key, "equipment").forEach { database.equipmentDao().deleteEquipmentById(it) }
            SupabaseService.fetchDeletedIds(url, key, "service_orders").forEach { database.serviceOrderDao().deleteServiceOrderById(it) }
            SupabaseService.fetchDeletedIds(url, key, "test_records").forEach { database.testRecordDao().deleteTestById(it) }
            SupabaseService.fetchDeletedIds(url, key, "lab_instruments").forEach { database.labInstrumentDao().deleteInstrumentById(it) }
        } catch (e: Exception) {
            errors.add("Erro ao aplicar exclusões da nuvem: ${e.message}")
        }

        // Faixas de numeração para trabalhar sem internet
        refillNumberBlocks()

        saveLastSyncTimestamp()
        addAuditLog("SYNC_SUPABASE", "Supabase", "pull", "Download do Supabase concluído: $compCount empresas, $usersCount usuários, $clientsCount clientes, $eqCount equipamentos, $testsCount laudos")

        SupabaseSyncResult(
            success = errors.isEmpty(),
            message = if (errors.isEmpty()) "Download do Supabase concluído com sucesso!" else "Download do Supabase concluído com avisos",
            companiesPulled = compCount,
            usersPulled = usersCount,
            clientsPulled = clientsCount,
            equipmentPulled = eqCount,
            ordersPulled = ordersCount,
            testsPulled = testsCount,
            instrumentsPulled = instCount,
            errors = errors
        )
    }

    private suspend fun saveLastSyncTimestamp() {
        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val currentSetting = database.settingsDao().getSettingsOnce()
        if (currentSetting != null) {
            database.settingsDao().saveSettings(currentSetting.copy(supabaseLastSyncTimestamp = nowStr))
        }
    }

    /**
     * Envia uma tabela: PENDING (alterado no aparelho) sobrescreve a nuvem;
     * LEGACY (anterior ao controle de envio) não sobrescreve o que a web alterou.
     */
    private suspend fun <T> pushTable(
        label: String,
        table: String,
        items: List<T>,
        statusOf: (T) -> String,
        idOf: (T) -> String,
        errors: MutableList<String>,
        upsert: suspend (List<T>, Boolean) -> Result<Int>
    ): Int {
        var pushed = 0
        for ((force, group) in items.groupBy { statusOf(it) != SyncStatus.LEGACY }) {
            val res = upsert(group, force)
            if (res.isSuccess) {
                pushed += res.getOrDefault(0)
                if (table != "test_records") markSynced(table, group.map(idOf))
            } else {
                errors.add("Erro ao enviar $label: ${res.exceptionOrNull()?.message}")
            }
        }
        return pushed
    }

    /** Envia somente o que foi alterado no aparelho, mais as exclusões da fila. */
    suspend fun pushToSupabase(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        val (url, key, _) = getSupabaseCredentials()
        val hasAuth = !SupabaseService.currentAuthToken.isNullOrBlank()
        val isSvc = SupabaseService.isServiceRole(key)
        if (!hasAuth && !isSvc) {
            return@withContext SupabaseSyncResult(
                success = true,
                message = "Os dados estão gravados com segurança no banco local do dispositivo. Faça login com o Supabase Auth para envio à nuvem.",
                errors = emptyList()
            )
        }

        val errors = mutableListOf<String>()
        var compPushed = 0
        val (companyId, companyName, user) = currentTargets()

        // Empresa do usuário (somente ela; sem sobrescrever o que a web alterou)
        try {
            val activeComp = SupabaseService.currentCompanyProfile
            if (activeComp != null) {
                val res = SupabaseService.upsertCompanies(url, key, listOf(activeComp), force = false)
                if (res.isSuccess) compPushed = res.getOrDefault(0)
                else errors.add("Erro ao enviar empresa: ${res.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            errors.add("Erro ao enviar empresa: ${e.message}")
        }
        // Usuários não entram no envio geral: são administrados na plataforma web
        // (contas de login, perfis e módulos). Cadastros feitos no app seguem por saveUser.

        // Ordem das chaves estrangeiras: clientes -> equipamentos -> OS -> laudos
        val clientsPushed = try {
            val items = database.clientDao().getUnsynced().map { c ->
                c.copy(companyId = pick(companyId, c.companyId, "comp-jvm-01"), companyName = pick(companyName, c.companyName, "JVM Engenharia"))
            }
            pushTable("clientes", "clients", items, { it.syncStatus }, { it.id }, errors) { list, force ->
                SupabaseService.upsertClients(url, key, list, force)
            }
        } catch (e: Exception) { errors.add("Erro ao enviar clientes: ${e.message}"); 0 }

        val eqPushed = try {
            val items = database.equipmentDao().getUnsynced().map { eq ->
                eq.copy(
                    companyId = pick(companyId, eq.companyId, "comp-jvm-01"),
                    companyName = pick(companyName, eq.companyName, "JVM Engenharia"),
                    userId = pick(user?.id, eq.userId, "user-jvm-01"),
                    userName = pick(user?.name, eq.userName, "Eng. João Carlos")
                )
            }
            pushTable("equipamentos", "equipment", items, { it.syncStatus }, { it.id }, errors) { list, force ->
                SupabaseService.upsertEquipment(url, key, list, force)
            }
        } catch (e: Exception) { errors.add("Erro ao enviar equipamentos: ${e.message}"); 0 }

        val ordersPushed = try {
            val items = database.serviceOrderDao().getUnsynced().map { ord ->
                ord.copy(
                    companyId = pick(companyId, ord.companyId, "comp-jvm-01"),
                    companyName = pick(companyName, ord.companyName, "JVM Engenharia"),
                    technicianId = pick(user?.id, ord.technicianId, "user-jvm-01", ""),
                    technicianName = pick(user?.name, ord.technicianName, "Analista Executor", "Engenheiro Responsável")
                )
            }
            pushTable("ordens de serviço", "service_orders", items, { it.syncStatus }, { it.id }, errors) { list, force ->
                SupabaseService.upsertServiceOrders(url, key, list, force)
            }
        } catch (e: Exception) { errors.add("Erro ao enviar ordens de serviço: ${e.message}"); 0 }

        val testsPushed = try {
            // pendentes + laudos já sincronizados que ainda têm foto/assinatura do
            // aparelho sem URL na nuvem (ex.: envio de foto que falhou antes)
            val withUnsentMedia = database.testRecordDao().getAllTests().first().filter { t ->
                t.syncStatus == SyncStatus.SYNCED && hasUnsentMedia(t)
            }
            val items = (database.testRecordDao().getUnsynced() + withUnsentMedia).map { t ->
                t.copy(
                    companyId = pick(companyId, t.companyId, "comp-jvm-01"),
                    companyName = pick(companyName, t.companyName, "JVM Engenharia"),
                    userId = pick(user?.id, t.userId, "user-jvm-01"),
                    userName = pick(user?.name, t.userName, "Eng. João Carlos")
                )
            }
            // pushTests envia fotos/assinaturas ao Storage e marca os laudos completos
            pushTable("laudos", "test_records", items, { it.syncStatus }, { it.id }, errors) { list, force ->
                pushTests(url, key, list, force, errors)
            }
        } catch (e: Exception) { errors.add("Erro ao enviar laudos: ${e.message}"); 0 }

        val instPushed = try {
            pushTable("instrumentos", "lab_instruments", database.labInstrumentDao().getUnsynced(), { it.syncStatus }, { it.id }, errors) { list, force ->
                SupabaseService.upsertLabInstruments(url, key, list, force)
            }
        } catch (e: Exception) { errors.add("Erro ao enviar instrumentos: ${e.message}"); 0 }

        errors.addAll(flushPendingDeletes(url, key))
        pushCompanySettings(url, key)?.let { errors.add(it) }

        saveLastSyncTimestamp()
        addAuditLog("SYNC_SUPABASE", "Supabase", "push", "Envio ao Supabase concluído: $compPushed empresas, $clientsPushed clientes, $eqPushed equipamentos, $ordersPushed OS, $testsPushed laudos")

        SupabaseSyncResult(
            success = errors.isEmpty(),
            message = if (errors.isEmpty()) "Envio ao Supabase concluído com sucesso!" else "Envio concluído com avisos",
            companiesPushed = compPushed,
            clientsPushed = clientsPushed,
            equipmentPushed = eqPushed,
            ordersPushed = ordersPushed,
            testsPushed = testsPushed,
            instrumentsPushed = instPushed,
            errors = errors
        )
    }

    suspend fun syncAllSupabase(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        // Envia primeiro: o download não substitui o que ainda está pendente
        val pushRes = pushToSupabase()
        val pullRes = pullFromSupabase()
        // sessão vencida que não pôde ser renovada: uma mensagem clara em vez de "JWT expired" em cada tabela
        val rawErrors = pullRes.errors + pushRes.errors
        val isAuthError: (String) -> Boolean = { it.contains("PGRST303") || it.contains("JWT expired", ignoreCase = true) || it.contains("PGRST301") }
        val allErrors = if (rawErrors.any(isAuthError)) {
            listOf("Sua sessão expirou e não pôde ser renovada automaticamente. Saia do aplicativo e entre de novo com seu usuário e senha — os dados do aparelho são mantidos e serão enviados na próxima sincronização.") +
                rawErrors.filterNot(isAuthError)
        } else rawErrors
        SupabaseSyncResult(
            success = allErrors.isEmpty(),
            message = if (allErrors.isEmpty()) "Sincronização bidirecional concluída com sucesso!" else "Sincronização concluída com avisos",
            companiesPulled = pullRes.companiesPulled,
            companiesPushed = pushRes.companiesPushed,
            usersPulled = pullRes.usersPulled,
            usersPushed = pushRes.usersPushed,
            clientsPulled = pullRes.clientsPulled,
            clientsPushed = pushRes.clientsPushed,
            equipmentPulled = pullRes.equipmentPulled,
            equipmentPushed = pushRes.equipmentPushed,
            ordersPulled = pullRes.ordersPulled,
            ordersPushed = pushRes.ordersPushed,
            testsPulled = pullRes.testsPulled,
            testsPushed = pushRes.testsPushed,
            instrumentsPulled = pullRes.instrumentsPulled,
            instrumentsPushed = pushRes.instrumentsPushed,
            errors = allErrors
        )
    }

    /** Quantidade de registros e exclusões ainda não enviados à nuvem. */
    suspend fun countPendingSync(): Int = withContext(Dispatchers.IO) {
        database.clientDao().getUnsyncedIds().size +
            database.equipmentDao().getUnsyncedIds().size +
            database.serviceOrderDao().getUnsyncedIds().size +
            database.testRecordDao().getUnsyncedIds().size +
            database.labInstrumentDao().getUnsyncedIds().size +
            database.pendingDeleteDao().getAll().size
    }
}
