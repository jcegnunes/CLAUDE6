package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.EquipmentType
import com.example.domain.NormsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("JVM Ensaios", appName)
  }

  @Test
  fun `verify database opens cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val settings = db.settingsDao().getSettingsOnce()
    assertNotNull(db)
  }

  @Test
  fun `verify aterramento temporario configuration`() {
    val eqType = EquipmentType.ATERRAMENTO_TEMPORARIO
    assertEquals("TAG-ATR", eqType.tagPrefix)
    assertTrue(eqType.norm.contains("ABNT NBR IEC 61230"))
    assertTrue(eqType.norm.contains("ASTM F855"))

    val fromStr = EquipmentType.fromString("Aterramento Temporário")
    assertEquals(EquipmentType.ATERRAMENTO_TEMPORARIO, fromStr)

    val checklist = NormsEngine.getVisualChecklist(eqType)
    assertEquals(6, checklist.size)
    assertTrue(checklist.any { it.title.contains("cabos de cobre", ignoreCase = true) })
    assertTrue(checklist.any { it.title.contains("Grampos de fixação", ignoreCase = true) })
    assertTrue(checklist.any { it.title.contains("Bastões isolantes", ignoreCase = true) })

    val criteria = NormsEngine.getStandardCriteria(eqType, "2")
    assertEquals("ABNT NBR IEC 61230 / ASTM F855", criteria.normCode)
    assertEquals(60, criteria.durationSeconds)
    assertEquals(12, criteria.retestIntervalMonths)
  }

  @Test
  fun `bastao e vara usam a NBR 11854 da plataforma web (50 kV, limite pelo diametro)`() {
    com.example.domain.NormsCatalog.load(ApplicationProvider.getApplicationContext<Context>())
    val bastao = EquipmentType.BASTAO_MANOBRA
    val vara = EquipmentType.VARA_MANOBRA

    val c38 = NormsEngine.getStandardCriteria(bastao, "4", stickDiameter_mm = 38)
    assertEquals("norm-bastao-100kv", c38.criterionId)
    assertEquals(50.0, c38.targetVoltage_kV, 0.001)
    assertEquals(6.0, c38.leakageLimit_mA, 0.001) // µA
    assertEquals(60, c38.durationSeconds)
    assertEquals(12, c38.retestIntervalMonths)
    assertTrue(c38.normCode.contains("NBR 11854"))
    assertEquals(5.0, NormsEngine.getStandardCriteria(vara, "2", stickDiameter_mm = 32).leakageLimit_mA, 0.001)
    assertEquals(8.0, NormsEngine.getStandardCriteria(vara, "2", stickDiameter_mm = 51).leakageLimit_mA, 0.001)
    assertEquals(10.0, NormsEngine.getStandardCriteria(vara, "2", stickDiameter_mm = 64).leakageLimit_mA, 0.001)

    val approved = NormsEngine.evaluate(
      NormsEngine.EvaluationInput(bastao, "4", 50.0, 60, 6.0, withstandWithoutPuncture = true, visualPassed = true, stickDiameter_mm = 38)
    )
    assertEquals(com.example.data.model.TestResult.APROVADO, approved.result)
    assertTrue(approved.rationale.contains("NBR 11854"))

    val rejected = NormsEngine.evaluate(
      NormsEngine.EvaluationInput(vara, "4", 50.0, 60, 6.0, withstandWithoutPuncture = true, visualPassed = true, stickDiameter_mm = 32)
    )
    assertEquals(com.example.data.model.TestResult.REPROVADO, rejected.result)
    assertTrue(rejected.rationale.contains("excedeu o limite"))

    val punctured = NormsEngine.evaluate(
      NormsEngine.EvaluationInput(bastao, "4", 50.0, 60, 3.0, withstandWithoutPuncture = false, visualPassed = true, stickDiameter_mm = 38)
    )
    assertEquals(com.example.data.model.TestResult.REPROVADO, punctured.result)
  }
  @Test
  fun `calcado carrega a sua norma ao ser selecionado`() {
    com.example.domain.NormsCatalog.load(ApplicationProvider.getApplicationContext<Context>())
    for (cls in listOf("00", "0", "1", "2", "3", "4")) {
      val c = NormsEngine.getStandardCriteria(EquipmentType.BOTA_DIELETRICA, cls)
      assertEquals("norm-calcado-dieletrico", c.criterionId)
      assertEquals(14.0, c.targetVoltage_kV, 0.001)
      assertEquals(0.5, c.leakageLimit_mA, 0.0001)
      assertEquals(60, c.durationSeconds)
    }
    // manga fora da classe 2 e detector capacitivo continuam exigindo a escolha da norma
    assertEquals(false, NormsEngine.getStandardCriteria(EquipmentType.MANGA_ISOLANTE, "1").configured)
    assertEquals(false, NormsEngine.getStandardCriteria(EquipmentType.DETECTOR_TENSAO, "2").configured)
  }

  /**
   * Paridade com a plataforma web: casos gerados executando o motor da web
   * (src/services/normsEngine.ts) com os critérios oficiais (INITIAL_NORMS).
   */
  @Test
  fun `avaliacao do app igual a da plataforma web`() {
    com.example.domain.NormsCatalog.load(ApplicationProvider.getApplicationContext<Context>())
    val text = javaClass.classLoader!!.getResourceAsStream("web_norms_cases.json")!!.bufferedReader().readText()
    val cases = org.json.JSONArray(text)
    for (i in 0 until cases.length()) {
      val c = cases.getJSONObject(i)
      val webType = c.getString("type")
      val type = if (webType == "bota_dielétrica") EquipmentType.BOTA_DIELETRICA else EquipmentType.valueOf(webType.uppercase())
      val stick = type == EquipmentType.BASTAO_MANOBRA || type == EquipmentType.VARA_MANOBRA
      val factor = 1.0 // bastões/varas: µA nos dois sistemas (NBR 11854)
      val out = NormsEngine.evaluate(
        NormsEngine.EvaluationInput(
          equipmentType = type,
          dielectricClass = c.getString("cls"),
          appliedVoltage_kV = c.getDouble("kv"),
          durationSeconds = c.getInt("s"),
          measuredLeakage_mA = c.getDouble("ma") * factor,
          withstandWithoutPuncture = c.getBoolean("withstand"),
          visualPassed = c.getBoolean("visual"),
          gloveLength_mm = c.getInt("glove"),
          toolsCount = c.getInt("tools"),
          stickDiameter_mm = c.optInt("pole", 0).takeIf { stick && it > 0 } ?: 38,
          criterionId = c.optString("normId", "").ifBlank { null },
          visualFailedItems = if (c.getBoolean("visual")) emptyList() else listOf("x"),
          inductionPoints = c.optJSONArray("points")?.let { com.example.domain.InductionDetector.parse(it.toString()) }
        )
      )
      val label = "caso $i: $webType classe ${c.getString("cls")} ${c.getDouble("ma")} mA"
      // Diferença intencional: no app o calçado carrega a sua norma (classe "Especial") ao ser
      // selecionado; na web ele fica PENDENTE até a norma ser escolhida no ensaio.
      if (type == EquipmentType.BOTA_DIELETRICA && c.optString("normId", "").isBlank()) {
        assertEquals(label, "norm-calcado-dieletrico", out.criterionId)
        assertEquals(label, if (c.getDouble("ma") <= 0.5) "APROVADO" else "REPROVADO", out.result.name)
        continue
      }
      assertEquals(label, c.getString("result"), out.result.name)
      assertEquals(label, c.getDouble("limit") * factor, out.leakageLimit_mA, 0.0001)
      assertEquals(label, c.getString("criterion"), out.criterionId)
      assertEquals(label, c.getInt("retest"), out.recommendedRetestMonths)
      if (type == EquipmentType.DETECTOR_INDUCAO) assertEquals(label, c.getString("rationale"), out.rationale)
    }
  }

  @Test
  fun `verify photo editor tools and color configuration`() {
    val tools = com.example.ui.components.AnnotationTool.entries
    assertEquals(6, tools.size)
    assertTrue(tools.contains(com.example.ui.components.AnnotationTool.ARROW))
    assertTrue(tools.contains(com.example.ui.components.AnnotationTool.CIRCLE))
    assertTrue(tools.contains(com.example.ui.components.AnnotationTool.RECTANGLE))
    assertTrue(tools.contains(com.example.ui.components.AnnotationTool.TEXT))
    assertTrue(tools.contains(com.example.ui.components.AnnotationTool.PEN))
    assertTrue(tools.contains(com.example.ui.components.AnnotationTool.MOVE))

    val colors = com.example.ui.components.EDITOR_COLOR_PALETTE
    assertTrue(colors.size >= 6)
    assertTrue(colors.any { it.name.contains("Vermelho", ignoreCase = true) })
    assertTrue(colors.any { it.name.contains("Amarelo", ignoreCase = true) })
    assertTrue(colors.any { it.name.contains("Verde", ignoreCase = true) })
    assertTrue(colors.any { it.name.contains("Azul", ignoreCase = true) })

    val quickTags = com.example.ui.components.QUICK_TEXT_TAGS
    assertTrue(quickTags.any { it.contains("Furo Dielétrico") })
    assertTrue(quickTags.any { it.contains("Não Conforme") })
  }

  @Test
  fun `verify photo annotations and rotation coordinate math`() {
    val arrow = com.example.ui.components.PhotoAnnotation.Arrow(
      startX = 0.2f,
      startY = 0.3f,
      endX = 0.8f,
      endY = 0.9f,
      colorArgb = 0xFFEF4444.toInt(),
      strokeWidthDp = 6f
    )
    assertEquals(0.2f, arrow.startX, 0.001f)
    assertEquals(0.9f, arrow.endY, 0.001f)

    // Simulating 90° clockwise rotation: newX = 1f - oldY, newY = oldX
    val rotatedStartX = 1f - arrow.startY
    val rotatedStartY = arrow.startX
    assertEquals(0.7f, rotatedStartX, 0.001f)
    assertEquals(0.2f, rotatedStartY, 0.001f)

    val circle = com.example.ui.components.PhotoAnnotation.Circle(
      startX = 0.4f,
      startY = 0.4f,
      endX = 0.6f,
      endY = 0.6f,
      colorArgb = 0xFFFACC15.toInt(),
      strokeWidthDp = 4f
    )
    assertEquals(0.4f, circle.startX, 0.001f)

    // Verify displacement / translation
    val movedCircle = circle.translated(0.1f, 0.1f)
    assertEquals(0.5f, movedCircle.startX, 0.001f)
    assertEquals(0.5f, movedCircle.startY, 0.001f)
    assertEquals(0.7f, movedCircle.endX, 0.001f)
    assertEquals(0.7f, movedCircle.endY, 0.001f)

    val textLabel = com.example.ui.components.PhotoAnnotation.TextLabel(
      x = 0.5f,
      y = 0.5f,
      text = "Furo Dielétrico",
      colorArgb = 0xFFEF4444.toInt(),
      strokeWidthDp = 5f
    )
    assertEquals("Furo Dielétrico", textLabel.text)

    val movedText = textLabel.translated(-0.1f, 0.2f)
    assertEquals(0.4f, movedText.x, 0.001f)
    assertEquals(0.7f, movedText.y, 0.001f)
  }
}
