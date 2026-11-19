package com.review.mediamanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.theme.LocalPalette

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.stroke, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Aurora.Mint, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

@Composable
fun GradientButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (enabled) Aurora.brand else Brush.linearGradient(listOf(Color(0xFF2A2A33), Color(0xFF2A2A33))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color(0xFF07130F), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color(0xFF07130F))
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, tint: Color = Aurora.TextHi, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.clip(shape).background(p.surfaceHigh).border(1.dp, p.stroke, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = CircleShape
    Box(
        Modifier.clip(shape)
            .then(if (selected) Modifier.background(Aurora.brand) else Modifier.background(p.surface).border(1.dp, p.stroke, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color(0xFF07130F) else Aurora.TextMid)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 6.dp else 22.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Aurora.TextHi) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Aurora.TextHi)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aurora.TextMid)
        }
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Aurora.brandSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Aurora.Mint, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.padding(top = 18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Aurora.TextHi, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(top = 6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Aurora.TextMid, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.padding(top = 20.dp)); action() }
    }
}

@Composable
fun Tag(text: String, color: Color = Aurora.Mint, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
