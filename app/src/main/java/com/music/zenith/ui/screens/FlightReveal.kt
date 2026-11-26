package com.music.zenith.ui.screens

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.zenith.ui.components.LocalCoverVisibilityScope
import com.music.zenith.ui.components.coverFlightPending
import com.music.zenith.ui.components.enterReveal

/**
 * [enterReveal], plus (bila [afterFlight]) menunggu cover terbang selesai dan
 * overlay-nya lepas. Cover terbang digambar di atas seluruh halaman, jadi teks
 * yang muncul selama penerbangan tampil di belakangnya lalu meloncat ke depan
 * saat cover mendarat. Dengan menunggu, teks langsung muncul di depan cover.
 * Halaman yang dibuka tanpa penerbangan tidak menunggu apa pun.
 */
@Composable
internal fun Modifier.flightReveal(
    delayMs: Int = 0,
    rise: Dp = 14.dp,
    afterFlight: Boolean = false,
): Modifier {
    // Saat menunggu penerbangan, geserannya cukup dari lapisan di bawah.
    val base = this.enterReveal(delayMs, if (afterFlight) 0.dp else rise)
    if (!afterFlight) return base
    // Halaman yang sedang ditinggalkan tidak ikut disembunyikan oleh
    // penerbangan menuju halaman berikutnya.
    val leaving = LocalCoverVisibilityScope.current?.transition?.targetState == EnterExitState.PostExit
    val flying = !leaving && coverFlightPending()
    val landed by animateFloatAsState(
        targetValue = if (flying) 0f else 1f,
        animationSpec = if (flying) {
            snap()
        } else {
            tween(durationMillis = 360, delayMillis = delayMs / 3, easing = FastOutSlowInEasing)
        },
        label = "flightReveal",
    )
    return base.graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = landed
        translationY = (1f - landed) * rise.toPx()
    }
}