package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors

private val AppColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color.Black,
    secondary = SafetyOrange,
    onSecondary = Color.White,
    tertiary = ElectricBlue,
    background = LightBackground,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color.Black,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White
)

@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.Black,
    focusedPlaceholderColor = Color(0xFF475569),
    unfocusedPlaceholderColor = Color(0xFF475569),
    focusedBorderColor = NavyPrimary,
    unfocusedBorderColor = Color(0xFF94A3B8),
    cursorColor = Color.Black,
    focusedSupportingTextColor = Color.Black,
    unfocusedSupportingTextColor = Color.Black,
    focusedPrefixColor = Color.Black,
    unfocusedPrefixColor = Color.Black,
    focusedSuffixColor = Color.Black,
    unfocusedSuffixColor = Color.Black
)

/** Campos da tela de login: fundo branco e texto escuro sobre o cartão escuro. */
@Composable
fun loginTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    errorContainerColor = Color.White,
    focusedTextColor = Color(0xFF0F172A),
    unfocusedTextColor = Color(0xFF0F172A),
    focusedPlaceholderColor = Color(0xFF64748B),
    unfocusedPlaceholderColor = Color(0xFF64748B),
    focusedBorderColor = Color(0xFFF97316),
    unfocusedBorderColor = Color(0xFFCBD5E1),
    cursorColor = Color(0xFF0F172A),
    focusedLeadingIconColor = Color(0xFF475569),
    unfocusedLeadingIconColor = Color(0xFF475569),
    focusedTrailingIconColor = Color(0xFF475569),
    unfocusedTrailingIconColor = Color(0xFF475569)
)

@Composable
fun appDropdownMenuItemColors(): MenuItemColors = MenuDefaults.itemColors(
    textColor = Color.Black,
    leadingIconColor = Color.Black,
    trailingIconColor = Color.Black
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}

