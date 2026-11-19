package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClientEntity
import com.example.data.model.CompanyEntity
import com.example.data.model.EquipmentEntity
import com.example.data.model.LabInstrumentEntity
import com.example.data.model.NormCriterionEntity
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.TestRecordEntity
import com.example.data.model.UserEntity
import com.example.data.repository.DielectricRepository
import com.example.data.service.SupabaseConnectionStatus
import com.example.data.service.SupabaseService
import com.example.data.service.SupabaseSyncResult
import com.example.util.BackupImportResult
import com.example.util.BackupInspectionSummary
import com.example.util.LocalBackupManager
import com.example.util.SignatureStorageHelper
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class DielectricViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DielectricRepository
    private val authPrefs = application.getSharedPreferences("jvm_dielectric_auth", Context.MODE_PRIVATE)

    val companies: StateFlow<List<CompanyEntity>>
    val users: StateFlow<List<UserEntity>>
    val clients: StateFlow<List<ClientEntity>>
    val equipment: StateFlow<List<EquipmentEntity>>
    val serviceOrders: StateFlow<List<ServiceOrderEntity>>
    val tests: StateFlow<List<TestRecordEntity>>
    val instruments: StateFlow<List<LabInstrumentEntity>>
    val norms: StateFlow<List<NormCriterionEntity>>
    val auditLogs: StateFlow<List<AuditLogEntity>>
    val settings: StateFlow<AppSettingEntity?>

    // Authentication and Active User / Company State
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentCompany = MutableStateFlow<CompanyEntity?>(null)
    val currentCompany: StateFlow<CompanyEntity?> = _currentCompany.asStateFlow()

    // Multi-company / user filter preference
    private val _filterByMyCompany = MutableStateFlow(false)
    val filterByMyCompany: StateFlow<Boolean> = _filterByMyCompany.asStateFlow()

    // Active View Navigation
    private val _currentScreen = MutableStateFlow("dashboard")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Wizard Pre-Selections
    private val _wizardEquipmentId = MutableStateFlow<String?>(null)
    val wizardEquipmentId: StateFlow<String?> = _wizardEquipmentId.asStateFlow()

    private val _wizardOSId = MutableStateFlow<String?>(null)
    val wizardOSId: StateFlow<String?> = _wizardOSId.asStateFlow()

    // Active Dialog Records
    private val _activeLaudoTest = MutableStateFlow<TestRecordEntity?>(null)
    val activeLaudoTest: StateFlow<TestRecordEntity?> = _activeLaudoTest.asStateFlow()

    private val _activeCertificadoTest = MutableStateFlow<TestRecordEntity?>(null)
    val activeCertificadoTest: StateFlow<TestRecordEntity?> = _activeCertificadoTest.asStateFlow()

    private val _activeNiimbotTest = MutableStateFlow<TestRecordEntity?>(null)
    val activeNiimbotTest: StateFlow<TestRecordEntity?> = _activeNiimbotTest.asStateFlow()

    private val _editingTest = MutableStateFlow<TestRecordEntity?>(null)
    val editingTest: StateFlow<TestRecordEntity?> = _editingTest.asStateFlow()

    // Validation Search State
    private val _validationResult = MutableStateFlow<TestRecordEntity?>(null)
    val validationResult: StateFlow<TestRecordEntity?> = _validationResult.asStateFlow()

    private val _validationSearched = MutableStateFlow(false)
    val validationSearched: StateFlow<Boolean> = _validationSearched.asStateFlow()

    // Supabase Cloud Sync States
    private val _supabaseConnectionStatus = MutableStateFlow<SupabaseConnectionStatus?>(null)
    val supabaseConnectionStatus: StateFlow<SupabaseConnectionStatus?> = _supabaseConnectionStatus.asStateFlow()

    private val _isSupabaseTesting = MutableStateFlow(false)
    val isSupabaseTesting: StateFlow<Boolean> = _isSupabaseTesting.asStateFlow()

    private val _isSupabaseSyncing = MutableStateFlow(false)
    val isSupabaseSyncing: StateFlow<Boolean> = _isSupabaseSyncing.asStateFlow()

    private val _supabaseSyncResult = MutableStateFlow<SupabaseSyncResult?>(null)
    val supabaseSyncResult: StateFlow<SupabaseSyncResult?> = _supabaseSyncResult.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DielectricRepository(db, application)
        // critérios normativos da web disponíveis antes de qualquer ensaio
        com.example.domain.NormsCatalog.load(application)

        companies = repository.allCompanies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        users = repository.allUsers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        clients = repository.allClients.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        equipment = repository.allEquipment.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        serviceOrders = repository.allServiceOrders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        tests = repository.allTests.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        instruments = repository.allInstruments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        norms = repository.allNorms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        auditLogs = repository.allAuditLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        settings = repository.appSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        val savedSession = authPrefs.getBoolean("session_active", false)
        val savedUserId = authPrefs.getString("session_user_id", null)
        val savedToken = authPrefs.getString("session_auth_token", null)
        val savedRefreshToken = authPrefs.getString("session_refresh_token", null)
        if (!savedToken.isNullOrBlank()) {
            SupabaseService.currentAuthToken = savedToken
        }
        if (!savedRefreshToken.isNullOrBlank()) {
            SupabaseService.currentRefreshToken = savedRefreshToken
        }
        // sessão renovada automaticamente: salva o par novo (o refresh token antigo deixa de valer)
        SupabaseService.onTokensRefreshed = { access, refresh ->
            authPrefs.edit()
                .putString("session_auth_token", access)
                .putString("session_refresh_token", refresh)
                .apply()
        }

        viewModelScope.launch {
            val sigPath = SignatureStorageHelper.getOrCreateDefaultJoaoSignature(application)
            repository.seedDatabaseIfEmpty(sigPath)

            if (savedSession && !savedUserId.isNullOrBlank()) {
                val user = repository.getUserById(savedUserId)
                if (user != null) {
                    val company = repository.getCompanyById(user.companyId)
                    _currentUser.value = user
                    _currentCompany.value = company
                    SupabaseService.currentUserProfile = user
                    SupabaseService.currentCompanyProfile = company
                    _isLoggedIn.value = true
                    // Sessão restaurada: envia o que ficou pendente e atualiza da nuvem
                    if (!savedToken.isNullOrBlank()) {
                        try {
                            _isSupabaseSyncing.value = true
                            _supabaseSyncResult.value = repository.syncAllSupabase()
                        } catch (e: Exception) {
                            android.util.Log.w("DielectricViewModel", "Sync on session restore: ${e.message}")
                        } finally {
                            _isSupabaseSyncing.value = false
                        }
                    }
                } else {
                    _isLoggedIn.value = false
                    _currentUser.value = null
                    _currentCompany.value = null
                    SupabaseService.currentUserProfile = null
                    SupabaseService.currentCompanyProfile = null
                }
            } else {
                _isLoggedIn.value = false
                _currentUser.value = null
                _currentCompany.value = null
                SupabaseService.currentUserProfile = null
                SupabaseService.currentCompanyProfile = null
            }
        }
    }

    // --- Authentication Actions ---
    suspend fun login(identifier: String, pass: String): Pair<Boolean, String?> {
        val trimmed = identifier.trim()
        val trimmedPass = pass.trim()
        if (trimmed.isBlank()) {
            return Pair(false, "Informe seu usuário ou e-mail cadastrado.")
        }
        if (trimmedPass.isBlank()) {
            return Pair(false, "Informe sua senha de acesso.")
        }
        val user = repository.authenticateUser(trimmed, trimmedPass)
        return if (user != null) {
            val company = repository.getCompanyById(user.companyId)
            _currentUser.value = user
            _currentCompany.value = company
            SupabaseService.currentUserProfile = user
            SupabaseService.currentCompanyProfile = company
            _isLoggedIn.value = true
            authPrefs.edit()
                .putBoolean("session_active", true)
                .putString("session_user_id", user.id)
                .putString("session_auth_token", SupabaseService.currentAuthToken ?: "")
                .putString("session_refresh_token", SupabaseService.currentRefreshToken ?: "")
                .apply()

            // Automatically download all existing data from the Supabase database upon login
            viewModelScope.launch {
                try {
                    _isSupabaseSyncing.value = true
                    // envia o que ficou pendente offline e baixa os dados da empresa
                    val syncRes = repository.syncAllSupabase()
                    _supabaseSyncResult.value = syncRes
                    // Refresh company details in case pulled from cloud
                    val updatedCompany = repository.getCompanyById(user.companyId)
                    if (updatedCompany != null) {
                        _currentCompany.value = updatedCompany
                        SupabaseService.currentCompanyProfile = updatedCompany
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DielectricViewModel", "First login pull from Supabase: ${e.message}")
                } finally {
                    _isSupabaseSyncing.value = false
                }
            }

            Pair(true, null)
        } else {
            Pair(false, "Usuário ou senha incorretos. Verifique as credenciais cadastradas no banco de dados.")
        }
    }

    fun quickLoginAs(user: UserEntity) {
        viewModelScope.launch {
            val company = repository.getCompanyById(user.companyId)
            _currentUser.value = user
            _currentCompany.value = company
            SupabaseService.currentUserProfile = user
            SupabaseService.currentCompanyProfile = company
            _isLoggedIn.value = true
            authPrefs.edit()
                .putBoolean("session_active", true)
                .putString("session_user_id", user.id)
                .apply()
        }
    }

    suspend fun registerCompanyAndUser(company: CompanyEntity, user: UserEntity): Pair<Boolean, String?> {
        return try {
            // Save company
            repository.saveCompany(company)
            // Associate user to this company
            val fullUser = user.copy(
                companyId = company.id,
                companyName = company.name
            )
            repository.saveUser(fullUser)
            _currentUser.value = fullUser
            _currentCompany.value = company
            SupabaseService.currentUserProfile = fullUser
            SupabaseService.currentCompanyProfile = company
            _isLoggedIn.value = true
            authPrefs.edit()
                .putBoolean("session_active", true)
                .putString("session_user_id", fullUser.id)
                .apply()
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Erro ao cadastrar: ${e.localizedMessage}")
        }
    }

    suspend fun registerUserOnly(user: UserEntity): Pair<Boolean, String?> {
        return try {
            val existing = repository.getUserByEmail(user.email)
            if (existing != null) {
                return Pair(false, "Este e-mail já está cadastrado no sistema.")
            }
            repository.saveUser(user)
            val company = repository.getCompanyById(user.companyId)
            _currentUser.value = user
            _currentCompany.value = company
            SupabaseService.currentUserProfile = user
            SupabaseService.currentCompanyProfile = company
            _isLoggedIn.value = true
            authPrefs.edit()
                .putBoolean("session_active", true)
                .putString("session_user_id", user.id)
                .apply()
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Erro ao cadastrar usuário: ${e.localizedMessage}")
        }
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentUser.value = null
        _currentCompany.value = null
        SupabaseService.currentUserProfile = null
        SupabaseService.currentCompanyProfile = null
        SupabaseService.currentAuthToken = null
        SupabaseService.currentRefreshToken = null
        authPrefs.edit()
            .putBoolean("session_active", false)
            .remove("session_user_id")
            .remove("session_auth_token")
            .remove("session_refresh_token")
            .apply()
    }

    fun toggleFilterByMyCompany() {
        _filterByMyCompany.value = !_filterByMyCompany.value
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun startNewTest(equipmentId: String? = null, osId: String? = null) {
        _wizardEquipmentId.value = equipmentId
        _wizardOSId.value = osId
        _editingTest.value = null
        _currentScreen.value = "wizard"
    }

    fun openLaudo(test: TestRecordEntity) {
        _activeLaudoTest.value = test
    }

    fun closeLaudo() {
        _activeLaudoTest.value = null
    }

    fun openCertificado(test: TestRecordEntity) {
        _activeCertificadoTest.value = test
    }

    fun closeCertificado() {
        _activeCertificadoTest.value = null
    }

    fun openNiimbotLabel(test: TestRecordEntity) {
        _activeNiimbotTest.value = test
    }

    fun closeNiimbotLabel() {
        _activeNiimbotTest.value = null
    }

    fun openEditTest(test: TestRecordEntity) {
        _editingTest.value = test
        _wizardEquipmentId.value = test.equipmentId
        _wizardOSId.value = test.serviceOrderId
        _activeLaudoTest.value = null
        _activeCertificadoTest.value = null
        _currentScreen.value = "wizard"
    }

    fun cancelEditTest() {
        _editingTest.value = null
        _currentScreen.value = "tests"
    }

    fun closeEditTest() {
        _editingTest.value = null
    }

    fun updateTestRecord(updatedTest: TestRecordEntity) {
        viewModelScope.launch {
            val activeComp = _currentCompany.value
            val activeUser = _currentUser.value
            val enriched = updatedTest.copy(
                companyId = if (updatedTest.companyId.isNotBlank() && updatedTest.companyId != "comp-jvm-01") updatedTest.companyId else (activeComp?.id ?: activeUser?.companyId ?: updatedTest.companyId),
                companyName = if (updatedTest.companyName.isNotBlank() && updatedTest.companyName != "JVM Engenharia") updatedTest.companyName else (activeComp?.name ?: activeUser?.companyName ?: updatedTest.companyName),
                userId = if (updatedTest.userId.isNotBlank() && updatedTest.userId != "user-jvm-01") updatedTest.userId else (activeUser?.id ?: updatedTest.userId),
                userName = if (updatedTest.userName.isNotBlank() && updatedTest.userName != "Eng. João Carlos") updatedTest.userName else (activeUser?.name ?: updatedTest.userName)
            )
            repository.saveTestRecord(enriched)
            if (_activeLaudoTest.value?.id == enriched.id) {
                _activeLaudoTest.value = enriched
            }
            if (_activeCertificadoTest.value?.id == enriched.id) {
                _activeCertificadoTest.value = enriched
            }
            if (_activeNiimbotTest.value?.id == enriched.id) {
                _activeNiimbotTest.value = enriched
            }
            _editingTest.value = null
        }
    }

    fun searchValidationCode(rawCode: String) {
        // aceita o código digitado ou o link lido do QR Code (?codigo= ou /validar/)
        val code = (com.example.util.ValidationPortal.extractCode(rawCode) ?: rawCode).trim().uppercase()
        viewModelScope.launch {
            _validationSearched.value = true
            val local = repository.getTestByValidationCode(code)
            if (local != null) {
                _validationResult.value = local
            } else {
                val remote = repository.validateCertificateRemote(code)
                _validationResult.value = remote?.first
            }
        }
    }

    fun clearValidation() {
        _validationResult.value = null
        _validationSearched.value = false
    }

    // Database Actions
    fun saveCompany(company: CompanyEntity) = viewModelScope.launch {
        repository.saveCompany(company)
        _currentCompany.value = company
        SupabaseService.currentCompanyProfile = company
    }

    fun deleteCompany(id: String) = viewModelScope.launch {
        repository.deleteCompany(id)
    }

    fun saveUser(user: UserEntity) = viewModelScope.launch {
        repository.saveUser(user)
        if (_currentUser.value?.id == user.id) {
            _currentUser.value = user
            SupabaseService.currentUserProfile = user
        }
    }

    fun deleteUser(id: String) = viewModelScope.launch {
        repository.deleteUser(id)
    }

    fun saveClient(client: ClientEntity) = viewModelScope.launch {
        val activeComp = _currentCompany.value
        val activeUser = _currentUser.value
        val enriched = client.copy(
            companyId = if (client.companyId.isNotBlank() && client.companyId != "comp-jvm-01") client.companyId else (activeComp?.id ?: activeUser?.companyId ?: client.companyId),
            companyName = if (client.companyName.isNotBlank() && client.companyName != "JVM Engenharia") client.companyName else (activeComp?.name ?: activeUser?.companyName ?: client.companyName)
        )
        repository.saveClient(enriched)
    }

    fun deleteClient(id: String) = viewModelScope.launch {
        repository.deleteClient(id)
    }

    fun saveEquipment(eq: EquipmentEntity) = viewModelScope.launch {
        val activeComp = _currentCompany.value
        val activeUser = _currentUser.value
        val enriched = eq.copy(
            companyId = if (eq.companyId.isNotBlank() && eq.companyId != "comp-jvm-01") eq.companyId else (activeComp?.id ?: activeUser?.companyId ?: eq.companyId),
            companyName = if (eq.companyName.isNotBlank() && eq.companyName != "JVM Engenharia") eq.companyName else (activeComp?.name ?: activeUser?.companyName ?: eq.companyName),
            userId = if (eq.userId.isNotBlank() && eq.userId != "user-jvm-01") eq.userId else (activeUser?.id ?: eq.userId),
            userName = if (eq.userName.isNotBlank() && eq.userName != "Eng. João Carlos") eq.userName else (activeUser?.name ?: eq.userName)
        )
        repository.saveEquipment(enriched)
    }

    fun deleteEquipment(id: String) = viewModelScope.launch {
        repository.deleteEquipment(id)
    }

    fun deleteMultipleEquipment(ids: List<String>) = viewModelScope.launch {
        repository.deleteMultipleEquipment(ids)
    }

    fun saveServiceOrder(order: ServiceOrderEntity) = viewModelScope.launch {
        val activeComp = _currentCompany.value
        val activeUser = _currentUser.value
        val enriched = order.copy(
            companyId = if (order.companyId.isNotBlank() && order.companyId != "comp-jvm-01") order.companyId else (activeComp?.id ?: activeUser?.companyId ?: order.companyId),
            companyName = if (order.companyName.isNotBlank() && order.companyName != "JVM Engenharia") order.companyName else (activeComp?.name ?: activeUser?.companyName ?: order.companyName),
            technicianId = if (order.technicianId.isNotBlank() && order.technicianId != "user-jvm-01") order.technicianId else (activeUser?.id ?: order.technicianId),
            technicianName = if (order.technicianName.isNotBlank() && order.technicianName != "Analista Executor") order.technicianName else (activeUser?.name ?: order.technicianName)
        )
        repository.saveServiceOrder(enriched)
    }

    fun deleteServiceOrder(id: String) = viewModelScope.launch {
        repository.deleteServiceOrder(id)
    }

    fun saveTest(test: TestRecordEntity) = viewModelScope.launch {
        val activeComp = _currentCompany.value
        val activeUser = _currentUser.value
        val enriched = test.copy(
            companyId = if (test.companyId.isNotBlank() && test.companyId != "comp-jvm-01") test.companyId else (activeComp?.id ?: test.companyId),
            companyName = if (test.companyName.isNotBlank() && test.companyName != "JVM Engenharia") test.companyName else (activeComp?.name ?: test.companyName),
            userId = if (test.userId.isNotBlank() && test.userId != "user-jvm-01") test.userId else (activeUser?.id ?: test.userId),
            userName = if (test.userName.isNotBlank() && test.userName != "Eng. João Carlos") test.userName else (activeUser?.name ?: test.userName)
        )
        repository.saveTestRecord(enriched)
    }

    fun deleteTest(id: String) = viewModelScope.launch {
        repository.deleteTest(id)
    }

    fun deleteMultipleTests(ids: List<String>) = viewModelScope.launch {
        repository.deleteMultipleTests(ids)
    }

    fun saveInstrument(instrument: LabInstrumentEntity) = viewModelScope.launch {
        repository.saveInstrument(instrument)
    }

    fun deleteInstrument(id: String) = viewModelScope.launch {
        repository.deleteInstrument(id)
    }

    fun saveNorm(norm: NormCriterionEntity) = viewModelScope.launch {
        repository.saveNorm(norm)
    }

    fun deleteNorm(id: String) = viewModelScope.launch {
        repository.deleteNorm(id)
    }

    fun saveSettings(appSettings: AppSettingEntity) = viewModelScope.launch {
        repository.saveSettings(appSettings)
    }

    /** Configurações da empresa (mesmos campos da web), enviadas à nuvem pelo administrador/RT. */
    fun saveCompanySettings(appSettings: AppSettingEntity, labInfo: org.json.JSONObject) = viewModelScope.launch {
        repository.saveCompanySettings(appSettings, labInfo)
    }

    /** Administrador ou Responsável Técnico: edita as configurações da empresa (como na web). */
    fun isCompanyManager(): Boolean = repository.isCompanyManager()

    fun resetToDemoData() = viewModelScope.launch {
        val sigPath = SignatureStorageHelper.getOrCreateDefaultJoaoSignature(getApplication())
        repository.resetToSeedData(sigPath)
    }

    fun getLocalBackups(): List<File> = LocalBackupManager.listLocalBackups(getApplication())

    fun getLocalPhotosStats(): Pair<Int, Long> = LocalBackupManager.calculateLocalPhotosStats(getApplication())

    suspend fun exportFullZipBackup(onProgress: (String) -> Unit = {}): File {
        val db = AppDatabase.getDatabase(getApplication())
        return LocalBackupManager.createZipBackup(getApplication(), db, onProgress)
    }

    suspend fun saveBackupToUri(sourceFile: File, targetUri: Uri): Boolean {
        return LocalBackupManager.saveBackupToUri(getApplication(), sourceFile, targetUri)
    }

    suspend fun exportFullDatabaseJson(): String {
        val db = AppDatabase.getDatabase(getApplication())
        val bundle = LocalBackupManager.fetchAllData(db)
        return LocalBackupManager.serializeBundleToJson(bundle)
    }

    suspend fun inspectBackup(uri: Uri): BackupInspectionSummary? {
        return LocalBackupManager.inspectBackupUri(getApplication(), uri)
    }

    suspend fun restoreBackup(
        uri: Uri,
        replaceAll: Boolean,
        onProgress: (String) -> Unit = {}
    ): BackupImportResult {
        val db = AppDatabase.getDatabase(getApplication())
        return LocalBackupManager.restoreBackupFromUri(getApplication(), db, uri, replaceAll, onProgress)
    }

    fun exportBackupJson(): String {
        val testsList = tests.value
        val clientsList = clients.value
        val equipmentList = equipment.value
        val ordersList = serviceOrders.value
        val instList = instruments.value
        val setObj = settings.value

        return buildString {
            append("{\n")
            append("  \"exportedAt\": \"${System.currentTimeMillis()}\",\n")
            append("  \"totalTests\": ${testsList.size},\n")
            append("  \"totalClients\": ${clientsList.size},\n")
            append("  \"totalEquipment\": ${equipmentList.size},\n")
            append("  \"totalServiceOrders\": ${ordersList.size},\n")
            append("  \"totalInstruments\": ${instList.size},\n")
            append("  \"company\": \"${setObj?.name ?: ""}\",\n")
            append("  \"technicalResponsible\": \"${setObj?.technicalResponsibleName ?: ""}\",\n")
            append("  \"technician\": \"${setObj?.defaultTechnicianName ?: ""}\"\n")
            append("}")
        }
    }

    fun testSupabaseConnection(customUrl: String? = null, customKey: String? = null) {
        viewModelScope.launch {
            _isSupabaseTesting.value = true
            try {
                _supabaseConnectionStatus.value = repository.testSupabaseConnection(customUrl, customKey)
            } finally {
                _isSupabaseTesting.value = false
            }
        }
    }

    fun pullFromSupabase() {
        viewModelScope.launch {
            _isSupabaseSyncing.value = true
            try {
                val res = repository.pullFromSupabase()
                _supabaseSyncResult.value = res
            } finally {
                _isSupabaseSyncing.value = false
            }
        }
    }

    fun pushToSupabase() {
        viewModelScope.launch {
            _isSupabaseSyncing.value = true
            try {
                val res = repository.pushToSupabase()
                _supabaseSyncResult.value = res
            } finally {
                _isSupabaseSyncing.value = false
            }
        }
    }

    fun syncAllSupabase() {
        viewModelScope.launch {
            _isSupabaseSyncing.value = true
            try {
                val res = repository.syncAllSupabase()
                _supabaseSyncResult.value = res
            } finally {
                _isSupabaseSyncing.value = false
            }
        }
    }

    fun clearSupabaseSyncResult() {
        _supabaseSyncResult.value = null
    }

    suspend fun generateNextTestNumber(): String = repository.generateNextTestNumber()
    suspend fun generateNextReportNumber(): String = repository.generateNextReportNumber()
    suspend fun generateNextCertificateNumber(): String = repository.generateNextCertificateNumber()
    suspend fun generateNextOSNumber(): String = repository.generateNextOSNumber()
    fun generateValidationCode(): String = repository.generateValidationCode()
}

class DielectricViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DielectricViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DielectricViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
