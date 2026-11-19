package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NavyPrimary

/**
 * Confirmação antes de excluir: a exclusão também é enviada à plataforma web
 * e chega a todos os aparelhos da empresa.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    itemName: String,
    details: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(28.dp)) },
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyPrimary) },
        text = {
            Column {
                Text(itemName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                if (!details.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(details, fontSize = 12.sp, color = Color(0xFF475569))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "A exclusão também será feita na plataforma web e em todos os aparelhos da empresa.",
                    fontSize = 12.sp,
                    color = Color(0xFF9A3412)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) { Text("Excluir", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = NavyPrimary, fontWeight = FontWeight.Bold) }
        }
    )
}
