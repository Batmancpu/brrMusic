#!/usr/bin/env python3
"""Temporary, fail-closed UI rebrand applied only in the CI build workspace."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main"
SHARED = ROOT / "sharedUi/src/commonMain/composeResources"

def config_enabled():
    settings = dict(line.split("=", 1) for line in
        (x.strip() for x in (ROOT / "fc/branding/config.properties").read_text().splitlines())
        if line and not line.startswith("#"))
    if settings.get("enabled") not in ("true", "false"):
        raise SystemExit("Brand overlay requires enabled=true or enabled=false")
    return settings["enabled"] == "true"

def replace_once(value, old, new, path):
    count = value.count(old)
    if count != 1:
        raise SystemExit(f"Brand overlay stopped: {path}: expected one {old[:65]!r}, found {count}")
    return value.replace(old, new, 1)

def edit(relative, transform):
    path = ROOT / relative
    old = path.read_text(encoding="utf-8")
    new = transform(old, path)
    if old == new:
        raise SystemExit(f"Brand overlay stopped: no changes to {relative}")
    path.write_text(new, encoding="utf-8")
    print(f"brrMusic temporary UI overlay: {relative}")

def xml_text(value, path):
    if "Zenith" not in value:
        raise SystemExit(f"Brand overlay stopped: no Zenith UI text in {path}")
    # Modify human-readable text nodes, never resource identifiers or third-party brands.
    return re.sub(r"(?<=>)[^<>]*(?=<)",
        lambda m: re.sub(r"\bZenith\b", "brrMusic", m.group(), flags=re.I),
        value)

def lyrics_card(value, path):
    """Remove the visible logo/watermark; support both reviewed upstream layouts.

    Zenith 2026-11-22 moved the mark from a footer band to the upper-right
    header. Do not remove any panel/lyrics/song-drawing code.
    """
    marker = "    // ── Brand ─"
    if value.count(marker) != 1:
        raise SystemExit("Lyrics brand section changed; review patch before releasing")
    if "private const val BRAND_BAND = 132f" in value:
        # Older layout: brand sits below panel and has a dedicated height band.
        end = "\n    bitmap\n"
        if value.count(end) != 1:
            raise SystemExit("Legacy lyrics footer layout changed; refusing patch")
        head, tail = value.split(marker, 1)
        removed, rest = tail.split(end, 1)
        if "drawLogo(" not in removed or "canvas.drawText(BRAND" not in removed:
            raise SystemExit("Legacy lyrics logo did not match reviewed layout")
        value = head + "    // No visual watermark on shared lyrics.\\n" + end + rest
        return replace_once(value, "private const val BRAND_BAND = 132f",
                            "private const val BRAND_BAND = 0f", path)
    # New layout: brand is a conditional header before the lyrics passage.
    end = "    // ── The passage ─"
    if value.count(end) != 1:
        raise SystemExit("New lyrics passage anchor changed; refusing patch")
    head, tail = value.split(marker, 1)
    removed, rest = tail.split(end, 1)
    required = ("if (showLogo)", "drawLogo(canvas, context,", "canvas.drawText(BRAND")
    if not all(token in removed for token in required):
        raise SystemExit("New lyrics header logo did not match reviewed layout")
    return head + "    // No visual watermark on shared lyrics.\n" + end + rest


def home(value, path):
    old = """            Wordmark(
                logoHeight = logoHeight,
                fontSize = style.fontSize * MUSIC_TO_FONT,
                color = MaterialTheme.colorScheme.onBackground,
                gap = 2.dp,
                modifier = Modifier.padding(bottom = 14.dp),
            )"""
    new = """            Text(
                text = "Music",
                style = style,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 14.dp),
            )"""
    return replace_once(value, old, new, path)

def settings(value, path):
    value = replace_once(value, '"Support Zenith"', '"Support upstream developer"', path)
    return replace_once(value, '"Zenith $version"', '"brrMusic $version"', path)
    # Preserve actual legacy Music/Zenith storage paths to avoid orphaning downloads.

def replay_poster(value, path):
    value = replace_once(value,
        '    canvas.drawText("Zenith", w - MARGIN, 150f, brand)\n'
        '    drawLogo(canvas, context, w - MARGIN - brand.measureText("Zenith") - LOGO_GAP, 150f)',
        '    canvas.drawText("brrMusic", w - MARGIN, 150f, brand)', path)
    value = replace_once(value,
        '    canvas.drawText("Zenith", POSTER_W - MARGIN, 132f, brand)',
        '    canvas.drawText("brrMusic", POSTER_W - MARGIN, 132f, brand)', path)
    return replace_once(value,
        '    val wordWidth = brand.measureText("Zenith")\n'
        '    drawLogo(canvas, context, POSTER_W - MARGIN - wordWidth - LOGO_GAP, 132f)',
        '    // No upstream logo: brrMusic heading stands alone.', path)

def lyrics_share(value, path):
    return replace_once(value, 'LYRICS_FILE_PREFIX = "zenith-lyrics"',
                        'LYRICS_FILE_PREFIX = "lyrics"', path)

def replay_share(value, path):
    value = replace_once(value, 'prefix: String = "zenith-replay"',
                         'prefix: String = "brrmusic-replay"', path)
    old = '"' + '$' + '{Environment.DIRECTORY_PICTURES}/Zenith"'
    new = '"' + '$' + '{Environment.DIRECTORY_PICTURES}/brrMusic"'
    return replace_once(value, old, new, path)

def main():
    if not config_enabled():
        print("brrMusic branding disabled; upstream visuals remain unchanged.")
        return
    for rel in (
        "app/src/main/res/values/strings.xml",
        "app/src/main/res/values-in/strings.xml",
        "app/src/main/res/values-ko/strings.xml",
        "sharedUi/src/commonMain/composeResources/values/strings.xml",
        "sharedUi/src/commonMain/composeResources/values-id/strings.xml",
        "sharedUi/src/commonMain/composeResources/values-ko/strings.xml",
    ):
        edit(rel, xml_text)
    edit("app/src/main/java/com/music/zenith/ui/player/LyricsShareCard.kt", lyrics_card)
    edit("app/src/main/java/com/music/zenith/ui/player/AndroidLyricsShareSheet.kt", lyrics_share)
    edit("app/src/main/java/com/music/zenith/ui/replay/ReplayPoster.kt", replay_poster)
    edit("app/src/main/java/com/music/zenith/ui/replay/ReplayShareSheet.kt", replay_share)
    edit("app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt", settings)
    edit("sharedUi/src/jvmSharedMain/kotlin/com/music/zenith/ui/screens/HomeScreen.kt", home)
    print("Brand-only overlay complete; Spatial Audio source unmodified.")

if __name__ == "__main__":
    main()
