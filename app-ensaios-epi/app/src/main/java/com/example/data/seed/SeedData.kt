package com.example.data.seed

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

object SeedData {

    val defaultCompanies = listOf(
        CompanyEntity(
            id = "comp-jvm-01",
            name = "JVM Engenharia",
            legalName = "JVM Engenharia Ltda.",
            cnpj = "29.894.500/0001-04",
            creaCompanyRegister = "CREA-DF 1620-PJ",
            address = "Brasília - DF",
            city = "Brasília",
            state = "DF",
            phone = "(61) 98115-0000",
            email = "jcegnunes@gmail.com",
            website = "www.jvmengenharia.com.br",
            createdAt = "01/01/2026"
        ),
        CompanyEntity(
            id = "comp-eletr-02",
            name = "EletroSafe Inspeções",
            legalName = "EletroSafe Segurança e Ensaios Eireli",
            cnpj = "45.123.890/0001-12",
            creaCompanyRegister = "CREA-SP 9871-PJ",
            address = "Av. Paulista, 1000",
            city = "São Paulo",
            state = "SP",
            phone = "(11) 3210-4400",
            email = "contato@eletrosafe.com.br",
            website = "www.eletrosafe.com.br",
            createdAt = "15/01/2026"
        )
    )

    val defaultUsers = listOf(
        UserEntity(
            id = "user-jvm-01",
            companyId = "comp-jvm-01",
            companyName = "JVM Engenharia",
            name = "Eng. João Carlos Evaristo Guedes Nunes",
            email = "jcegnunes@gmail.com",
            password = "123",
            role = "Engenheiro Responsável",
            registrationCouncil = "CREA 24481/D-DF",
            phone = "(61) 98115-0000",
            createdAt = "01/01/2026"
        ),
        UserEntity(
            id = "user-jvm-02",
            companyId = "comp-jvm-01",
            companyName = "JVM Engenharia",
            name = "Lucas Pereira dos Santos",
            email = "lucas.tecnico@jvmengenharia.com.br",
            password = "123",
            role = "Técnico Executor",
            registrationCouncil = "CFT 192834-BR",
            phone = "(61) 98222-1111",
            createdAt = "10/01/2026"
        ),
        UserEntity(
            id = "user-eletr-01",
            companyId = "comp-eletr-02",
            companyName = "EletroSafe Inspeções",
            name = "Engª. Mariana Duarte Silva",
            email = "mariana@eletrosafe.com.br",
            password = "123",
            role = "Engenheiro Responsável",
            registrationCouncil = "CREA 506921-SP",
            phone = "(11) 99887-3322",
            createdAt = "15/01/2026"
        )
    )

    val defaultCompany = AppSettingEntity(
        id = "company_singleton",
        name = "JVM Engenharia",
        legalName = "JVM Engenharia Ltda.",
        cnpj = "29.894.500/0001-04",
        creaCompanyRegister = "CREA-DF 1620-PJ",
        address = "Brasília - DF",
        phone = "(61) 98115-0000",
        email = "joao.nunes@jvmengenharia.com.br",
        website = "www.jvmengenharia.com.br",
        hostingerPlatformUrl = "https://mediumvioletred-bison-595566.hostingersite.com",
        technicalResponsibleName = "Eng. João Carlos Evaristo Guedes Nunes",
        technicalResponsibleCrea = "CREA 24481/D-DF",
        defaultTechnicianName = "Eng. João Carlos Evaristo Guedes Nunes",
        defaultTechnicianCftOrCrea = "CREA 24481/D-DF",
        defaultRetestMonths = 6
    )

    val sampleClients = listOf(
        ClientEntity(
            id = "cli-01",
            razaoSocial = "CPFL Energia S.A.",
            nomeFantasia = "CPFL Paulista",
            cnpj = "02.429.144/0001-93",
            address = "Rodovia Eng. Miguel Noel Nascentes Burnier, 1755",
            city = "Campinas",
            state = "SP",
            cep = "13088-140",
            contactPhone = "(19) 3756-8000",
            contactEmail = "seguranca@cpfl.com.br",
            contactName = "Eng. Marcelo Santos (Gerente SST)"
        ),
        ClientEntity(
            id = "cli-02",
            razaoSocial = "Enel Distribuição São Paulo S.A.",
            nomeFantasia = "Enel SP",
            cnpj = "61.695.227/0001-93",
            address = "Praça Ramos de Azevedo, 254",
            city = "São Paulo",
            state = "SP",
            cep = "01037-010",
            contactPhone = "(11) 2195-2000",
            contactEmail = "sst.operacional@eneldistribuicao.com.br",
            contactName = "Roberta Faria (Coord. Segurança)"
        ),
        ClientEntity(
            id = "cli-03",
            razaoSocial = "Eletrobras Furnas Centrais Elétricas S.A.",
            nomeFantasia = "Furnas",
            cnpj = "23.274.194/0001-19",
            address = "Rua Real Grandeza, 219",
            city = "Rio de Janeiro",
            state = "RJ",
            cep = "22281-900",
            contactPhone = "(21) 2528-3111",
            contactEmail = "inspecoes.furnas@eletrobras.com",
            contactName = "Dr. Luiz Gustavo Prado"
        )
    )

    val sampleServiceOrders = listOf(
        ServiceOrderEntity(
            id = "os-2026-001",
            osNumber = "OS-2026-001",
            clientId = "cli-01",
            clientName = "CPFL Paulista",
            openDate = "10/02/2026",
            scheduledDate = "12/02/2026",
            technicianName = "Eng. João Carlos Evaristo Guedes Nunes",
            description = "Ensaio Semestral de Rotina NR-10 em EPIs de Linha Viva 13.8kV",
            artNumber = "ART-DF-2026-0982441",
            status = "CONCLUIDA"
        ),
        ServiceOrderEntity(
            id = "os-2026-002",
            osNumber = "OS-2026-002",
            clientId = "cli-02",
            clientName = "Enel SP",
            openDate = "15/02/2026",
            scheduledDate = "18/02/2026",
            technicianName = "Eng. João Carlos Evaristo Guedes Nunes",
            description = "Inspeção Dielétrica Periódica de Luvas, Mantas e Bastões de Manobra",
            artNumber = "ART-DF-2026-1123490",
            status = "EM_EXECUCAO"
        )
    )

    val sampleEquipment = listOf(
        EquipmentEntity(
            id = "eq-01",
            clientId = "cli-01",
            clientName = "CPFL Paulista",
            type = "LUVA_ISOLANTE",
            tag = "LUV-CPFL-042",
            qrCode = "LUV-CPFL-042",
            manufacturer = "Orion",
            serialNumber = "SN-2025-9812",
            caNumber = "CA 29.742",
            dielectricClass = "2",
            gloveLength_mm = 360,
            collaboratorName = "Carlos Alberto Silva",
            collaboratorRegistration = "MAT-4820",
            collaboratorSector = "Subestações AT",
            serviceOrderId = "os-2026-001",
            serviceOrderNumber = "OS-2026-001",
            lastTestDate = "12/02/2026",
            nextTestDueDate = "12/08/2026",
            retestIntervalMonths = 6,
            status = "EM_USO"
        ),
        EquipmentEntity(
            id = "eq-02",
            clientId = "cli-01",
            clientName = "CPFL Paulista",
            type = "MANGA_ISOLANTE",
            tag = "MNG-CPFL-018",
            qrCode = "MNG-CPFL-018",
            manufacturer = "Salisbury by Honeywell",
            serialNumber = "SAL-77821",
            caNumber = "CA 14.882",
            dielectricClass = "2",
            collaboratorName = "Carlos Alberto Silva",
            collaboratorRegistration = "MAT-4820",
            collaboratorSector = "Subestações AT",
            serviceOrderId = "os-2026-001",
            serviceOrderNumber = "OS-2026-001",
            lastTestDate = "12/02/2026",
            nextTestDueDate = "12/08/2026",
            retestIntervalMonths = 6,
            status = "EM_USO"
        ),
        EquipmentEntity(
            id = "eq-03",
            clientId = "cli-02",
            clientName = "Enel SP",
            type = "BASTAO_MANOBRA",
            tag = "BST-ENEL-104",
            qrCode = "BST-ENEL-104",
            manufacturer = "Ritz do Brasil",
            serialNumber = "RTZ-99120",
            caNumber = "",
            dielectricClass = "4",
            collaboratorName = "Equipe Linha Viva Leste",
            collaboratorRegistration = "EQ-04",
            collaboratorSector = "Distribuição",
            serviceOrderId = "os-2026-002",
            serviceOrderNumber = "OS-2026-002",
            lastTestDate = "18/02/2026",
            nextTestDueDate = "18/02/2027",
            retestIntervalMonths = 12,
            status = "EM_USO"
        ),
        EquipmentEntity(
            id = "eq-04",
            clientId = "cli-02",
            clientName = "Enel SP",
            type = "LUVA_ISOLANTE",
            tag = "LUV-ENEL-099",
            qrCode = "LUV-ENEL-099",
            manufacturer = "Orion",
            serialNumber = "SN-2024-1102",
            caNumber = "CA 29.742",
            dielectricClass = "2",
            gloveLength_mm = 360,
            collaboratorName = "Fernando Souza",
            collaboratorRegistration = "MAT-9011",
            collaboratorSector = "Redes Aéreas",
            serviceOrderId = "os-2026-002",
            serviceOrderNumber = "OS-2026-002",
            lastTestDate = "10/07/2025",
            nextTestDueDate = "10/01/2026", // Vencido propositalmente para demonstrar alerta
            retestIntervalMonths = 6,
            status = "FORA_DE_SERVICO"
        ),
        EquipmentEntity(
            id = "eq-05",
            clientId = "cli-01",
            clientName = "CPFL Paulista",
            type = "ATERRAMENTO_TEMPORARIO",
            tag = "ATR-CPFL-005",
            qrCode = "ATR-CPFL-005",
            manufacturer = "Ritz do Brasil",
            serialNumber = "RTZ-ATR-3419",
            caNumber = "EPC NR-10",
            dielectricClass = "2",
            collaboratorName = "Equipe Manutenção Subestação",
            collaboratorRegistration = "EQP-SUB-02",
            collaboratorSector = "Subestações AT",
            serviceOrderId = "os-2026-001",
            serviceOrderNumber = "OS-2026-001",
            lastTestDate = "15/02/2026",
            nextTestDueDate = "15/02/2027",
            retestIntervalMonths = 12,
            status = "EM_USO"
        )
    )

    val sampleTests = listOf(
        TestRecordEntity(
            id = "ens-01",
            testNumber = "ENS-2026-0001",
            reportNumber = "LAU-2026-0001",
            certificateNumber = "CERT-2026-0001",
            clientId = "cli-01",
            clientName = "CPFL Paulista",
            collaboratorName = "Carlos Alberto Silva",
            collaboratorRegistration = "MAT-4820",
            collaboratorSector = "Subestações AT",
            equipmentId = "eq-01",
            equipmentTag = "LUV-CPFL-042",
            equipmentType = "LUVA_ISOLANTE",
            equipmentClass = "2",
            equipmentSerial = "SN-2025-9812",
            equipmentCa = "CA 29.742",
            serviceOrderId = "os-2026-001",
            serviceOrderNumber = "OS-2026-001",
            artNumber = "ART-DF-2026-0982441",
            technicianName = "Eng. João Carlos Evaristo Guedes Nunes",
            technicianCftOrCrea = "CREA 24481/D-DF",
            techResponsibleName = "Eng. João Carlos Evaristo Guedes Nunes",
            techResponsibleCrea = "CREA 24481/D-DF",
            testDate = "12/02/2026",
            testTime = "09:30",
            location = "Laboratório Móvel JVM",
            normCode = "ABNT NBR 16295 Tabela 4 / IEC 60903",
            appliedVoltage_kV = 20.0,
            voltageType = "AC",
            applicationDurationSeconds = 60,
            measuredLeakageCurrent_mA = 11.4,
            leakageCurrentLimit_mA = 16.0,
            currentUnit = "mA",
            withstandWithoutPuncture = true,
            gloveLength_mm = 360,
            temperatureC = 22.4,
            relativeHumidityPercent = 54.0,
            visualInspectionSummary = "100% Conforme",
            result = "APROVADO",
            resultRationale = "Equipamento plenamente CONFORME com a ABNT NBR 16295 Tabela 4. Aprovado na inspeção visual preliminar, suportou a tensão de ensaio de 20.0 kV por 60s sem perfuração dielétrica, registrando corrente de fuga de 11.4 mA (estritamente abaixo do limite normativo de 16.0 mA).",
            technicalNotes = "",
            retestDueDate = "12/08/2026",
            validationCode = "A8F9C2D1"
        ),
        TestRecordEntity(
            id = "ens-02",
            testNumber = "ENS-2026-0002",
            reportNumber = "LAU-2026-0002",
            certificateNumber = "CERT-2026-0002",
            clientId = "cli-01",
            clientName = "CPFL Paulista",
            collaboratorName = "Carlos Alberto Silva",
            collaboratorRegistration = "MAT-4820",
            collaboratorSector = "Subestações AT",
            equipmentId = "eq-02",
            equipmentTag = "MNG-CPFL-018",
            equipmentType = "MANGA_ISOLANTE",
            equipmentClass = "2",
            equipmentSerial = "SAL-77821",
            equipmentCa = "CA 14.882",
            serviceOrderId = "os-2026-001",
            serviceOrderNumber = "OS-2026-001",
            artNumber = "ART-DF-2026-0982441",
            technicianName = "Eng. João Carlos Evaristo Guedes Nunes",
            technicianCftOrCrea = "CREA 24481/D-DF",
            techResponsibleName = "Eng. João Carlos Evaristo Guedes Nunes",
            techResponsibleCrea = "CREA 24481/D-DF",
            testDate = "12/02/2026",
            testTime = "10:15",
            location = "Laboratório Móvel JVM",
            normCode = "ABNT NBR 10624 / ASTM D1051",
            appliedVoltage_kV = 20.0,
            voltageType = "AC",
            applicationDurationSeconds = 60,
            measuredLeakageCurrent_mA = 13.8,
            leakageCurrentLimit_mA = 18.0,
            currentUnit = "mA",
            withstandWithoutPuncture = true,
            temperatureC = 22.8,
            relativeHumidityPercent = 52.0,
            visualInspectionSummary = "100% Conforme",
            result = "APROVADO",
            resultRationale = "Equipamento plenamente CONFORME com a ABNT NBR 10624 / ASTM D1051. Suportou 20.0 kV por 60s com corrente de fuga de 13.8 mA (abaixo do limite de 18.0 mA).",
            technicalNotes = "",
            retestDueDate = "12/08/2026",
            validationCode = "B3E7A1C9"
        )
    )

    val sampleInstruments = listOf(
        LabInstrumentEntity(
            id = "inst-01",
            tag = "PAD-HIP-01",
            type = "Hipot Dielétrico AC/DC 0-50kV",
            manufacturer = "Megabras Metrologia",
            model = "HP-50AC/DC Pro",
            serialNumber = "MG-88491-SP",
            calibrationCertNumber = "CAL-RBC-2026-0814",
            calibrationDate = "15/01/2026",
            calibrationDueDate = "15/01/2027",
            calibrationExpiryDate = "15/01/2027",
            calibrationLab = "LabMetrologia RBC Acreditado Cgcre",
            maxVoltageCapacity_kV = 50.0
        ),
        LabInstrumentEntity(
            id = "inst-02",
            tag = "PAD-THG-01",
            type = "Termohigrômetro Digital Calibrado",
            manufacturer = "Testo do Brasil",
            model = "Testo 608-H1",
            serialNumber = "TST-349012",
            calibrationCertNumber = "CAL-RBC-2025-9921",
            calibrationDate = "20/11/2025",
            calibrationDueDate = "20/11/2026",
            calibrationExpiryDate = "20/11/2026",
            calibrationLab = "Metrologia Paulistana RBC",
            maxVoltageCapacity_kV = 0.0
        )
    )

    val standardNorms = listOf(
        NormCriterionEntity(
            id = "norm-01",
            code = "ABNT NBR 16295 Tabela 4",
            title = "Luvas de Borracha Isolante para Trabalhos em Tensão",
            description = "Critérios de tensão de ensaio e limites máximos de corrente de fuga por classe e comprimento.",
            applicableEquipmentType = "LUVA_ISOLANTE",
            dielectricClass = "2",
            testVoltage_kV = 20.0,
            voltageType = "AC",
            durationSeconds = 60,
            maxLeakageCurrent_mA = 16.0,
            retestIntervalMonths = 6
        ),
        NormCriterionEntity(
            id = "norm-02",
            code = "ASTM D1048-14",
            title = "Mantas de Borracha Isolante (Rubber Insulating Blankets)",
            description = "Ensaio de rigidez dielétrica e suportabilidade de cobertura protetora de condutores.",
            applicableEquipmentType = "MANTA_ISOLANTE",
            dielectricClass = "2",
            testVoltage_kV = 20.0,
            voltageType = "AC",
            durationSeconds = 60,
            maxLeakageCurrent_mA = 14.0,
            retestIntervalMonths = 12
        ),
        NormCriterionEntity(
            id = "norm-03",
            code = "ASTM F711 / IEC 60855",
            title = "Varas e Bastões de Manobra Isolantes (PRFV) — ASTM F711 / IEC 60855",
            description = "Ensaio dielétrico a 50 kV a uma distância de 150 mm com dois eletrodos de referência por 1 min (60s). Limites de corrente de fuga na faixa de 50 kV: Ø32mm: 5 µA; Ø38mm: 6 µA; Ø51mm: 8 µA; Ø64mm: 10 µA. Critério de aprovação: sem perfuração, disrupção ou aquecimento anormal conforme ASTM F711 e IEC 60855.",
            applicableEquipmentType = "BASTAO_MANOBRA",
            dielectricClass = "4",
            testVoltage_kV = 50.0,
            voltageType = "AC",
            durationSeconds = 60,
            maxLeakageCurrent_mA = 6.0,
            retestIntervalMonths = 12
        ),
        NormCriterionEntity(
            id = "norm-04",
            code = "ABNT NBR 9699 / IEC 60900",
            title = "Ferramentas Manuais Isoladas para Trabalho em Tensão até 1000V AC",
            description = "Ensaio dielétrico imerso de 10.000V (10 kV) por 3 minutos com fuga máxima de 1 mA por ferramenta.",
            applicableEquipmentType = "FERRAMENTA_ISOLADA",
            dielectricClass = "0",
            testVoltage_kV = 10.0,
            voltageType = "AC",
            durationSeconds = 180,
            maxLeakageCurrent_mA = 1.0,
            retestIntervalMonths = 12
        ),
        NormCriterionEntity(
            id = "norm-05",
            code = "EN 50528:2024 / ABNT NBR IEC 61478",
            title = "Escadas Isolantes de Fibra de Vidro para Eletricistas",
            description = "Ensaio de isolamento sob 36 kV (baixa tensão) ou 100 kV (alta tensão) entre degraus consecutivos.",
            applicableEquipmentType = "ESCADA_ISOLADA",
            dielectricClass = "0",
            testVoltage_kV = 36.0,
            voltageType = "AC",
            durationSeconds = 60,
            maxLeakageCurrent_mA = 0.50,
            retestIntervalMonths = 12
        ),
        NormCriterionEntity(
            id = "norm-06",
            code = "ABNT NBR IEC 61230 / ASTM F855",
            title = "Conjuntos de Aterramento Temporário e de Curto-Circuito (EPC)",
            description = "Ensaio de rigidez dielétrica nos bastões isolantes (NBR 16613), integridade dos condutores extraflexíveis e grampos de fase/terra.",
            applicableEquipmentType = "ATERRAMENTO_TEMPORARIO",
            dielectricClass = "2",
            testVoltage_kV = 20.0,
            voltageType = "AC",
            durationSeconds = 60,
            maxLeakageCurrent_mA = 0.20,
            retestIntervalMonths = 12
        )
    )

    val sampleAuditLogs = listOf(
        AuditLogEntity(
            id = "log-01",
            action = "EMISSAO_LAUDO",
            userName = "Eng. João Carlos Evaristo Guedes Nunes",
            userRole = "Analista Executor",
            timestamp = "12/02/2026 09:35:10",
            dateTime = "12/02/2026 09:35:10",
            entityType = "TestRecord",
            entityId = "ens-01",
            details = "Laudo LAU-2026-0001 emitido com aprovação para TAG LUV-CPFL-042.",
            description = "Laudo LAU-2026-0001 emitido com aprovação para TAG LUV-CPFL-042."
        ),
        AuditLogEntity(
            id = "log-02",
            action = "EMISSAO_CERTIFICADO",
            userName = "Eng. João Carlos Evaristo Guedes Nunes",
            userRole = "Responsável Técnico",
            timestamp = "12/02/2026 09:36:00",
            dateTime = "12/02/2026 09:36:00",
            entityType = "Certificate",
            entityId = "ens-01",
            details = "Certificado CERT-2026-0001 assinado digitalmente com validade até 12/08/2026.",
            description = "Certificado CERT-2026-0001 assinado digitalmente com validade até 12/08/2026."
        )
    )
}
