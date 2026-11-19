package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class EquipmentType(val label: String, val norm: String, val tagPrefix: String = "TAG-EQP") {
    LUVA_ISOLANTE("Luvas Isolantes de Borracha", "ABNT NBR 16295 Tabela 4", "TAG-LUV"),
    MANGA_ISOLANTE("Mangas Isolantes de Borracha", "ABNT NBR 10624 / ASTM D1051", "TAG-MNG"),
    MANTA_ISOLANTE("Mantas de Cobertura Isolantes", "ASTM D1048", "TAG-MTA"),
    TAPETE_ISOLANTE("Tapetes Isolantes de Borracha", "ASTM D178-22", "TAG-TAP"),
    BASTAO_MANOBRA("Bastões de Manobra e Salvamento", "ABNT NBR 11854", "TAG-BST"),
    VARA_MANOBRA("Varas de Manobra Telescópicas", "ABNT NBR 11854", "TAG-VAR"),
    /** Tipo antigo (critério próprio do app): mantido para os ensaios já registrados */
    ATERRAMENTO_TEMPORARIO("Conjuntos de Aterramento Temporário (EPC) — antigo", "ABNT NBR IEC 61230 / ASTM F855", "TAG-ATR"),
    /** Critério da vara de manobra (ABNT NBR 11854 — 50 kV, limite em µA pelo diâmetro) */
    ATERRAMENTO_COM_BASTAO("Aterramento Temporário com Bastão", "ABNT NBR 11854 (critério de vara de manobra)", "TAG-ATB"),
    /** Critério da ponteira de prova (ABNT NBR 6881 — 1,2 kV, 5,0 mA) */
    ATERRAMENTO_SEM_BASTAO("Aterramento Temporário sem Bastão", "ABNT NBR 6881 (critério de ponteira de prova)", "TAG-ATS"),
    CAPACETE_CLASSE_B("Capacetes de Segurança Classe B (Classe 2)", "ABNT NBR 8221 / ANSI Z89.1", "TAG-CAP"),
    BOTA_DIELETRICA("Calçados / Botas Dielétricas", "ABNT NBR 16603 / ASTM F2413", "TAG-CAL"),
    FERRAMENTA_ISOLADA("Ferramentas Manuais Isoladas 1000V", "ABNT NBR 9699 / IEC 60900", "TAG-FER"),
    ESCADA_ISOLADA("Escadas Isoladas de Fibra de Vidro (PRFV)", "EN 50528:2024 / ABNT NBR IEC 61478", "TAG-ESC"),
    DETECTOR_TENSAO("Detectores de Tensão Capacitivos", "ABNT NBR IEC 61243-1", "TAG-DET"),
    DETECTOR_INDUCAO("Detectores de Tensão por Indução", "Especificações do fabricante (1 kV a 50 kV)", "TAG-DTI"),
    PONTEIRA_PROVA("Ponteiras de Prova e Cabos", "ABNT NBR 6881", "TAG-PNT"),
    OUTRO("Outros Dispositivos Dielétricos", "Norma Geral NR-10", "TAG-EQP");

    fun suggestTag(clientName: String? = null, existingTags: Collection<String> = emptyList()): String {
        val shortPrefix = this.tagPrefix.removePrefix("TAG-")
        val clientWord = clientName?.trim()?.split(" ", "-", "_", ".")?.firstOrNull { it.isNotBlank() }?.uppercase()
        val clientTag = if (!clientWord.isNullOrEmpty() && clientWord.length in 2..6 && clientWord != "CLIENTE") clientWord else null
        val targetPrefix = if (clientTag != null) "$shortPrefix-$clientTag" else this.tagPrefix

        val matchingNumbers = existingTags.mapNotNull { tag ->
            val clean = tag.trim().uppercase()
            if (clean.startsWith(targetPrefix)) {
                val remainder = clean.removePrefix(targetPrefix).trim('-', '_', ' ')
                val digits = remainder.takeWhile { it.isDigit() }
                if (digits.isNotEmpty()) digits.toIntOrNull() else null
            } else if (clean.startsWith("TAG-$shortPrefix")) {
                val remainder = clean.removePrefix("TAG-$shortPrefix").trim('-', '_', ' ')
                val digits = remainder.takeWhile { it.isDigit() }
                if (digits.isNotEmpty()) digits.toIntOrNull() else null
            } else if (clean.contains(shortPrefix)) {
                val idx = clean.indexOf(shortPrefix)
                val remainder = clean.substring(idx + shortPrefix.length).trim('-', '_', ' ')
                val digits = remainder.takeWhile { it.isDigit() }
                if (digits.isNotEmpty()) digits.toIntOrNull() else null
            } else null
        }
        val nextNum = if (matchingNumbers.isNotEmpty()) (matchingNumbers.maxOrNull() ?: 0) + 1 else 1
        return if (clientTag != null) {
            String.format(java.util.Locale.US, "%s-%03d", targetPrefix, nextNum)
        } else {
            String.format(java.util.Locale.US, "%s-%02d", targetPrefix, nextNum)
        }
    }

    companion object {
        /** Tipos oferecidos em novos cadastros e ensaios (sem o aterramento antigo). */
        val selectable: List<EquipmentType> get() = entries.filter { it != ATERRAMENTO_TEMPORARIO }

        fun fromString(value: String): EquipmentType {
            val clean = value.trim()
            // correspondência exata primeiro ("aterramento_com_bastao" não pode cair no tipo antigo)
            return entries.find {
                it.name.equals(clean, ignoreCase = true) ||
                it.name.replace("_", "").equals(clean.replace("_", "").replace(" ", ""), ignoreCase = true) ||
                it.label.equals(clean, ignoreCase = true)
            } ?: when {
                clean.contains("ATERRAMENTO", ignoreCase = true) && clean.contains("SEM", ignoreCase = true) -> ATERRAMENTO_SEM_BASTAO
                clean.contains("ATERRAMENTO", ignoreCase = true) && clean.contains("BAST", ignoreCase = true) -> ATERRAMENTO_COM_BASTAO
                clean.contains("ATERRAMENTO", ignoreCase = true) -> ATERRAMENTO_TEMPORARIO
                else -> LUVA_ISOLANTE
            }
        }
    }
}

enum class DielectricClass(val label: String, val maxUseVoltageAC: Double, val testVoltageAC: Double) {
    CLASS_00("00", 0.5, 2.5),
    CLASS_0("0", 1.0, 5.0),
    CLASS_1("1", 7.5, 10.0),
    CLASS_2("2", 17.0, 20.0),
    CLASS_3("3", 26.5, 30.0),
    CLASS_4("4", 36.0, 40.0);

    companion object {
        fun fromString(value: String): DielectricClass {
            val clean = value.replace("Classe", "", ignoreCase = true).trim()
            return entries.find { it.label.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true) }
                ?: CLASS_2
        }
    }
}

enum class EquipmentStatus(val label: String) {
    EM_USO("Em Uso / Aprovado"),
    DISPONIVEL("Disponível"),
    EM_MANUTENCAO("Em Manutenção"),
    FORA_DE_SERVICO("Fora de Serviço (Vencido)"),
    REPROVADO("Reprovado"),
    DESCARTADO("Descartado")
}

enum class TestResult {
    APROVADO,
    REPROVADO,
    PENDENTE
}

enum class ServiceOrderStatus(val label: String) {
    ABERTA("Aberta"),
    AGENDADA("Agendada"),
    EM_EXECUCAO("Em Execução"),
    CONCLUIDA("Concluída"),
    CANCELADA("Cancelada")
}

data class ManualToolItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val isApproved: Boolean = true,
    val rejectionReason: String = "",
    val isSelected: Boolean = true
) {
    val maxLeakageCurrentLimit_mA: Double
        get() = quantity * 1.0

    fun toDisplayText(): String {
        val statusText = if (isApproved) "APROVADO" else "REPROVADO${if (rejectionReason.isNotBlank()) " ($rejectionReason)" else ""}"
        return "$name (Qtd: $quantity un. | Limite: ${quantity * 1.0} mA) - $statusText"
    }

    fun toEncodedString(): String {
        val reason = rejectionReason.replace("|", "/").replace(";", ",")
        val status = if (isApproved) "OK" else "REJ"
        return "$name|$quantity|$status|$reason"
    }

    companion object {
        fun fromEncodedString(str: String): ManualToolItem? {
            val parts = str.split("|")
            if (parts.size < 3) return null
            val name = parts[0].trim()
            val qty = (parts[1].trim().toIntOrNull() ?: 1).coerceAtLeast(1)
            val isApp = parts[2].trim().equals("OK", ignoreCase = true) || parts[2].trim().equals("APROVADO", ignoreCase = true)
            val reason = if (parts.size >= 4) parts[3].trim() else ""
            return ManualToolItem(
                name = name,
                quantity = qty,
                isApproved = isApp,
                rejectionReason = reason,
                isSelected = true
            )
        }

        fun parseListFromNotes(notes: String): List<ManualToolItem> {
            if (notes.isBlank()) return emptyList()

            // 1. Format FERRAMENTAS_V2:...:::END_TOOLS:::
            if (notes.contains("FERRAMENTAS_V2:")) {
                val encodedBlock = notes.substringAfter("FERRAMENTAS_V2:").substringBefore(":::END_TOOLS:::")
                val parsed = encodedBlock.split(";;;").mapNotNull { itemStr ->
                    if (itemStr.isNotBlank()) fromEncodedString(itemStr) else null
                }
                if (parsed.isNotEmpty()) return parsed
            }

            // 2. Format FERRAMENTAS_V1:... or generic FERRAMENTAS:...
            if (notes.contains("FERRAMENTAS_V1:") || (notes.contains("FERRAMENTAS:") && !notes.contains("FERRAMENTAS_V2:"))) {
                val rawEncoded = notes.substringAfter("FERRAMENTAS:").substringAfter("FERRAMENTAS_V1:").substringBefore(":::END_TOOLS:::").substringBefore("\n\n").trim()
                val parsed = rawEncoded.split(";;;").mapNotNull { itemStr ->
                    if (itemStr.isNotBlank()) fromEncodedString(itemStr) else null
                }
                if (parsed.isNotEmpty()) return parsed
            }

            // 3. Human readable list format with headers like "Ferramentas Ensaiadas", "Ferramentas Ensaidas", etc.
            val lower = notes.lowercase()
            val headerKey = listOf(
                "ferramentas ensaiadas",
                "ferramentas ensaidas",
                "ferramentas manuais",
                "ferramentas isoladas",
                "conjunto de ferramentas",
                "relação de ferramentas",
                "relacao de ferramentas",
                "composição do conjunto",
                "composicao do conjunto"
            ).firstOrNull { lower.contains(it) }

            val textToScan = if (headerKey != null) {
                val afterHeader = notes.substring(lower.indexOf(headerKey))
                afterHeader.substringAfter(":").substringBefore("FERRAMENTAS_V2:").substringBefore(":::END_TOOLS:::").trim()
            } else {
                notes
            }

            val tokens = textToScan.split(Regex("[;\n]+")).map { it.trim() }.filter { it.isNotBlank() }
            val parsedFromTokens = tokens.mapNotNull { token ->
                parseSingleToolToken(token)
            }
            if (parsedFromTokens.isNotEmpty()) {
                return parsedFromTokens
            }

            return emptyList()
        }

        fun isManualTool(equipmentType: String, equipmentTag: String = ""): Boolean {
            val typeUpper = equipmentType.uppercase()
            val tagUpper = equipmentTag.uppercase()
            return typeUpper == EquipmentType.FERRAMENTA_ISOLADA.name ||
                    typeUpper.contains("FERRAMENTA") ||
                    tagUpper.startsWith("TAG-FER") ||
                    tagUpper.startsWith("FER-") ||
                    tagUpper.startsWith("FER_")
        }

        private fun parseSingleToolToken(token: String): ManualToolItem? {
            val clean = token.removePrefix("-").removePrefix("*").removePrefix("•").removePrefix("▸").trim()
            if (clean.isBlank()) return null

            val isToolWord = listOf(
                "chave", "alicate", "soquete", "torquímetro", "torquimetro", "faca", "desencapador",
                "martelo", "ponteira", "ferramenta", "catraca", "extensão", "extensao"
            ).any { clean.contains(it, ignoreCase = true) }

            // Must strictly be a tool item to avoid capturing generic notes of other equipment
            if (!isToolWord) {
                return null
            }

            val isRej = clean.contains("REPROVADO", ignoreCase = true) ||
                    clean.contains("NÃO CONFORME", ignoreCase = true) ||
                    clean.contains("NAO CONFORME", ignoreCase = true) ||
                    clean.contains("CONDENADO", ignoreCase = true) ||
                    clean.contains("FALHA", ignoreCase = true)

            val qtyRegex = Regex("""(?:qtd:?\s*|(?:\(|\s))(\d+)\s*(?:un|peça|peca|x)""", RegexOption.IGNORE_CASE)
            val qtyMatch = qtyRegex.find(clean)
            val qty = qtyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

            var reason = ""
            if (isRej) {
                val reasonRegex = Regex("""(?:motivo:?|motivo da reprovação:?|causa:?)\s*([^)]+)""", RegexOption.IGNORE_CASE)
                val m = reasonRegex.find(clean)
                if (m != null) {
                    reason = m.groupValues[1].trim()
                } else if (clean.contains("(") && clean.contains(")")) {
                    val insideParens = clean.substringAfterLast("(").substringBeforeLast(")").trim()
                    if (!insideParens.contains("un", ignoreCase = true) &&
                        !insideParens.contains("qtd", ignoreCase = true) &&
                        !insideParens.contains("reprovado", ignoreCase = true)
                    ) {
                        reason = insideParens
                    }
                }
            }

            var namePart = clean
                .replace(Regex("""^[0-9]+\.\s*"""), "")
                .replace(Regex("""(?i)\(qtd:?\s*\d+[^)]*\)"""), "")
                .replace(Regex("""(?i)\(\d+\s*un[^)]*\)"""), "")
                .replace(Regex("""(?i)-\s*aprovado.*"""), "")
                .replace(Regex("""(?i)-\s*reprovado.*"""), "")
                .replace(Regex("""(?i)\baprovado\b.*"""), "")
                .replace(Regex("""(?i)\breprovado\b.*"""), "")
                .replace(Regex("""(?i)\bconforme\b.*"""), "")
                .replace(Regex("""(?i)\bnão conforme\b.*"""), "")
                .replace(Regex("""(?i)\bnao conforme\b.*"""), "")
                .replace(Regex("""\|\s*limite:.*"""), "")
                .trim(' ', '-', ':', '|', '(', ')')

            if (namePart.isBlank()) {
                namePart = "Ferramenta Isolada 1000V"
            }

            return ManualToolItem(
                name = namePart,
                quantity = qty.coerceAtLeast(1),
                isApproved = !isRej,
                rejectionReason = reason,
                isSelected = true
            )
        }

        fun getToolsForTest(test: TestRecordEntity): List<ManualToolItem> {
            // Strictly check that the equipment is actually a manual tool!
            // Other equipment (luvas, mangas, mantas, tapetes, bastões, etc.) must NEVER have tool breakdown
            if (!isManualTool(test.equipmentType, test.equipmentTag)) {
                return emptyList()
            }

            val fromNotes = parseListFromNotes(test.technicalNotes)
            if (fromNotes.isNotEmpty()) return fromNotes

            val fromRationale = parseListFromNotes(test.resultRationale)
            if (fromRationale.isNotEmpty()) return fromRationale

            val isFerramenta = test.equipmentType.contains("FERRAMENTA", ignoreCase = true) ||
                    test.equipmentTag.contains("FER", ignoreCase = true)

            if (!isFerramenta) return emptyList()

            // Standard insulated hand tools set when specific composition wasn't encoded
            val isOverallApproved = test.result == "APROVADO"
            val isRej = !isOverallApproved
            val rationaleLower = test.resultRationale.lowercase()

            val rejAlicate = isRej && (rationaleLower.contains("alicate") || (!rationaleLower.contains("fenda") && !rationaleLower.contains("phillips")))
            val rejFenda = isRej && rationaleLower.contains("fenda") && !rationaleLower.contains("cruzada") && !rationaleLower.contains("phillips")
            val rejPhillips = isRej && (rationaleLower.contains("cruzada") || rationaleLower.contains("phillips"))

            val reason = if (isRej) {
                if (test.resultRationale.isNotBlank() && !test.resultRationale.contains("conforme norma", ignoreCase = true)) {
                    test.resultRationale.take(60)
                } else "Isolação danificada / Fuga acima de 1,0 mA"
            } else ""

            return listOf(
                ManualToolItem(
                    name = "Chave de Fenda Isolada 1000V",
                    quantity = 1,
                    isApproved = if (isRej) !rejFenda else true,
                    rejectionReason = if (isRej && (rejFenda || (!rejAlicate && !rejPhillips))) reason else "",
                    isSelected = true
                ),
                ManualToolItem(
                    name = "Chave de Fenda Cruzada (Phillips) Isolada 1000V",
                    quantity = 1,
                    isApproved = if (isRej) !rejPhillips else true,
                    rejectionReason = if (isRej && rejPhillips) reason else "",
                    isSelected = true
                ),
                ManualToolItem(
                    name = "Alicate Universal Isolado 1000V",
                    quantity = 1,
                    isApproved = if (isRej) !rejAlicate else true,
                    rejectionReason = if (isRej && rejAlicate) reason else "",
                    isSelected = true
                )
            )
        }

        fun encodeListToNotes(tools: List<ManualToolItem>): String {
            val validTools = tools.filter { it.isSelected }
            val encoded = validTools.joinToString(";;;") { it.toEncodedString() }
            return "FERRAMENTAS_V2:$encoded:::END_TOOLS:::"
        }

        fun getApprovedTools(tools: List<ManualToolItem>): List<ManualToolItem> =
            tools.filter { it.isSelected && it.isApproved }

        fun getRejectedTools(tools: List<ManualToolItem>): List<ManualToolItem> =
            tools.filter { it.isSelected && !it.isApproved }
    }
}

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String, // Nome Fantasia
    val legalName: String = "", // Razão Social
    val cnpj: String = "",
    val creaCompanyRegister: String = "",
    val address: String = "",
    val city: String = "Brasília",
    val state: String = "DF",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val logoUri: String = "",
    val createdAt: String = ""
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val companyName: String = "",
    val name: String,
    val email: String,
    val password: String = "", // hash PBKDF2 (PasswordHasher) para o login offline; nunca texto puro
    val role: String = "Engenheiro Responsável", // Engenheiro Responsável, Técnico Executor, Analista de Ensaios, Inspetor SST
    val registrationCouncil: String = "", // CREA ou CFT
    val phone: String = "",
    val signatureImageUri: String = "",
    val createdAt: String = ""
)

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val razaoSocial: String,
    val nomeFantasia: String = "",
    val cnpj: String,
    val inscricaoEstadual: String = "",
    val address: String = "",
    val city: String = "São Paulo",
    val state: String = "SP",
    val cep: String = "01000-000",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val contactName: String = "",
    val observacoes: String = "",
    val companyId: String = "comp-jvm-01",
    val companyName: String = "JVM Engenharia",
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: String = SyncStatus.PENDING
)

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val uuid: String = UUID.randomUUID().toString(),
    val clientId: String,
    val clientName: String,
    val type: String, // from EquipmentType.name
    val tag: String,
    val qrCode: String = tag,
    val manufacturer: String = "Homologado",
    val model: String = "Padrão",
    val serialNumber: String = "S/N",
    val assetNumber: String = "",
    val caNumber: String = "",
    val dielectricClass: String = "2",
    val sizeOrLength: String = "",
    val gloveLength_mm: Int = 360,
    val collaboratorName: String = "",
    val collaboratorRegistration: String = "",
    val collaboratorSector: String = "",
    val serviceOrderId: String = "",
    val serviceOrderNumber: String = "",
    val acquisitionDate: String = "",
    val lastTestDate: String = "",
    val nextTestDueDate: String = "",
    val retestIntervalMonths: Int = 6,
    val status: String = EquipmentStatus.DISPONIVEL.name,
    val notes: String = "",
    val companyId: String = "comp-jvm-01",
    val companyName: String = "JVM Engenharia",
    val userId: String = "user-jvm-01",
    val userName: String = "Eng. João Carlos",
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: String = SyncStatus.PENDING
)

@Entity(tableName = "service_orders")
data class ServiceOrderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val osNumber: String,
    val clientId: String,
    val clientName: String,
    val openDate: String,
    val scheduledDate: String = "",
    val technicianId: String = "",
    val technicianName: String = "Analista Executor",
    val description: String = "",
    val artNumber: String = "",
    val equipmentIds: String = "", // Comma separated list of IDs
    val status: String = ServiceOrderStatus.ABERTA.name,
    val companyId: String = "comp-jvm-01",
    val companyName: String = "JVM Engenharia",
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: String = SyncStatus.PENDING
)

@Entity(tableName = "test_records")
data class TestRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val uuid: String = UUID.randomUUID().toString(),
    val testNumber: String,
    val reportNumber: String,
    val certificateNumber: String = "",
    val clientId: String,
    val clientName: String,
    val collaboratorName: String = "",
    val collaboratorRegistration: String = "",
    val collaboratorSector: String = "",
    val equipmentId: String,
    val equipmentTag: String,
    val equipmentType: String,
    val equipmentClass: String,
    val equipmentSerial: String = "",
    val equipmentCa: String = "",
    val serviceOrderId: String = "",
    val serviceOrderNumber: String = "",
    val artNumber: String = "",
    val technicianId: String = "",
    val technicianName: String = "Eng. João Carlos Evaristo Guedes Nunes",
    val technicianCftOrCrea: String = "",
    val techResponsibleId: String = "",
    val techResponsibleName: String = "Eng. João Carlos Evaristo Guedes Nunes",
    val techResponsibleCrea: String = "CREA 24481/D-DF",
    val testDate: String,
    val testTime: String = "10:00",
    val location: String = "Laboratório Móvel JVM",
    val normCode: String = "ABNT NBR 16295 Tabela 4",
    val procedureCode: String = "PR-JVM-LAB-01 Rev.08",
    val appliedVoltage_kV: Double = 20.0,
    val voltageType: String = "AC",
    val applicationDurationSeconds: Int = 60,
    val measuredLeakageCurrent_mA: Double = 11.4,
    val leakageCurrentLimit_mA: Double = 16.0,
    val currentUnit: String = "mA",
    val withstandWithoutPuncture: Boolean = true,
    val gloveLength_mm: Int = 360,
    val temperatureC: Double = 22.4,
    val relativeHumidityPercent: Double = 54.0,
    val visualInspectionSummary: String = "Todos os itens conformes",
    val result: String = TestResult.APROVADO.name,
    val resultRationale: String = "",
    val technicalNotes: String = "",
    val retestDueDate: String,
    val technicianSignature: String = "",
    val techResponsibleSignature: String = "",
    val validationCode: String,
    val documentHash: String = "",
    val photoUris: String = "", // Comma-separated or JSON list of photo paths/URIs
    val companyId: String = "comp-jvm-01",
    val companyName: String = "JVM Engenharia",
    val userId: String = "user-jvm-01",
    val userName: String = "Eng. João Carlos",
    val createdAt: String = "",
    val updatedAt: String = "",
    val remoteMedia: String = "", // JSON {caminho local: URL pública no bucket jvm-evidencias}
    /** Detector por indução: [{voltage_kV, distance_cm, detected}] — mesmo formato da web (inductionPoints) */
    val inductionPointsJson: String = "",
    /** Bastões/varas e escada: quantidade de pontos ensaiados (limite de fuga = pontos × limite da tabela/norma) */
    val stickTestPoints: Int = 1,
    val syncStatus: String = SyncStatus.PENDING
)

@Entity(tableName = "lab_instruments")
data class LabInstrumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tag: String = "",
    val type: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val assetNumber: String = "",
    val measurementRange: String = "",
    val calibrationCertNumber: String,
    val calibrationDate: String,
    val calibrationDueDate: String = "",
    val calibrationExpiryDate: String = "",
    val calibrationLab: String = "Laboratório RBC Acreditado Cgcre",
    val maxVoltageCapacity_kV: Double = 100.0,
    val active: Boolean = true,
    val notes: String = "",
    val calibrationCertificateUri: String = "",
    val calibrationCertificateFileName: String = "",
    val syncStatus: String = SyncStatus.PENDING
)

@Entity(tableName = "norms")
data class NormCriterionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val applicableEquipmentType: String = "",
    val dielectricClass: String = "",
    val testVoltage_kV: Double = 20.0,
    val voltageType: String = "AC",
    val durationSeconds: Int = 60,
    val maxLeakageCurrent_mA: Double = 16.0,
    val currentUnit: String = "mA",
    val retestIntervalMonths: Int = 6,
    val notes: String = ""
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val action: String,
    val userName: String = "Administrador",
    val userRole: String = "Analista",
    val timestamp: String = "",
    val dateTime: String = "",
    val entityType: String,
    val entityId: String = "",
    val details: String = "",
    val description: String = ""
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val id: String = "company_singleton",
    val name: String = "JVM Engenharia",
    val legalName: String = "JVM Engenharia Ltda.",
    val cnpj: String = "29.894.500/0001-04",
    val creaCompanyRegister: String = "CREA-DF 1620-PJ",
    val address: String = "Brasília - DF",
    val phone: String = "(61) 98115-0000",
    val email: String = "joao.nunes@jvmengenharia.com.br",
    val website: String = "www.jvmengenharia.com.br",
    val hostingerPlatformUrl: String = "https://mediumvioletred-bison-595566.hostingersite.com",
    val technicalResponsibleName: String = "Eng. João Carlos Evaristo Guedes Nunes",
    val technicalResponsibleCrea: String = "CREA 24481/D-DF",
    val technicalResponsibleSignatureImage: String = "",
    val defaultTechnicianName: String = "Eng. João Carlos Evaristo Guedes Nunes",
    val defaultTechnicianCftOrCrea: String = "CREA 24481/D-DF",
    val defaultTechnicianSignatureImage: String = "",
    val defaultRetestMonths: Int = 6,
    val reportIntroText: String = "",
    val reportObjectiveText: String = "",
    val reportMethodologyText: String = "",
    val reportDevelopmentText: String = "",
    val reportConclusionText: String = "",
    val companyLogoUri: String = "",
    val letterheadUri: String = "",
    val useLetterhead: Boolean = false,
    val letterheadOpacity: Float = 1.0f,
    val letterheadApplyMode: String = "ALL_PAGES", // ALL_PAGES, FIRST_PAGE_ONLY, INNER_PAGES_ONLY
    val letterheadHeaderMode: String = "INTEGRATED", // INTEGRATED, CLEAN_HEADER
    val gloveProcedurePhotoUri: String = "", // Custom photo for the glove test procedure
    val supabaseUrl: String = "https://cdtbzbshylrcprvmjpgc.supabase.co/rest/v1/",
    val supabaseAnonKey: String = "sb_publishable_j3sUJcAb-zBEQI_S09u2Cg_X1-WJ-8M",
    val supabaseAutoSync: Boolean = true,
    val supabaseLastSyncTimestamp: String = "",
    /** Configurações da empresa no formato da web (companies.lab_info), em JSON */
    val labInfoJson: String = ""
)

/**
 * Situação do registro em relação ao Supabase.
 * PENDING: alterado no aparelho e ainda não enviado.
 * SYNCED: igual ao da nuvem (baixado ou já enviado).
 * LEGACY: gravado antes da versão com controle de envio (enviado uma vez
 * sem sobrescrever registros que a plataforma web alterou depois).
 */
object SyncStatus {
    const val PENDING = "pending"
    const val SYNCED = "synced"
    const val LEGACY = "legacy"
}

/** Exclusão feita no aparelho que ainda não chegou à nuvem (aparelho offline). */
@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey val key: String, // "<tabela>:<id>"
    val tableName: String,
    val entityId: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Faixa de numeração reservada no servidor (RPC jvm_reserve_numbers) para uso
 * mesmo sem internet: test, report, certificate, os.
 */
@Entity(tableName = "number_blocks")
data class NumberBlockEntity(
    @PrimaryKey val kind: String,
    val nextValue: Long,
    val endValue: Long
)
