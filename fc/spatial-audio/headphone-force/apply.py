#!/usr/bin/env python3
"""Build-only UI for explicit software headphone surround.

Not an Atmos decoder or an Atmos entitlement. Uses existing SpatialAudioMode.ON
to bypass Auto learning only; keep device codec, JOC, speaker, bit-perfect and DSP
safety checks in the existing service.
"""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / "app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt"

def once(source: str, before: str, after: str) -> str:
    matches = source.count(before)
    if matches != 1:
        raise SystemExit(f"Headphone surround UI changed upstream: expected one anchor, got {matches}")
    return source.replace(before, after, 1)

def patch(value: str) -> str:
    # The permanent spatial-audio adapter has already installed this mode.
    value = once(
        value,
        "    val spatialAudioOnPhoneSpeaker by AppSettings.spatialAudioOnPhoneSpeaker.collectAsStateWithLifecycle()\n",
        "    val spatialAudioOnPhoneSpeaker by AppSettings.spatialAudioOnPhoneSpeaker.collectAsStateWithLifecycle()\n"
        "    val headphoneSurroundMode by AppSettings.spatialAudioMode.collectAsStateWithLifecycle()\n",
    )
    added = '''            // Software headphone surround: a manual override of Auto learning.
            // This is deliberately NOT an attempt to unlock Dolby Atmos decoding.
            val forceHeadphoneSurround = headphoneSurroundMode == com.music.zenith.playback.SpatialAudioMode.ON
            row("Force headphone surround", "stereo", "headphones", "spatial audio") {
                BypassedByBitPerfect(bitPerfectReady) {
                    SettingsRow(
                        icon = SfIcons.SurroundSound,
                        title = "Force headphone surround",
                        subtitle = "Software stereo widening, not Dolby Atmos. Bypassed on phone speakers, Atmos tracks and incompatible outputs.",
                        trailing = {
                            IosSwitch(
                                checked = forceHeadphoneSurround,
                                onCheckedChange = { force ->
                                    AppSettings.setSpatialAudioMode(
                                        if (force) com.music.zenith.playback.SpatialAudioMode.ON
                                        else com.music.zenith.playback.SpatialAudioMode.AUTO,
                                    )
                                },
                            )
                        },
                        onClick = {
                            AppSettings.setSpatialAudioMode(
                                if (forceHeadphoneSurround) com.music.zenith.playback.SpatialAudioMode.AUTO
                                else com.music.zenith.playback.SpatialAudioMode.ON,
                            )
                        },
                    )
                }
            }
'''
    return once(
        value,
        "            val spatialLearningTitle = stringResource(R.string.spatial_audio_learning_enabled)\n",
        added + "            val spatialLearningTitle = stringResource(R.string.spatial_audio_learning_enabled)\n",
    )

if __name__ == "__main__":
    if not UI.is_file():
        raise SystemExit("Expected Settings UI missing; refusing unsafe integration")
    before = UI.read_text(encoding="utf-8")
    after = patch(before)
    UI.write_text(after, encoding="utf-8")
    print("Headphone surround: optional manual stereo override added; Dolby decoder gate untouched")
