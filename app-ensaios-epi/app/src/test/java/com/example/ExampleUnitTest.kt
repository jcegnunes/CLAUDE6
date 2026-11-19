package com.example

import com.example.data.model.EquipmentType
import com.example.data.model.ManualToolItem
import com.example.data.model.TestResult
import com.example.domain.NormsEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testManualToolsLeakageCalculation_5Tools() {
        val tools = listOf(
            ManualToolItem(name = "Alicate Universal", quantity = 2, isApproved = true),
            ManualToolItem(name = "Chave de Fenda", quantity = 3, isApproved = true)
        )
        val totalQuantity = tools.sumOf { it.quantity }
        assertEquals(5, totalQuantity)

        val input = NormsEngine.EvaluationInput(
            equipmentType = EquipmentType.FERRAMENTA_ISOLADA,
            dielectricClass = "0",
            appliedVoltage_kV = 10.0,
            durationSeconds = 180,
            measuredLeakage_mA = 4.2,
            withstandWithoutPuncture = true,
            visualPassed = true,
            toolsCount = totalQuantity,
            individualToolsRejectedCount = 0
        )

        val result = NormsEngine.evaluate(input)
        assertEquals(5.0, result.leakageLimit_mA, 0.001)
        assertEquals(TestResult.APROVADO, result.result)
    }

    @Test
    fun testManualToolsIndividualRejection_failsEvenIfLeakageUnderLimit() {
        val input = NormsEngine.EvaluationInput(
            equipmentType = EquipmentType.FERRAMENTA_ISOLADA,
            dielectricClass = "0",
            appliedVoltage_kV = 10.0,
            durationSeconds = 180,
            measuredLeakage_mA = 1.5,
            withstandWithoutPuncture = true,
            visualPassed = true,
            toolsCount = 3,
            individualToolsRejectedCount = 1,
            rejectedToolsSummary = "1 ferramenta(s) com não conformidade individual: Alicate de Corte (Isolação danificada)"
        )

        val result = NormsEngine.evaluate(input)
        assertEquals(TestResult.REPROVADO, result.result)
        assertTrue(result.rationale.contains("não conformidade") || result.rationale.contains("REPROVADA"))
    }

    @Test
    fun testManualToolItemSerializationAndParsing() {
        val originalList = listOf(
            ManualToolItem(name = "Alicate Universal", quantity = 2, isApproved = true),
            ManualToolItem(name = "Chave Phillips", quantity = 1, isApproved = false, rejectionReason = "Cabo rachado")
        )

        val encoded = ManualToolItem.encodeListToNotes(originalList)
        val notes = "Ensaio realizado conforme procedimento padrão.\n$encoded"

        val parsed = ManualToolItem.parseListFromNotes(notes)
        assertEquals(2, parsed.size)
        assertEquals("Alicate Universal", parsed[0].name)
        assertEquals(2, parsed[0].quantity)
        assertTrue(parsed[0].isApproved)

        assertEquals("Chave Phillips", parsed[1].name)
        assertEquals(1, parsed[1].quantity)
        assertFalse(parsed[1].isApproved)
        assertEquals("Cabo rachado", parsed[1].rejectionReason)
    }

    @Test
    fun testLaudoPdfFileName_FormatNumberCompanyCollaborator() {
        val test = com.example.data.model.TestRecordEntity(
            testNumber = "ENS-2026-0001",
            reportNumber = "LAU-2026-0001",
            clientId = "c1",
            clientName = "CPFL Paulista",
            collaboratorName = "Carlos Alberto Silva",
            equipmentId = "eq1",
            equipmentTag = "LUV-01",
            equipmentType = "LUVA_ISOLANTE",
            equipmentClass = "2",
            testDate = "2026-09-04",
            retestDueDate = "2027-03-04",
            validationCode = "VAL-123"
        )

        val fileName = com.example.util.PdfExporter.buildLaudoPdfFileName(test)
        assertEquals("LAU-2026-0001_CPFL_Paulista_Carlos_Alberto_Silva.pdf", fileName)
    }

    @Test
    fun testLaudoPdfFileName_AccentsAndSpecialCharactersSanitization() {
        val test = com.example.data.model.TestRecordEntity(
            testNumber = "ENS-2026-0002",
            reportNumber = "LAU-2026/0002",
            clientId = "c2",
            clientName = "Enel Distribuição & Serviços S/A",
            collaboratorName = "João Carlos Conceição",
            equipmentId = "eq2",
            equipmentTag = "MAN-01",
            equipmentType = "MANGA_ISOLANTE",
            equipmentClass = "2",
            testDate = "2026-09-04",
            retestDueDate = "2027-03-04",
            validationCode = "VAL-456"
        )

        val fileName = com.example.util.PdfExporter.buildLaudoPdfFileName(test)
        assertEquals("LAU-2026_0002_Enel_Distribuicao_Servicos_S_A_Joao_Carlos_Conceicao.pdf", fileName)
    }

    @Test
    fun testLaudoPdfFileName_FallbackWhenCollaboratorBlank() {
        val test = com.example.data.model.TestRecordEntity(
            testNumber = "ENS-2026-0003",
            reportNumber = "LAU-2026-0003",
            clientId = "c3",
            clientName = "CEMIG",
            collaboratorName = "",
            equipmentId = "eq3",
            equipmentTag = "VAR-01",
            equipmentType = "VARA_DE_MANOBRA",
            equipmentClass = "2",
            testDate = "2026-09-04",
            retestDueDate = "2027-03-04",
            validationCode = "VAL-789"
        )

        val fileName = com.example.util.PdfExporter.buildLaudoPdfFileName(test)
        assertEquals("LAU-2026-0003_CEMIG_Geral.pdf", fileName)
    }

    @Test
    fun testGloveUnitaryAndPairLeakageLimits() {
        // Classe 2, 360 mm: unitário = 16.0 mA, ensaio (2 luvas) = 32.0 mA
        val unitLimitCl2 = NormsEngine.getGloveUnitaryLeakageLimit("2", 360)
        val pairLimitCl2 = NormsEngine.getGloveMaxLeakageCurrent("2", 360)
        assertEquals(16.0, unitLimitCl2, 0.001)
        assertEquals(32.0, pairLimitCl2, 0.001)

        // Classe 0, 360 mm: unitário = 12.0 mA, ensaio (2 luvas) = 24.0 mA
        val unitLimitCl0 = NormsEngine.getGloveUnitaryLeakageLimit("0", 360)
        val pairLimitCl0 = NormsEngine.getGloveMaxLeakageCurrent("0", 360)
        assertEquals(12.0, unitLimitCl0, 0.001)
        assertEquals(24.0, pairLimitCl0, 0.001)

        // Classe 4, 410 mm: unitário = 22.0 mA, ensaio (2 luvas) = 44.0 mA
        val unitLimitCl4 = NormsEngine.getGloveUnitaryLeakageLimit("4", 410)
        val pairLimitCl4 = NormsEngine.getGloveMaxLeakageCurrent("4", 410)
        assertEquals(22.0, unitLimitCl4, 0.001)
        assertEquals(44.0, pairLimitCl4, 0.001)
    }

    @Test
    fun testGloveEvaluation_ApprovedUnderDoubleLimit() {
        // Luva Classe 2 (360 mm): limite unitário = 16.0 mA, limite ensaio (2 luvas simultâneas) = 32.0 mA
        // Se mediu 28.0 mA, deve ser APROVADO pois 28.0 <= 32.0 (embora > 16.0 mA unitário)
        val input = NormsEngine.EvaluationInput(
            equipmentType = EquipmentType.LUVA_ISOLANTE,
            dielectricClass = "2",
            appliedVoltage_kV = 20.0,
            durationSeconds = 60,
            measuredLeakage_mA = 28.0,
            withstandWithoutPuncture = true,
            visualPassed = true,
            gloveLength_mm = 360
        )

        val eval = NormsEngine.evaluate(input)
        assertEquals(32.0, eval.leakageLimit_mA, 0.001)
        assertEquals(TestResult.APROVADO, eval.result)
        assertTrue(eval.rationale.contains("simultaneamente"))
        assertTrue(eval.rationale.contains("32"))
    }

    @Test
    fun testGloveEvaluation_RejectedOverDoubleLimit() {
        // Luva Classe 2 (360 mm): limite ensaio = 32.0 mA
        // Se mediu 35.5 mA, deve ser REPROVADO
        val input = NormsEngine.EvaluationInput(
            equipmentType = EquipmentType.LUVA_ISOLANTE,
            dielectricClass = "2",
            appliedVoltage_kV = 20.0,
            durationSeconds = 60,
            measuredLeakage_mA = 35.5,
            withstandWithoutPuncture = true,
            visualPassed = true,
            gloveLength_mm = 360
        )

        val eval = NormsEngine.evaluate(input)
        assertEquals(32.0, eval.leakageLimit_mA, 0.001)
        assertEquals(TestResult.REPROVADO, eval.result)
        assertTrue(eval.rationale.contains("simultaneamente") && eval.rationale.contains("35.5"))
    }
}
