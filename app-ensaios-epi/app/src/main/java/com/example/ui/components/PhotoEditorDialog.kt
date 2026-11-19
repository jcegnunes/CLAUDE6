package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.ExifInterface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyOrange
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.PhotoStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Supported annotation tools in the Photo Editor
 */
enum class AnnotationTool(val label: String, val icon: ImageVector) {
    MOVE("Mover", Icons.Default.OpenWith),
    ARROW("Seta", Icons.AutoMirrored.Filled.ArrowForward),
    CIRCLE("Círculo", Icons.Default.RadioButtonUnchecked),
    RECTANGLE("Retângulo", Icons.Default.CropSquare),
    TEXT("Texto", Icons.Default.TextFields),
    PEN("Caneta", Icons.Default.Brush)
}

/**
 * Thickness options for lines and vector markings
 */
enum class StrokeThickness(val label: String, val strokeDp: Float, val bitmapPxMultiplier: Float) {
    THIN("Fino", 3f, 4f),
    MEDIUM("Médio", 6f, 8f),
    THICK("Grosso", 10f, 14f)
}

/**
 * Color option in the annotation palette
 */
data class EditorColorOption(
    val name: String,
    val color: Color,
    val argb: Int
)

val EDITOR_COLOR_PALETTE = listOf(
    EditorColorOption("Vermelho (Defeito)", Color(0xFFEF4444), 0xFFEF4444.toInt()),
    EditorColorOption("Amarelo (Atenção)", Color(0xFFFACC15), 0xFFFACC15.toInt()),
    EditorColorOption("Verde (Conforme)", Color(0xFF22C55E), 0xFF22C55E.toInt()),
    EditorColorOption("Azul (Técnico)", Color(0xFF0284C7), 0xFF0284C7.toInt()),
    EditorColorOption("Laranja (Alerta)", Color(0xFFEA580C), 0xFFEA580C.toInt()),
    EditorColorOption("Branco", Color(0xFFFFFFFF), 0xFFFFFFFF.toInt()),
    EditorColorOption("Preto", Color(0xFF0F172A), 0xFF0F172A.toInt())
)

/**
 * 2D normalized bounding box (0f..1f)
 */
data class BoundsF(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
)

/**
 * Clamps translation deltas safely so the bounding box remains inside [0f..1f].
 */
private fun safeTranslate(dx: Float, dy: Float, minX: Float, minY: Float, maxX: Float, maxY: Float): Pair<Float, Float> {
    val allowedMinDx = -minX
    val allowedMaxDx = 1f - maxX
    val finalDx = if (allowedMinDx <= allowedMaxDx) {
        dx.coerceIn(allowedMinDx, allowedMaxDx)
    } else {
        0f
    }

    val allowedMinDy = -minY
    val allowedMaxDy = 1f - maxY
    val finalDy = if (allowedMinDy <= allowedMaxDy) {
        dy.coerceIn(allowedMinDy, allowedMaxDy)
    } else {
        0f
    }
    return Pair(finalDx, finalDy)
}

/**
 * Normalized vector annotations (coordinates 0f..1f relative to image dimensions)
 */
sealed class PhotoAnnotation {
    abstract val colorArgb: Int
    abstract val strokeWidthDp: Float

    abstract fun translated(dx: Float, dy: Float): PhotoAnnotation
    abstract fun getBounds(): BoundsF
    abstract fun hitTest(x: Float, y: Float, threshold: Float = 0.06f): Boolean

    data class Circle(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val colorArgb: Int,
        override val strokeWidthDp: Float
    ) : PhotoAnnotation() {
        override fun getBounds(): BoundsF = BoundsF(
            minX = minOf(startX, endX),
            minY = minOf(startY, endY),
            maxX = maxOf(startX, endX),
            maxY = maxOf(startY, endY)
        )

        override fun translated(dx: Float, dy: Float): Circle {
            val b = getBounds()
            val (safeDx, safeDy) = safeTranslate(dx, dy, b.minX, b.minY, b.maxX, b.maxY)
            return copy(
                startX = startX + safeDx,
                startY = startY + safeDy,
                endX = endX + safeDx,
                endY = endY + safeDy
            )
        }

        override fun hitTest(x: Float, y: Float, threshold: Float): Boolean {
            val b = getBounds()
            val centerX = (b.minX + b.maxX) / 2f
            val centerY = (b.minY + b.maxY) / 2f
            val rx = (b.maxX - b.minX) / 2f + threshold
            val ry = (b.maxY - b.minY) / 2f + threshold
            if (rx <= 0f || ry <= 0f) return false
            val normDist = ((x - centerX) / rx).let { it * it } + ((y - centerY) / ry).let { it * it }
            return normDist <= 1.25f
        }
    }

    data class Arrow(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val colorArgb: Int,
        override val strokeWidthDp: Float
    ) : PhotoAnnotation() {
        override fun getBounds(): BoundsF = BoundsF(
            minX = minOf(startX, endX),
            minY = minOf(startY, endY),
            maxX = maxOf(startX, endX),
            maxY = maxOf(startY, endY)
        )

        override fun translated(dx: Float, dy: Float): Arrow {
            val b = getBounds()
            val (safeDx, safeDy) = safeTranslate(dx, dy, b.minX, b.minY, b.maxX, b.maxY)
            return copy(
                startX = startX + safeDx,
                startY = startY + safeDy,
                endX = endX + safeDx,
                endY = endY + safeDy
            )
        }

        override fun hitTest(x: Float, y: Float, threshold: Float): Boolean {
            val l2 = (endX - startX) * (endX - startX) + (endY - startY) * (endY - startY)
            val dist = if (l2 == 0f) {
                hypot(x - startX, y - startY)
            } else {
                val t = (((x - startX) * (endX - startX) + (y - startY) * (endY - startY)) / l2).coerceIn(0f, 1f)
                val projX = startX + t * (endX - startX)
                val projY = startY + t * (endY - startY)
                hypot(x - projX, y - projY)
            }
            return dist <= threshold
        }
    }

    data class Rectangle(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val colorArgb: Int,
        override val strokeWidthDp: Float
    ) : PhotoAnnotation() {
        override fun getBounds(): BoundsF = BoundsF(
            minX = minOf(startX, endX),
            minY = minOf(startY, endY),
            maxX = maxOf(startX, endX),
            maxY = maxOf(startY, endY)
        )

        override fun translated(dx: Float, dy: Float): Rectangle {
            val b = getBounds()
            val (safeDx, safeDy) = safeTranslate(dx, dy, b.minX, b.minY, b.maxX, b.maxY)
            return copy(
                startX = startX + safeDx,
                startY = startY + safeDy,
                endX = endX + safeDx,
                endY = endY + safeDy
            )
        }

        override fun hitTest(x: Float, y: Float, threshold: Float): Boolean {
            val b = getBounds()
            return x in (b.minX - threshold)..(b.maxX + threshold) &&
                   y in (b.minY - threshold)..(b.maxY + threshold)
        }
    }

    data class TextLabel(
        val x: Float,
        val y: Float,
        val text: String,
        override val colorArgb: Int,
        override val strokeWidthDp: Float
    ) : PhotoAnnotation() {
        override fun getBounds(): BoundsF {
            val approxHalfW = (0.04f + text.length * 0.011f).coerceIn(0.06f, 0.40f)
            val approxHalfH = 0.035f
            return BoundsF(
                minX = (x - approxHalfW).coerceAtLeast(0f),
                minY = (y - approxHalfH).coerceAtLeast(0f),
                maxX = (x + approxHalfW).coerceAtMost(1f),
                maxY = (y + approxHalfH).coerceAtMost(1f)
            )
        }

        override fun translated(dx: Float, dy: Float): TextLabel {
            return copy(
                x = (x + dx).coerceIn(0.02f, 0.98f),
                y = (y + dy).coerceIn(0.02f, 0.98f)
            )
        }

        override fun hitTest(x: Float, y: Float, threshold: Float): Boolean {
            val b = getBounds()
            return x in (b.minX - threshold)..(b.maxX + threshold) &&
                   y in (b.minY - threshold)..(b.maxY + threshold)
        }
    }

    data class FreehandPath(
        val points: List<Pair<Float, Float>>,
        override val colorArgb: Int,
        override val strokeWidthDp: Float
    ) : PhotoAnnotation() {
        override fun getBounds(): BoundsF {
            if (points.isEmpty()) return BoundsF(0f, 0f, 0f, 0f)
            return BoundsF(
                minX = points.minOf { it.first },
                minY = points.minOf { it.second },
                maxX = points.maxOf { it.first },
                maxY = points.maxOf { it.second }
            )
        }

        override fun translated(dx: Float, dy: Float): FreehandPath {
            if (points.isEmpty()) return this
            val b = getBounds()
            val (safeDx, safeDy) = safeTranslate(dx, dy, b.minX, b.minY, b.maxX, b.maxY)
            return copy(
                points = points.map { Pair(it.first + safeDx, it.second + safeDy) }
            )
        }

        override fun hitTest(x: Float, y: Float, threshold: Float): Boolean {
            if (points.isEmpty()) return false
            val b = getBounds()
            if (x !in (b.minX - threshold)..(b.maxX + threshold) ||
                y !in (b.minY - threshold)..(b.maxY + threshold)) {
                return false
            }
            return points.any { (px, py) -> hypot(x - px, y - py) <= threshold }
        }
    }
}

/**
 * Finds the topmost annotation that intersects with normalized coordinates (x, y).
 */
fun findAnnotationAt(x: Float, y: Float, annotations: List<PhotoAnnotation>): Int {
    for (i in annotations.indices.reversed()) {
        if (annotations[i].hitTest(x, y)) {
            return i
        }
    }
    return -1
}

/**
 * Quick technical inspection preset text tags for easy one-tap insertion
 */
val QUICK_TEXT_TAGS = listOf(
    "⚡ Furo Dielétrico",
    "⚠️ Não Conforme",
    "🔍 Fissura / Trinca",
    "🏷️ TAG Equipamento",
    "🔥 Ponto de Queima",
    "✅ Conforme / Aprovado",
    "⚡ 10 kV Aplicado",
    "⚠️ Desgaste Mecânico"
)

/**
 * Full-featured Photo Editor Dialog for rotating and annotating dielectric test photos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoEditorDialog(
    photo: TestPhotoItem,
    serviceOrderNumber: String = "OS-AVULSA",
    equipmentTag: String = "",
    onDismiss: () -> Unit,
    onPhotoSaved: (newUriString: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var selectedTool by remember { mutableStateOf(AnnotationTool.ARROW) }
    var selectedColor by remember { mutableStateOf(EDITOR_COLOR_PALETTE[0]) }
    var selectedThickness by remember { mutableStateOf(StrokeThickness.MEDIUM) }

    val annotations = remember { mutableStateListOf<PhotoAnnotation>() }
    var currentInProgressAnnotation by remember { mutableStateOf<PhotoAnnotation?>(null) }
    var selectedAnnotationIndex by remember { mutableIntStateOf(-1) }

    // Text Dialog State
    var showTextInputDialog by remember { mutableStateOf(false) }
    var textPendingNormalizedPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var inputAnnotationText by remember { mutableStateOf("") }

    // Load bitmap asynchronously with downsampling & EXIF orientation
    LaunchedEffect(photo.uriString) {
        isLoadingBitmap = true
        withContext(Dispatchers.IO) {
            baseBitmap = loadBitmapSafely(context, photo.uriString)
        }
        isLoadingBitmap = false
    }

    // Helper to rotate annotations clockwise
    fun rotateAnnotationsCw() {
        rotationDegrees = (rotationDegrees + 90) % 360
        val rotated = annotations.map { a ->
            when (a) {
                is PhotoAnnotation.Circle -> a.copy(
                    startX = 1f - a.startY, startY = a.startX,
                    endX = 1f - a.endY, endY = a.endX
                )
                is PhotoAnnotation.Arrow -> a.copy(
                    startX = 1f - a.startY, startY = a.startX,
                    endX = 1f - a.endY, endY = a.endX
                )
                is PhotoAnnotation.Rectangle -> a.copy(
                    startX = 1f - a.startY, startY = a.startX,
                    endX = 1f - a.endY, endY = a.endX
                )
                is PhotoAnnotation.TextLabel -> a.copy(
                    x = 1f - a.y, y = a.x
                )
                is PhotoAnnotation.FreehandPath -> a.copy(
                    points = a.points.map { (px, py) -> Pair(1f - py, px) }
                )
            }
        }
        annotations.clear()
        annotations.addAll(rotated)
    }

    // Helper to rotate annotations counter-clockwise
    fun rotateAnnotationsCcw() {
        rotationDegrees = (rotationDegrees + 270) % 360
        val rotated = annotations.map { a ->
            when (a) {
                is PhotoAnnotation.Circle -> a.copy(
                    startX = a.startY, startY = 1f - a.startX,
                    endX = a.endY, endY = 1f - a.endX
                )
                is PhotoAnnotation.Arrow -> a.copy(
                    startX = a.startY, startY = 1f - a.startX,
                    endX = a.endY, endY = 1f - a.endX
                )
                is PhotoAnnotation.Rectangle -> a.copy(
                    startX = a.startY, startY = 1f - a.startX,
                    endX = a.endY, endY = 1f - a.endX
                )
                is PhotoAnnotation.TextLabel -> a.copy(
                    x = a.y, y = 1f - a.x
                )
                is PhotoAnnotation.FreehandPath -> a.copy(
                    points = a.points.map { (px, py) -> Pair(py, 1f - px) }
                )
            }
        }
        annotations.clear()
        annotations.addAll(rotated)
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ==========================================
                // 1. TOP HEADER BAR
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyPrimary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Editor de Foto Técnica",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${photo.label} • ${if (rotationDegrees != 0) "$rotationDegrees° " else ""}${annotations.size} marcações",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val bmp = baseBitmap
                            if (bmp != null && !isSaving) {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val savedPath = withContext(Dispatchers.IO) {
                                            renderAndSaveAnnotatedPhoto(
                                                context = context,
                                                baseBitmap = bmp,
                                                rotationDegrees = rotationDegrees,
                                                annotations = annotations.toList(),
                                                serviceOrderNumber = serviceOrderNumber,
                                                label = photo.label,
                                                equipmentTag = equipmentTag
                                            )
                                        }
                                        if (savedPath != null) {
                                            Toast.makeText(context, "Foto editada e salva com sucesso!", Toast.LENGTH_SHORT).show()
                                            onPhotoSaved(savedPath)
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Falha ao salvar a imagem.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao processar: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp).testTag("save_edited_photo_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvando...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salvar Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ==========================================
                // 2. TOP ACTION TOOLBAR (Rotation & History)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rotation Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { rotateAnnotationsCcw() },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.height(32.dp).testTag("rotate_left_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Girar 90° Esquerda", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("-90°", fontSize = 10.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = { rotateAnnotationsCw() },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.height(32.dp).testTag("rotate_right_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Girar 90° Direita", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+90°", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    // Undo and Clear Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                if (annotations.isNotEmpty()) {
                                    val lastIdx = annotations.lastIndex
                                    annotations.removeAt(lastIdx)
                                    if (selectedAnnotationIndex == lastIdx) {
                                        selectedAnnotationIndex = -1
                                    } else if (selectedAnnotationIndex > annotations.lastIndex) {
                                        selectedAnnotationIndex = annotations.lastIndex
                                    }
                                }
                            },
                            enabled = annotations.isNotEmpty(),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (annotations.isNotEmpty()) Color(0xFF334155) else Color(0xFF1E293B)),
                            modifier = Modifier.height(32.dp).testTag("undo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Desfazer última marcação",
                                tint = if (annotations.isNotEmpty()) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Desfazer", fontSize = 10.sp, color = if (annotations.isNotEmpty()) Color.White else Color(0xFF64748B))
                        }

                        IconButton(
                            onClick = {
                                annotations.clear()
                                selectedAnnotationIndex = -1
                            },
                            enabled = annotations.isNotEmpty(),
                            modifier = Modifier.size(32.dp).testTag("clear_annotations_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpar todas marcações",
                                tint = if (annotations.isNotEmpty()) StatusRed else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // 3. MAIN INTERACTIVE CANVAS AREA
                // ==========================================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF020617)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingBitmap) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(36.dp))
                            Text("Carregando imagem...", color = Color.White, fontSize = 12.sp)
                        }
                    } else if (baseBitmap != null) {
                        val originalBmp = baseBitmap!!

                        // Dynamic rotation of bitmap for preview
                        val displayedBitmap = remember(originalBmp, rotationDegrees) {
                            if (rotationDegrees % 360 != 0) {
                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                Bitmap.createBitmap(originalBmp, 0, 0, originalBmp.width, originalBmp.height, matrix, true)
                            } else {
                                originalBmp
                            }
                        }

                        var canvasDisplayedOffset by remember { mutableStateOf(Offset.Zero) }
                        var canvasDisplayedSize by remember { mutableStateOf(Size.Zero) }

                        // Canvas supporting drawing, selection, and direct dragging/displacement of annotations
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(selectedTool, selectedColor, selectedThickness, canvasDisplayedOffset, canvasDisplayedSize, annotations.size, selectedAnnotationIndex) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val startOffset = down.position
                                        val dSize = canvasDisplayedSize
                                        val dOffset = canvasDisplayedOffset
                                        if (dSize.width <= 0 || dSize.height <= 0) return@awaitEachGesture

                                        val startNormX = ((startOffset.x - dOffset.x) / dSize.width).coerceIn(0f, 1f)
                                        val startNormY = ((startOffset.y - dOffset.y) / dSize.height).coerceIn(0f, 1f)

                                        // Check if touch is on currently selected annotation's bounding box
                                        val isTouchingCurrentSelected = if (selectedAnnotationIndex in annotations.indices) {
                                            val b = annotations[selectedAnnotationIndex].getBounds()
                                            startNormX in (b.minX - 0.08f)..(b.maxX + 0.08f) &&
                                            startNormY in (b.minY - 0.08f)..(b.maxY + 0.08f)
                                        } else false

                                        // Hit test all annotations from top to bottom
                                        val hitIdx = if (isTouchingCurrentSelected) {
                                            selectedAnnotationIndex
                                        } else {
                                            findAnnotationAt(startNormX, startNormY, annotations)
                                        }

                                        val willMoveAnnotation = (selectedTool == AnnotationTool.MOVE && hitIdx != -1) ||
                                                isTouchingCurrentSelected ||
                                                (hitIdx != -1 && selectedTool != AnnotationTool.PEN)

                                        val targetMoveIndex = if (willMoveAnnotation) {
                                            if (isTouchingCurrentSelected) selectedAnnotationIndex else hitIdx
                                        } else -1

                                        if (targetMoveIndex != -1) {
                                            selectedAnnotationIndex = targetMoveIndex
                                        }

                                        var isDragging = false
                                        var prevPosition = startOffset
                                        val freehandPoints = mutableListOf<Pair<Float, Float>>()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pointer = event.changes.find { it.id == down.id } ?: break
                                            if (!pointer.pressed) {
                                                pointer.consume()
                                                break
                                            }

                                            val dragAmount = pointer.position - prevPosition
                                            val dist = hypot(pointer.position.x - startOffset.x, pointer.position.y - startOffset.y)

                                            if (dist > 8f) {
                                                isDragging = true
                                                pointer.consume()

                                                val currentNormX = ((pointer.position.x - dOffset.x) / dSize.width).coerceIn(0f, 1f)
                                                val currentNormY = ((pointer.position.y - dOffset.y) / dSize.height).coerceIn(0f, 1f)

                                                if (targetMoveIndex != -1 && targetMoveIndex in annotations.indices) {
                                                    // Displace / translate the selected annotation smoothly
                                                    val dx = dragAmount.x / dSize.width
                                                    val dy = dragAmount.y / dSize.height
                                                    val currentAnn = annotations[targetMoveIndex]
                                                    annotations[targetMoveIndex] = currentAnn.translated(dx, dy)
                                                } else if (selectedTool == AnnotationTool.PEN) {
                                                    if (freehandPoints.isEmpty()) {
                                                        freehandPoints.add(Pair(startNormX, startNormY))
                                                    }
                                                    freehandPoints.add(Pair(currentNormX, currentNormY))
                                                    currentInProgressAnnotation = PhotoAnnotation.FreehandPath(
                                                        points = freehandPoints.toList(),
                                                        colorArgb = selectedColor.argb,
                                                        strokeWidthDp = selectedThickness.strokeDp
                                                    )
                                                } else if (selectedTool != AnnotationTool.MOVE && selectedTool != AnnotationTool.TEXT) {
                                                    currentInProgressAnnotation = when (selectedTool) {
                                                        AnnotationTool.ARROW -> PhotoAnnotation.Arrow(
                                                            startX = startNormX,
                                                            startY = startNormY,
                                                            endX = currentNormX,
                                                            endY = currentNormY,
                                                            colorArgb = selectedColor.argb,
                                                            strokeWidthDp = selectedThickness.strokeDp
                                                        )
                                                        AnnotationTool.CIRCLE -> PhotoAnnotation.Circle(
                                                            startX = startNormX,
                                                            startY = startNormY,
                                                            endX = currentNormX,
                                                            endY = currentNormY,
                                                            colorArgb = selectedColor.argb,
                                                            strokeWidthDp = selectedThickness.strokeDp
                                                        )
                                                        AnnotationTool.RECTANGLE -> PhotoAnnotation.Rectangle(
                                                            startX = startNormX,
                                                            startY = startNormY,
                                                            endX = currentNormX,
                                                            endY = currentNormY,
                                                            colorArgb = selectedColor.argb,
                                                            strokeWidthDp = selectedThickness.strokeDp
                                                        )
                                                        else -> null
                                                    }
                                                }
                                            }
                                            prevPosition = pointer.position
                                        }

                                        // Gesture finished
                                        if (isDragging) {
                                            currentInProgressAnnotation?.let { finished ->
                                                annotations.add(finished)
                                                selectedAnnotationIndex = annotations.lastIndex
                                            }
                                            currentInProgressAnnotation = null
                                        } else {
                                            // TAP gesture
                                            if (hitIdx != -1) {
                                                selectedAnnotationIndex = hitIdx
                                            } else {
                                                if (selectedTool == AnnotationTool.TEXT) {
                                                    textPendingNormalizedPosition = Pair(startNormX, startNormY)
                                                    inputAnnotationText = ""
                                                    showTextInputDialog = true
                                                } else {
                                                    selectedAnnotationIndex = -1
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height
                            val imgW = displayedBitmap.width.toFloat()
                            val imgH = displayedBitmap.height.toFloat()

                            if (canvasW > 0 && canvasH > 0 && imgW > 0 && imgH > 0) {
                                val scale = minOf(canvasW / imgW, canvasH / imgH)
                                val drawW = imgW * scale
                                val drawH = imgH * scale
                                val offsetX = (canvasW - drawW) / 2f
                                val offsetY = (canvasH - drawH) / 2f

                                canvasDisplayedOffset = Offset(offsetX, offsetY)
                                canvasDisplayedSize = Size(drawW, drawH)

                                // Draw the underlying rotated bitmap fitted to canvas
                                drawImage(
                                    image = displayedBitmap.asImageBitmap(),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
                                )

                                // Draw subtle border around photo bounding box
                                drawRect(
                                    color = Color(0xFF334155),
                                    topLeft = Offset(offsetX, offsetY),
                                    size = Size(drawW, drawH),
                                    style = Stroke(width = 1f)
                                )

                                // Draw all committed annotations
                                annotations.forEach { annotation ->
                                    drawAnnotationOnScope(this, annotation, offsetX, offsetY, drawW, drawH)
                                }

                                // Draw live in-progress annotation
                                currentInProgressAnnotation?.let { inProgress ->
                                    drawAnnotationOnScope(this, inProgress, offsetX, offsetY, drawW, drawH)
                                }

                                // Draw high-visibility selection outline and handles if an annotation is selected
                                if (selectedAnnotationIndex in annotations.indices) {
                                    val sel = annotations[selectedAnnotationIndex]
                                    val b = sel.getBounds()
                                    val pad = 8f
                                    val bLeft = (offsetX + b.minX * drawW - pad).coerceAtLeast(offsetX)
                                    val bTop = (offsetY + b.minY * drawH - pad).coerceAtLeast(offsetY)
                                    val bRight = (offsetX + b.maxX * drawW + pad).coerceAtMost(offsetX + drawW)
                                    val bBottom = (offsetY + b.maxY * drawH + pad).coerceAtMost(offsetY + drawH)
                                    val bW = maxOf(bRight - bLeft, 24f)
                                    val bH = maxOf(bBottom - bTop, 24f)

                                    // Dashed selection border
                                    drawRect(
                                        color = ElectricBlue,
                                        topLeft = Offset(bLeft, bTop),
                                        size = Size(bW, bH),
                                        style = Stroke(
                                            width = 2.5f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                        )
                                    )

                                    // Corner handle circles
                                    val handleR = 6f
                                    drawCircle(color = Color.White, radius = handleR + 2f, center = Offset(bLeft, bTop))
                                    drawCircle(color = ElectricBlue, radius = handleR, center = Offset(bLeft, bTop))

                                    drawCircle(color = Color.White, radius = handleR + 2f, center = Offset(bRight, bTop))
                                    drawCircle(color = ElectricBlue, radius = handleR, center = Offset(bRight, bTop))

                                    drawCircle(color = Color.White, radius = handleR + 2f, center = Offset(bLeft, bBottom))
                                    drawCircle(color = ElectricBlue, radius = handleR, center = Offset(bLeft, bBottom))

                                    drawCircle(color = Color.White, radius = handleR + 2f, center = Offset(bRight, bBottom))
                                    drawCircle(color = ElectricBlue, radius = handleR, center = Offset(bRight, bBottom))

                                    // Center displacement crosshair anchor for clear drag affordance
                                    val centerX = bLeft + bW / 2f
                                    val centerY = bTop + bH / 2f
                                    drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = 12f, center = Offset(centerX, centerY))
                                    drawCircle(color = ElectricBlue, radius = 10f, center = Offset(centerX, centerY))
                                    drawCircle(color = Color.White, radius = 3.5f, center = Offset(centerX, centerY))
                                }
                            }
                        }

                        // Floating Precision Displacement Panel when an annotation is selected
                        if (selectedAnnotationIndex in annotations.indices) {
                            val selectedAnn = annotations[selectedAnnotationIndex]
                            val annName = when (selectedAnn) {
                                is PhotoAnnotation.Arrow -> "Seta"
                                is PhotoAnnotation.Circle -> "Círculo"
                                is PhotoAnnotation.Rectangle -> "Retângulo"
                                is PhotoAnnotation.TextLabel -> "Texto (\"${selectedAnn.text.take(10)}\")"
                                is PhotoAnnotation.FreehandPath -> "Traçado Livre"
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xF00F172A),
                                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.8f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp)
                                    .testTag("annotation_displacement_panel")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenWith,
                                                contentDescription = null,
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Deslocar $annName",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Text(
                                            text = "Arraste na foto ou use setas:",
                                            fontSize = 9.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    // Directional nudge buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left
                                        IconButton(
                                            onClick = {
                                                if (selectedAnnotationIndex in annotations.indices) {
                                                    val cur = annotations[selectedAnnotationIndex]
                                                    annotations[selectedAnnotationIndex] = cur.translated(-0.02f, 0f)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFF1E293B), CircleShape)
                                                .testTag("nudge_left")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Mover Esquerda",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Up
                                        IconButton(
                                            onClick = {
                                                if (selectedAnnotationIndex in annotations.indices) {
                                                    val cur = annotations[selectedAnnotationIndex]
                                                    annotations[selectedAnnotationIndex] = cur.translated(0f, -0.02f)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFF1E293B), CircleShape)
                                                .testTag("nudge_up")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Mover Cima",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Down
                                        IconButton(
                                            onClick = {
                                                if (selectedAnnotationIndex in annotations.indices) {
                                                    val cur = annotations[selectedAnnotationIndex]
                                                    annotations[selectedAnnotationIndex] = cur.translated(0f, 0.02f)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFF1E293B), CircleShape)
                                                .testTag("nudge_down")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Mover Baixo",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Right
                                        IconButton(
                                            onClick = {
                                                if (selectedAnnotationIndex in annotations.indices) {
                                                    val cur = annotations[selectedAnnotationIndex]
                                                    annotations[selectedAnnotationIndex] = cur.translated(0.02f, 0f)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFF1E293B), CircleShape)
                                                .testTag("nudge_right")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Mover Direita",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Delete single selected annotation
                                    IconButton(
                                        onClick = {
                                            if (selectedAnnotationIndex in annotations.indices) {
                                                annotations.removeAt(selectedAnnotationIndex)
                                                selectedAnnotationIndex = -1
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(StatusRed.copy(alpha = 0.2f), CircleShape)
                                            .testTag("delete_selected_annotation")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Excluir marcação selecionada",
                                            tint = StatusRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Deselect button
                                    IconButton(
                                        onClick = { selectedAnnotationIndex = -1 },
                                        modifier = Modifier.size(28.dp).testTag("deselect_annotation")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Desmarcar",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Não foi possível carregar o arquivo da imagem.", color = StatusRed, fontSize = 12.sp)
                    }
                }

                // ==========================================
                // 4. BOTTOM TOOL SELECTION BAR
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tool Selector Buttons (Scrollable horizontally to fit all tools)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnnotationTool.entries.forEach { tool ->
                            val isSelected = selectedTool == tool
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ElectricBlue else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (isSelected) ElectricBlue else Color(0xFF334155)),
                                modifier = Modifier
                                    .clickable { selectedTool = tool }
                                    .testTag("tool_${tool.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = tool.label,
                                        tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = tool.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // ==========================================
                    // 5. COLOR PALETTE & STROKE THICKNESS
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color Palette Swatches
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EDITOR_COLOR_PALETTE.forEach { opt ->
                                val isColorSelected = selectedColor.argb == opt.argb
                                Box(
                                    modifier = Modifier
                                        .size(if (isColorSelected) 28.dp else 24.dp)
                                        .clip(CircleShape)
                                        .background(opt.color)
                                        .border(
                                            width = if (isColorSelected) 2.5.dp else 1.dp,
                                            color = if (isColorSelected) Color.White else Color(0xFF475569),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedColor = opt
                                            if (selectedAnnotationIndex in annotations.indices) {
                                                val cur = annotations[selectedAnnotationIndex]
                                                annotations[selectedAnnotationIndex] = when (cur) {
                                                    is PhotoAnnotation.Circle -> cur.copy(colorArgb = opt.argb)
                                                    is PhotoAnnotation.Arrow -> cur.copy(colorArgb = opt.argb)
                                                    is PhotoAnnotation.Rectangle -> cur.copy(colorArgb = opt.argb)
                                                    is PhotoAnnotation.TextLabel -> cur.copy(colorArgb = opt.argb)
                                                    is PhotoAnnotation.FreehandPath -> cur.copy(colorArgb = opt.argb)
                                                }
                                            }
                                        }
                                        .testTag("color_${opt.name.take(3).lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isColorSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (opt.color == Color.White) Color.Black else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Stroke Thickness Selector
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StrokeThickness.entries.forEach { thickness ->
                                val isThickSelected = selectedThickness == thickness
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isThickSelected) ElectricBlue.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isThickSelected) ElectricBlue else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedThickness = thickness
                                        if (selectedAnnotationIndex in annotations.indices) {
                                            val cur = annotations[selectedAnnotationIndex]
                                            annotations[selectedAnnotationIndex] = when (cur) {
                                                is PhotoAnnotation.Circle -> cur.copy(strokeWidthDp = thickness.strokeDp)
                                                is PhotoAnnotation.Arrow -> cur.copy(strokeWidthDp = thickness.strokeDp)
                                                is PhotoAnnotation.Rectangle -> cur.copy(strokeWidthDp = thickness.strokeDp)
                                                is PhotoAnnotation.TextLabel -> cur.copy(strokeWidthDp = thickness.strokeDp)
                                                is PhotoAnnotation.FreehandPath -> cur.copy(strokeWidthDp = thickness.strokeDp)
                                            }
                                        }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = thickness.label,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isThickSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isThickSelected) ElectricBlue else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Guidance Note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when (selectedTool) {
                                AnnotationTool.MOVE -> "Arraste qualquer marcação na foto ou use os botões direcionais para deslocá-la"
                                AnnotationTool.ARROW -> "Arraste o dedo para criar uma seta apontando para a evidência"
                                AnnotationTool.CIRCLE -> "Arraste o dedo para traçar um círculo ao redor do ponto de ensaio"
                                AnnotationTool.RECTANGLE -> "Arraste o dedo para criar uma caixa demarcatória"
                                AnnotationTool.TEXT -> "Toque no ponto da foto onde deseja inserir a legenda de texto"
                                AnnotationTool.PEN -> "Desenhe livremente com o dedo para destacar a área desejada"
                            },
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // 6. MODAL TEXT INPUT DIALOG
    // ==========================================
    if (showTextInputDialog && textPendingNormalizedPosition != null) {
        val pos = textPendingNormalizedPosition!!
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.TextFields, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inserir Marcação de Texto", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Digite o texto descritivo ou clique em um modelo rápido abaixo:",
                        fontSize = 11.5.sp,
                        color = Color(0xFF475569)
                    )

                    OutlinedTextField(
                        value = inputAnnotationText,
                        onValueChange = { inputAnnotationText = it },
                        placeholder = { Text("Ex: Furo Dielétrico no Ensaio", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("annotation_text_input"),
                        singleLine = true
                    )

                    Text("Modelos rápidos:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QUICK_TEXT_TAGS.forEach { tagText ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable { inputAnnotationText = tagText }
                            ) {
                                Text(
                                    text = tagText,
                                    fontSize = 10.sp,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputAnnotationText.isNotBlank()) {
                            annotations.add(
                                PhotoAnnotation.TextLabel(
                                    x = pos.first,
                                    y = pos.second,
                                    text = inputAnnotationText.trim(),
                                    colorArgb = selectedColor.argb,
                                    strokeWidthDp = selectedThickness.strokeDp
                                )
                            )
                            selectedAnnotationIndex = annotations.lastIndex
                            showTextInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Inserir Legenda", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancelar", fontSize = 11.sp)
                }
            }
        )
    }
}

/**
 * Draws a single annotation on the Compose DrawScope canvas.
 */
private fun drawAnnotationOnScope(
    scope: DrawScope,
    annotation: PhotoAnnotation,
    offsetX: Float,
    offsetY: Float,
    drawW: Float,
    drawH: Float
) {
    val color = Color(annotation.colorArgb)
    val strokeWidthPx = annotation.strokeWidthDp * 2.2f

    when (annotation) {
        is PhotoAnnotation.Arrow -> {
            val p1 = Offset(offsetX + annotation.startX * drawW, offsetY + annotation.startY * drawH)
            val p2 = Offset(offsetX + annotation.endX * drawW, offsetY + annotation.endY * drawH)

            // Main line
            scope.drawLine(
                color = color,
                start = p1,
                end = p2,
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            // Arrowhead at p2
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dist = hypot(dx, dy)
            if (dist > 4f) {
                val angle = atan2(dy, dx)
                val arrowHeadLen = maxOf(strokeWidthPx * 3.5f, 18f)
                val arrowAngle = PI / 6.0

                val a1 = Offset(
                    (p2.x - arrowHeadLen * cos(angle - arrowAngle)).toFloat(),
                    (p2.y - arrowHeadLen * sin(angle - arrowAngle)).toFloat()
                )
                val a2 = Offset(
                    (p2.x - arrowHeadLen * cos(angle + arrowAngle)).toFloat(),
                    (p2.y - arrowHeadLen * sin(angle + arrowAngle)).toFloat()
                )

                val headPath = Path().apply {
                    moveTo(p2.x, p2.y)
                    lineTo(a1.x, a1.y)
                    lineTo(a2.x, a2.y)
                    close()
                }
                scope.drawPath(path = headPath, color = color, style = Fill)
            }
        }

        is PhotoAnnotation.Circle -> {
            val left = offsetX + minOf(annotation.startX, annotation.endX) * drawW
            val top = offsetY + minOf(annotation.startY, annotation.endY) * drawH
            val right = offsetX + maxOf(annotation.startX, annotation.endX) * drawW
            val bottom = offsetY + maxOf(annotation.startY, annotation.endY) * drawH
            val w = maxOf(right - left, 8f)
            val h = maxOf(bottom - top, 8f)

            scope.drawOval(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = strokeWidthPx)
            )
        }

        is PhotoAnnotation.Rectangle -> {
            val left = offsetX + minOf(annotation.startX, annotation.endX) * drawW
            val top = offsetY + minOf(annotation.startY, annotation.endY) * drawH
            val right = offsetX + maxOf(annotation.startX, annotation.endX) * drawW
            val bottom = offsetY + maxOf(annotation.startY, annotation.endY) * drawH
            val w = maxOf(right - left, 8f)
            val h = maxOf(bottom - top, 8f)

            scope.drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(width = strokeWidthPx)
            )
        }

        is PhotoAnnotation.TextLabel -> {
            val posX = offsetX + annotation.x * drawW
            val posY = offsetY + annotation.y * drawH

            scope.drawContext.canvas.nativeCanvas.apply {
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = android.graphics.Color.WHITE
                    this.textSize = 34f
                    this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = annotation.colorArgb
                    this.style = android.graphics.Paint.Style.FILL
                }

                val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = android.graphics.Color.WHITE
                    this.style = android.graphics.Paint.Style.STROKE
                    this.strokeWidth = 3f
                }

                val bounds = android.graphics.Rect()
                textPaint.getTextBounds(annotation.text, 0, annotation.text.length, bounds)

                val padH = 18f
                val padV = 12f
                val rectF = RectF(
                    posX - padH,
                    posY - bounds.height() - padV,
                    posX + bounds.width() + padH,
                    posY + padV
                )

                drawRoundRect(rectF, 14f, 14f, bgPaint)
                drawRoundRect(rectF, 14f, 14f, borderPaint)
                drawText(annotation.text, posX, posY, textPaint)
            }
        }

        is PhotoAnnotation.FreehandPath -> {
            if (annotation.points.size > 1) {
                val path = Path().apply {
                    val first = annotation.points.first()
                    moveTo(offsetX + first.first * drawW, offsetY + first.second * drawH)
                    for (i in 1 until annotation.points.size) {
                        val pt = annotation.points[i]
                        lineTo(offsetX + pt.first * drawW, offsetY + pt.second * drawH)
                    }
                }
                scope.drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Loads a bitmap from file path or content URI with memory safeguards and orientation correction.
 */
private fun loadBitmapSafely(context: Context, uriString: String): Bitmap? {
    try {
        // 1. Decode bounds first to prevent OutOfMemoryError
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (uriString.startsWith("/")) {
            BitmapFactory.decodeFile(uriString, options)
        } else {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sampleSize = 1
        val maxDimension = maxOf(options.outWidth, options.outHeight)
        while (maxDimension / sampleSize > 2560) {
            sampleSize *= 2
        }

        // 2. Decode sampled bitmap
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val decodedBitmap = if (uriString.startsWith("/")) {
            BitmapFactory.decodeFile(uriString, options)
        } else {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } ?: return null

        // 3. Inspect EXIF orientation
        val exif = try {
            if (uriString.startsWith("/")) {
                ExifInterface(uriString)
            } else {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                    ExifInterface(it)
                }
            }
        } catch (e: Exception) {
            null
        }

        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

        val rotationAngle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        return if (rotationAngle != 0f) {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
        } else {
            decodedBitmap
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

/**
 * Renders all vector annotations and rotation onto a high-resolution Bitmap and saves it to disk.
 */
private fun renderAndSaveAnnotatedPhoto(
    context: Context,
    baseBitmap: Bitmap,
    rotationDegrees: Int,
    annotations: List<PhotoAnnotation>,
    serviceOrderNumber: String,
    label: String,
    equipmentTag: String
): String? {
    // 1. Rotate base bitmap
    val rotatedBitmap = if (rotationDegrees % 360 != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
    } else {
        baseBitmap
    }

    // 2. Create mutable copy for drawing annotations
    val outputBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(outputBitmap)
    val bmpW = outputBitmap.width.toFloat()
    val bmpH = outputBitmap.height.toFloat()

    // Dynamic scale factor for resolution independence
    val resolutionScale = maxOf(bmpW, bmpH) / 1000f

    // 3. Render each vector annotation on the Android Canvas
    annotations.forEach { annotation ->
        val strokeWidthPx = annotation.strokeWidthDp * resolutionScale * 1.8f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.colorArgb
            strokeWidth = strokeWidthPx
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        when (annotation) {
            is PhotoAnnotation.Arrow -> {
                val x1 = annotation.startX * bmpW
                val y1 = annotation.startY * bmpH
                val x2 = annotation.endX * bmpW
                val y2 = annotation.endY * bmpH

                // Draw line
                canvas.drawLine(x1, y1, x2, y2, paint)

                // Draw Arrowhead
                val dx = x2 - x1
                val dy = y2 - y1
                val dist = hypot(dx, dy)
                if (dist > 4f) {
                    val angle = atan2(dy, dx)
                    val headLen = maxOf(strokeWidthPx * 3.5f, 22f * resolutionScale)
                    val arrowAngle = PI / 6.0

                    val ax1 = (x2 - headLen * cos(angle - arrowAngle)).toFloat()
                    val ay1 = (y2 - headLen * sin(angle - arrowAngle)).toFloat()
                    val ax2 = (x2 - headLen * cos(angle + arrowAngle)).toFloat()
                    val ay2 = (y2 - headLen * sin(angle + arrowAngle)).toFloat()

                    val headPath = android.graphics.Path().apply {
                        moveTo(x2, y2)
                        lineTo(ax1, ay1)
                        lineTo(ax2, ay2)
                        close()
                    }
                    val fillPaint = android.graphics.Paint(paint).apply {
                        style = android.graphics.Paint.Style.FILL
                    }
                    canvas.drawPath(headPath, fillPaint)
                }
            }

            is PhotoAnnotation.Circle -> {
                val left = minOf(annotation.startX, annotation.endX) * bmpW
                val top = minOf(annotation.startY, annotation.endY) * bmpH
                val right = maxOf(annotation.startX, annotation.endX) * bmpW
                val bottom = maxOf(annotation.startY, annotation.endY) * bmpH
                val rectF = RectF(left, top, maxOf(right, left + 8f), maxOf(bottom, top + 8f))
                canvas.drawOval(rectF, paint)
            }

            is PhotoAnnotation.Rectangle -> {
                val left = minOf(annotation.startX, annotation.endX) * bmpW
                val top = minOf(annotation.startY, annotation.endY) * bmpH
                val right = maxOf(annotation.startX, annotation.endX) * bmpW
                val bottom = maxOf(annotation.startY, annotation.endY) * bmpH
                val rectF = RectF(left, top, maxOf(right, left + 8f), maxOf(bottom, top + 8f))
                canvas.drawRect(rectF, paint)
            }

            is PhotoAnnotation.TextLabel -> {
                val posX = annotation.x * bmpW
                val posY = annotation.y * bmpH

                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 28f * resolutionScale
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = annotation.colorArgb
                    style = android.graphics.Paint.Style.FILL
                }

                val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2.5f * resolutionScale
                }

                val bounds = android.graphics.Rect()
                textPaint.getTextBounds(annotation.text, 0, annotation.text.length, bounds)

                val padH = 16f * resolutionScale
                val padV = 10f * resolutionScale
                val rectF = RectF(
                    posX - padH,
                    posY - bounds.height() - padV,
                    posX + bounds.width() + padH,
                    posY + padV
                )

                val cornerRadius = 10f * resolutionScale
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)
                canvas.drawText(annotation.text, posX, posY, textPaint)
            }

            is PhotoAnnotation.FreehandPath -> {
                if (annotation.points.size > 1) {
                    val path = android.graphics.Path().apply {
                        val first = annotation.points.first()
                        moveTo(first.first * bmpW, first.second * bmpH)
                        for (i in 1 until annotation.points.size) {
                            val pt = annotation.points[i]
                            lineTo(pt.first * bmpW, pt.second * bmpH)
                        }
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    // 4. Save to designated OS folder using PhotoStorageManager
    val result = PhotoStorageManager.saveBitmapForOs(
        context = context,
        bitmap = outputBitmap,
        osNumber = serviceOrderNumber,
        label = "${label}_marcada",
        equipmentTag = equipmentTag
    )

    return result?.filePath ?: result?.contentUri?.toString()
}
