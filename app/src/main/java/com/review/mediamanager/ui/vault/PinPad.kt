package com.review.mediamanager.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.review.mediamanager.ui.theme.Aurora
import com.review.mediamanager.ui.theme.LocalPalette

@Composable
fun PinDots(length: Int, filled: Int, errorTick: Int) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorTick) {
        if (errorTick > 0) {
            for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.offset(x = shake.value.dp)) {
        repeat(length) { i ->
            Box(
                Modifier.size(15.dp).clip(CircleShape)
                    .then(if (i < filled) Modifier.background(Aurora.brand) else Modifier.border(1.5.dp, Aurora.TextLow, CircleShape))
            )
        }
    }
}

@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { k ->
                    when (k) {
                        "bio" -> Key(enabled = onBiometric != null, onClick = { onBiometric?.invoke() }) {
                            if (onBiometric != null) Icon(Icons.Rounded.Fingerprint, "Biometric", tint = Aurora.Mint, modifier = Modifier.size(30.dp))
                        }
                        "del" -> Key(onClick = onDelete, ghost = true) {
                            Icon(Icons.Rounded.Backspace, "Delete", tint = Aurora.TextMid)
                        }
                        else -> Key(onClick = { onDigit(k[0]) }) {
                            Text(k, style = MaterialTheme.typography.headlineSmall, color = Aurora.TextHi)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Key(enabled: Boolean = true, ghost: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(74.dp).clip(CircleShape)
            .background(if (ghost || !enabled) Color.Transparent else p.surfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
