package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NavyPrimary
import kotlin.math.abs

@Composable
fun QRCodeView(
    data: String,
    modifier: Modifier = Modifier,
    qrSize: Dp = 100.dp
) {
    // QR Code real (ZXing), igual ao do laudo e do validador
    val grid = remember(data) { com.example.util.QrCode.matrix(data) }
    val matrixSize = grid.size

    Box(
        modifier = modifier
            .size(qrSize)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(1f)) {
            val cellSize = this.size.width.coerceAtMost(this.size.height) / matrixSize.toFloat()
            for (r in 0 until matrixSize) {
                for (c in 0 until matrixSize) {
                    if (grid[r][c]) {
                        drawRect(
                            color = NavyPrimary,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}
