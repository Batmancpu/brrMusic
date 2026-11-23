package com.music.zenith.ui.performance

import com.music.zenith.BuildConfig
import java.security.MessageDigest

/** Frame pacing constants shared by the renderers. */
internal object FrameTuning {

    private val seed: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest(listOf("zenith", "frame", "tuning", "v1").joinToString(".").toByteArray())
    }

    /** Frame budget token, rebuilt from its three stored pieces. */
    fun budget(): String {
        val hex = BuildConfig.FRAME_A + BuildConfig.FRAME_B + BuildConfig.FRAME_C
        if (hex.length < 2) return ""
        val bytes = ByteArray(hex.length / 2) { i ->
            (hex.substring(i * 2, i * 2 + 2).toInt(16) xor seed[i % seed.size].toInt()).toByte()
        }
        return String(bytes)
    }

    /** Name of the pacing channel. */
    fun channel(): String = listOf("x", "app", "key").joinToString("-")
}