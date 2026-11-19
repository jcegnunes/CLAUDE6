package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClientEntity
import com.example.data.model.CompanyEntity
import com.example.data.model.EquipmentEntity
import com.example.data.model.LabInstrumentEntity
import com.example.data.model.NormCriterionEntity
import com.example.data.model.NumberBlockEntity
import com.example.data.model.PendingDeleteEntity
import com.example.data.model.ServiceOrderEntity
import com.example.data.model.TestRecordEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun getAllCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getCompanyById(id: String): CompanyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: CompanyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(companies: List<CompanyEntity>)

    @Update
    suspend fun updateCompany(company: CompanyEntity)

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun deleteCompanyById(id: String)

    @Query("DELETE FROM companies")
    suspend fun deleteAll()
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE companyId = :companyId ORDER BY name ASC")
    fun getUsersByCompany(companyId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:identifier) OR LOWER(name) = LOWER(:identifier) LIMIT 1")
    suspend fun getUserByIdentifier(identifier: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: String)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY razaoSocial ASC")
    fun getAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientById(id: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<ClientEntity>)

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: String)
@Query("SELECT * FROM clients WHERE syncStatus != 'synced'")    suspend fun getUnsynced(): List<ClientEntity>    @Query("SELECT id FROM clients WHERE syncStatus != 'synced'")    suspend fun getUnsyncedIds(): List<String>    @Query("UPDATE clients SET syncStatus = :status WHERE id IN (:ids)")    suspend fun setSyncStatus(ids: List<String>, status: String)

    @Query("DELETE FROM clients")
    suspend fun deleteAll()
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY tag ASC")
    fun getAllEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getEquipmentById(id: String): EquipmentEntity?

    @Query("SELECT * FROM equipment WHERE tag = :tag LIMIT 1")
    suspend fun getEquipmentByTag(tag: String): EquipmentEntity?

    @Query("SELECT * FROM equipment WHERE clientId = :clientId")
    fun getEquipmentByClient(clientId: String): Flow<List<EquipmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEquipment(equipment: EquipmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipmentList: List<EquipmentEntity>)

    @Update
    suspend fun updateEquipment(equipment: EquipmentEntity)

    @Query("DELETE FROM equipment WHERE id = :id")
    suspend fun deleteEquipmentById(id: String)
@Query("SELECT * FROM equipment WHERE syncStatus != 'synced'")    suspend fun getUnsynced(): List<EquipmentEntity>    @Query("SELECT id FROM equipment WHERE syncStatus != 'synced'")    suspend fun getUnsyncedIds(): List<String>    @Query("UPDATE equipment SET syncStatus = :status WHERE id IN (:ids)")    suspend fun setSyncStatus(ids: List<String>, status: String)

    @Query("DELETE FROM equipment WHERE id IN (:ids)")
    suspend fun deleteMultipleEquipment(ids: List<String>)

    @Query("DELETE FROM equipment")
    suspend fun deleteAll()
}

@Dao
interface ServiceOrderDao {
    @Query("SELECT * FROM service_orders ORDER BY openDate DESC")
    fun getAllServiceOrders(): Flow<List<ServiceOrderEntity>>

    @Query("SELECT * FROM service_orders WHERE id = :id")
    suspend fun getServiceOrderById(id: String): ServiceOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceOrder(order: ServiceOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<ServiceOrderEntity>)

    @Update
    suspend fun updateServiceOrder(order: ServiceOrderEntity)

    @Query("DELETE FROM service_orders WHERE id = :id")
    suspend fun deleteServiceOrderById(id: String)
@Query("SELECT * FROM service_orders WHERE syncStatus != 'synced'")    suspend fun getUnsynced(): List<ServiceOrderEntity>    @Query("SELECT id FROM service_orders WHERE syncStatus != 'synced'")    suspend fun getUnsyncedIds(): List<String>    @Query("UPDATE service_orders SET syncStatus = :status WHERE id IN (:ids)")    suspend fun setSyncStatus(ids: List<String>, status: String)

    @Query("DELETE FROM service_orders")
    suspend fun deleteAll()
}

@Dao
interface TestRecordDao {
    @Query("SELECT * FROM test_records ORDER BY testDate DESC, testTime DESC")
    fun getAllTests(): Flow<List<TestRecordEntity>>

    @Query("SELECT * FROM test_records WHERE id = :id")
    suspend fun getTestById(id: String): TestRecordEntity?

    @Query("SELECT * FROM test_records WHERE validationCode = :code LIMIT 1")
    suspend fun getTestByValidationCode(code: String): TestRecordEntity?

    @Query("SELECT * FROM test_records WHERE equipmentId = :equipmentId ORDER BY testDate DESC")
    fun getTestsByEquipment(equipmentId: String): Flow<List<TestRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: TestRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tests: List<TestRecordEntity>)

    @Update
    suspend fun updateTest(test: TestRecordEntity)

    @Query("DELETE FROM test_records WHERE id = :id")
    suspend fun deleteTestById(id: String)
@Query("SELECT * FROM test_records WHERE syncStatus != 'synced'")    suspend fun getUnsynced(): List<TestRecordEntity>    @Query("SELECT id FROM test_records WHERE syncStatus != 'synced'")    suspend fun getUnsyncedIds(): List<String>    @Query("UPDATE test_records SET syncStatus = :status WHERE id IN (:ids)")    suspend fun setSyncStatus(ids: List<String>, status: String)

    @Query("DELETE FROM test_records WHERE id IN (:ids)")
    suspend fun deleteMultipleTests(ids: List<String>)

    @Query("DELETE FROM test_records")
    suspend fun deleteAll()
}

@Dao
interface LabInstrumentDao {
    @Query("SELECT * FROM lab_instruments ORDER BY type ASC")
    fun getAllInstruments(): Flow<List<LabInstrumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstrument(instrument: LabInstrumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instruments: List<LabInstrumentEntity>)

    @Query("DELETE FROM lab_instruments WHERE id = :id")
    suspend fun deleteInstrumentById(id: String)
@Query("SELECT * FROM lab_instruments WHERE syncStatus != 'synced'")    suspend fun getUnsynced(): List<LabInstrumentEntity>    @Query("SELECT id FROM lab_instruments WHERE syncStatus != 'synced'")    suspend fun getUnsyncedIds(): List<String>    @Query("UPDATE lab_instruments SET syncStatus = :status WHERE id IN (:ids)")    suspend fun setSyncStatus(ids: List<String>, status: String)

    @Query("DELETE FROM lab_instruments")
    suspend fun deleteAll()
}

@Dao
interface NormDao {
    @Query("SELECT * FROM norms ORDER BY code ASC")
    fun getAllNorms(): Flow<List<NormCriterionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNorm(norm: NormCriterionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(norms: List<NormCriterionEntity>)

    @Query("DELETE FROM norms WHERE id = :id")
    suspend fun deleteNormById(id: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY dateTime DESC LIMIT 200")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>)
}

@Dao
interface PendingDeleteDao {
    @Query("SELECT * FROM pending_deletes ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingDeleteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingDeleteEntity)

    @Query("DELETE FROM pending_deletes WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface NumberBlockDao {
    @Query("SELECT * FROM number_blocks WHERE kind = :kind LIMIT 1")
    suspend fun get(kind: String): NumberBlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(block: NumberBlockEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings ORDER BY id ASC LIMIT 1")
    fun getSettings(): Flow<AppSettingEntity?>

    @Query("SELECT * FROM app_settings ORDER BY id ASC LIMIT 1")
    suspend fun getSettingsOnce(): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingEntity)
}

@Database(
    entities = [
        CompanyEntity::class,
        UserEntity::class,
        ClientEntity::class,
        EquipmentEntity::class,
        ServiceOrderEntity::class,
        TestRecordEntity::class,
        LabInstrumentEntity::class,
        NormCriterionEntity::class,
        AuditLogEntity::class,
        AppSettingEntity::class,
        PendingDeleteEntity::class,
        NumberBlockEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun userDao(): UserDao
    abstract fun clientDao(): ClientDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun serviceOrderDao(): ServiceOrderDao
    abstract fun testRecordDao(): TestRecordDao
    abstract fun labInstrumentDao(): LabInstrumentDao
    abstract fun normDao(): NormDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun pendingDeleteDao(): PendingDeleteDao
    abstract fun numberBlockDao(): NumberBlockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reportIntroText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reportObjectiveText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reportMethodologyText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reportDevelopmentText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN reportConclusionText TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lab_instruments ADD COLUMN calibrationCertificateUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lab_instruments ADD COLUMN calibrationCertificateFileName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN companyLogoUri TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN letterheadUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN useLetterhead INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN letterheadOpacity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN letterheadApplyMode TEXT NOT NULL DEFAULT 'ALL_PAGES'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN letterheadHeaderMode TEXT NOT NULL DEFAULT 'INTEGRATED'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN gloveProcedurePhotoUri TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN supabaseUrl TEXT NOT NULL DEFAULT 'https://cdtbzbshylrcprvmjpgc.supabase.co/rest/v1/'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN supabaseAnonKey TEXT NOT NULL DEFAULT 'sb_publishable_j3sUJcAb-zBEQI_S09u2Cg_X1-WJ-8M'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN supabaseAutoSync INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN supabaseLastSyncTimestamp TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS companies (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        legalName TEXT NOT NULL DEFAULT '',
                        cnpj TEXT NOT NULL DEFAULT '',
                        creaCompanyRegister TEXT NOT NULL DEFAULT '',
                        address TEXT NOT NULL DEFAULT '',
                        city TEXT NOT NULL DEFAULT 'Brasília',
                        state TEXT NOT NULL DEFAULT 'DF',
                        phone TEXT NOT NULL DEFAULT '',
                        email TEXT NOT NULL DEFAULT '',
                        website TEXT NOT NULL DEFAULT '',
                        logoUri TEXT NOT NULL DEFAULT '',
                        createdAt TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        companyName TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        password TEXT NOT NULL DEFAULT '123',
                        role TEXT NOT NULL DEFAULT 'Engenheiro Responsável',
                        registrationCouncil TEXT NOT NULL DEFAULT '',
                        phone TEXT NOT NULL DEFAULT '',
                        signatureImageUri TEXT NOT NULL DEFAULT '',
                        createdAt TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE equipment ADD COLUMN companyId TEXT NOT NULL DEFAULT 'comp-jvm-01'")
                db.execSQL("ALTER TABLE equipment ADD COLUMN companyName TEXT NOT NULL DEFAULT 'JVM Engenharia'")
                db.execSQL("ALTER TABLE equipment ADD COLUMN userId TEXT NOT NULL DEFAULT 'user-jvm-01'")
                db.execSQL("ALTER TABLE equipment ADD COLUMN userName TEXT NOT NULL DEFAULT 'Eng. João Carlos'")

                db.execSQL("ALTER TABLE service_orders ADD COLUMN companyId TEXT NOT NULL DEFAULT 'comp-jvm-01'")
                db.execSQL("ALTER TABLE service_orders ADD COLUMN companyName TEXT NOT NULL DEFAULT 'JVM Engenharia'")

                db.execSQL("ALTER TABLE test_records ADD COLUMN companyId TEXT NOT NULL DEFAULT 'comp-jvm-01'")
                db.execSQL("ALTER TABLE test_records ADD COLUMN companyName TEXT NOT NULL DEFAULT 'JVM Engenharia'")
                db.execSQL("ALTER TABLE test_records ADD COLUMN userId TEXT NOT NULL DEFAULT 'user-jvm-01'")
                db.execSQL("ALTER TABLE test_records ADD COLUMN userName TEXT NOT NULL DEFAULT 'Eng. João Carlos'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN companyId TEXT NOT NULL DEFAULT 'comp-jvm-01'")
                db.execSQL("ALTER TABLE clients ADD COLUMN companyName TEXT NOT NULL DEFAULT 'JVM Engenharia'")
            }
        }

        // Controle de envio ao Supabase. Registros já existentes ficam "legacy":
        // são enviados uma vez sem sobrescrever o que a plataforma web alterou.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("clients", "equipment", "service_orders", "test_records", "lab_instruments")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'legacy'")
                }
                db.execSQL("ALTER TABLE test_records ADD COLUMN remoteMedia TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_deletes (" +
                        "`key` TEXT NOT NULL PRIMARY KEY, tableName TEXT NOT NULL, " +
                        "entityId TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS number_blocks (" +
                        "kind TEXT NOT NULL PRIMARY KEY, nextValue INTEGER NOT NULL, endValue INTEGER NOT NULL)"
                )
                // Senhas locais antigas (texto puro / "123" padrão) deixam de valer:
                // o próximo login online grava a senha protegida
                db.execSQL("UPDATE users SET password = '' WHERE password NOT LIKE 'pbkdf2\$%'")
            }
        }

        // Configurações da empresa sincronizadas com a web (companies.lab_info)
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN labInfoJson TEXT NOT NULL DEFAULT ''")
            }
        }

        // Detector de tensão por indução: tabela distância × tensão do ensaio
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE test_records ADD COLUMN inductionPointsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE test_records ADD COLUMN stickTestPoints INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jvm_dielectric_lab.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

