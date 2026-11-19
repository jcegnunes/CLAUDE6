package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberBg
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusGreenBg
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusRedBg

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, borderColor, icon) = when (status.uppercase()) {
        "APROVADO", "EM_USO", "CONCLUIDA", "CONFORME", "VALID" -> Quadruple(
            StatusGreenBg,
            StatusGreen,
            StatusGreen.copy(alpha = 0.3f),
            Icons.Default.CheckCircle
        )
        "REPROVADO", "CANCELADA", "NAO_CONFORME", "VENCIDO", "FORA_DE_SERVICO" -> Quadruple(
            StatusRedBg,
            StatusRed,
            StatusRed.copy(alpha = 0.3f),
            Icons.Default.Warning
        )
        else -> Quadruple(
            StatusAmberBg,
            StatusAmber,
            StatusAmber.copy(alpha = 0.3f),
            Icons.Default.HourglassEmpty
        )
    }

    val displayLabel = when (status.uppercase()) {
        "EM_USO" -> "Em Uso / Aprovado"
        "FORA_DE_SERVICO" -> "Vencido / Fora"
        "EM_EXECUCAO" -> "Em Execução"
        "NAO_CONFORME" -> "Não Conforme"
        else -> status.replace("_", " ")
    }

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = displayLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
