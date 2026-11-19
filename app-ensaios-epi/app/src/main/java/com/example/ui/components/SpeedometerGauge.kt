package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    title: String,
    value: Double,
    unit: String,
    maxScale: Double,
    limit: Double? = null,
    isConforming: Boolean = true,
    isVoltage: Boolean = true,
    subtext: String = "",
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = if (maxScale > 0) (value / maxScale).coerceIn(0.0, 1.0).toFloat() else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "needle_anim"
    )

    Column(
        modifier = modifier
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyPrimary, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(vertical = 6.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Gauge Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val cx = size.width / 2f
                val cy = size.height * 0.82f
                val radius = size.height * 0.68f
                val strokeW = 14f

                val startAngle = 180f
                val sweepAngle = 180f

                // Track Background
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                if (isVoltage) {
                    // Active Voltage Arc
                    drawArc(
                        color = ElectricBlue,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * animatedFraction,
                        useCenter = false,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                } else {
                    // Leakage current safe vs danger zones
                    val limitFraction = if (limit != null && maxScale > 0) (limit / maxScale).coerceIn(0.0, 1.0).toFloat() else 0.8f
                    // Safe green arc
                    drawArc(
                        color = StatusGreen,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * limitFraction,
                        useCenter = false,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Danger red arc
                    drawArc(
                        color = StatusRed,
                        startAngle = startAngle + (sweepAngle * limitFraction),
                        sweepAngle = sweepAngle * (1f - limitFraction),
                        useCenter = false,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                // Needle
                val needleAngleRad = (180f - (animatedFraction * 180f)) * (PI.toFloat() / 180f)
                val needleLen = radius - 10f
                val tipX = cx + needleLen * cos(needleAngleRad)
                val tipY = cy - needleLen * sin(needleAngleRad)

                val baseWidth = 6f
                val perpRad = needleAngleRad + (PI.toFloat() / 2f)
                val bx1 = cx + baseWidth * cos(perpRad)
                val by1 = cy - baseWidth * sin(perpRad)
                val bx2 = cx - baseWidth * cos(perpRad)
                val by2 = cy + baseWidth * sin(perpRad)

                val path = Path().apply {
                    moveTo(bx1, by1)
                    lineTo(tipX, tipY)
                    lineTo(bx2, by2)
                    close()
                }

                val needleColor = if (isVoltage) ElectricBlue else if (isConforming) StatusGreen else StatusRed
                drawPath(path, color = needleColor)

                // Center Cap
                drawCircle(color = NavyPrimary, radius = 9f, center = Offset(cx, cy))
                drawCircle(color = Color(0xFFCBD5E1), radius = 4f, center = Offset(cx, cy))
            }

            // Digital Value in Center
            Column(
                modifier = Modifier.padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f %s", value, unit),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isVoltage) ElectricBlue else if (isConforming) StatusGreen else StatusRed
                )
            }
        }

        // Status Badge & Subtext
        val statusText = if (isVoltage) "TENSÃO DE PROVA" else if (isConforming) "CONFORME" else "NÃO CONFORME"
        val statusBg = if (isVoltage) Color(0xFFEFF6FF) else if (isConforming) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
        val statusColor = if (isVoltage) ElectricBlue else if (isConforming) StatusGreen else StatusRed

        Box(
            modifier = Modifier
                .background(statusBg, RoundedCornerShape(6.dp))
                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = statusText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }

        if (subtext.isNotEmpty()) {
            Text(
                text = subtext,
                fontSize = 10.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}
