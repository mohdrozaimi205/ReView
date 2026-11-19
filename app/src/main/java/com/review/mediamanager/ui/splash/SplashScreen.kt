package com.review.mediamanager.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.review.mediamanager.ui.theme.Aurora
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@Composable
fun ReViewLogo(modifier: Modifier = Modifier, spin: Float = 0f) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.09f
        val r = s / 2 - stroke
        rotate(spin) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Aurora.Mint, Aurora.Lavender, Aurora.Mint)),
                startAngle = -40f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        val tri = Path().apply {
            moveTo(center.x - s * 0.12f, center.y - s * 0.17f)
            lineTo(center.x + s * 0.19f, center.y)
            lineTo(center.x - s * 0.12f, center.y + s * 0.17f)
            close()
        }
        drawPath(tri, Brush.linearGradient(listOf(Aurora.Lavender, Aurora.Mint)))
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val logoScale = remember { Animatable(0.6f) }
    val textAlpha = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    LaunchedEffect(Unit) {
        coroutineScope {
            val a = async { logoScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            val b = async { delay(250); textAlpha.animateTo(1f, tween(600)) }
            val c = async { bar.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
            a.await(); b.await(); c.await()
        }
        delay(150)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.size(320.dp).alpha(0.35f).background(
            Brush.radialGradient(listOf(Aurora.Lavender.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent))
        ))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ReViewLogo(Modifier.size(112.dp).scale(logoScale.value), spin)
            Spacer(Modifier.height(26.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Aurora.Mint)) { append("Re") }
                    withStyle(SpanStyle(color = Aurora.TextHi)) { append("View") }
                },
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(6.dp))
            Text("Your Media. Your Control.", style = MaterialTheme.typography.bodyMedium, color = Aurora.TextMid,
                modifier = Modifier.alpha(textAlpha.value))
            Spacer(Modifier.height(36.dp))
            Box(Modifier.width(140.dp).height(4.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF1A1A22))) {
                Box(Modifier.fillMaxWidth(bar.value).height(4.dp).clip(CircleShape).background(Aurora.brand))
            }
        }
        Text("v" + com.review.mediamanager.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall, color = Aurora.TextLow,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))
    }
}
