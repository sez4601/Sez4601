package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Gradient & Deep Blue Theme (Exact match with images & video)
val BlueGradientStart = Color(0xFF0F52BA) // Cobalt Blue top
val BlueGradientEnd = Color(0xFF0A2E68)   // Deep Navy bottom
val BluePrimary = Color(0xFF0284C7)
val BlueCard = Color(0xFF134E96)
val BlueCardBorder = Color(0xFF2575FC)
val BlueActiveTab = Color(0xFF0084FF)
val BlueInactiveTab = Color(0x33FFFFFF)

// Accent Colors
val CyanAccent = Color(0xFF00E5FF)
val GreenSuccess = Color(0xFF22C55E)
val GreenButton = Color(0xFF22C55E)
val GreenButtonDark = Color(0xFF16A34A)
val RedDanger = Color(0xFFEF4444)
val WarningOrange = Color(0xFFF97316)
val WarningYellow = Color(0xFFFBBF24)
val OrangeCritical = Color(0xFFEA580C)
val PurpleCard = Color(0xFF7C3AED)
val AmberCard = Color(0xFFF59E0B)

// Dark/Light Surfaces for app
val DarkBackground = Color(0xFF0A224D)
val DarkSurface = Color(0xFF113875)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)
val BorderLight = Color(0xFF335C9E)

val MainGradientBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF084B9E),
        Color(0xFF052B61),
        Color(0xFF031A3D)
    )
)

