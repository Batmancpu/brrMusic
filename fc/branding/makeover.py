#!/usr/bin/env python3
"""Build-only, reversible brrMusic visual identity makeover.

Apply after fc/branding/apply.py. Never change package names, signing, deep links,
upstream service URLs, or on-disk legacy download identifiers.
"""
from __future__ import annotations

import re
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main"
SHARED = ROOT / "sharedUi/src/commonMain/composeResources"
UI_ROOTS = (
    ROOT / "app/src/main/java/com/music/zenith/ui",
    ROOT / "sharedUi/src/jvmSharedMain/kotlin/com/music/zenith/ui",
)
BRAND = "brrMusic"


def edit(path: Path, transform, required: bool = True) -> bool:
    if not path.is_file():
        if required:
            raise SystemExit(f"brrMusic visual overlay: missing {path.relative_to(ROOT)}")
        return False
    previous = path.read_text(encoding="utf-8")
    updated = transform(previous)
    if updated != previous:
        path.write_text(updated, encoding="utf-8")
        print(f"Visual identity: {path.relative_to(ROOT)}")
        return True
    return False


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Visual overlay: expected exactly one {label}; found {count}")
    return text.replace(old, new, 1)


# A new, deliberately unbranded sound-wave symbol, not the upstream Z mark.
WAVE_PATHS = (
    '  <path android:pathData="M30,34L30,38" android:strokeColor="#FFFFFFFF" '
    'android:strokeWidth="5" android:strokeLineCap="round"/>\n'
    '  <path android:pathData="M42,23L42,49" android:strokeColor="#FFFFFFFF" '
    'android:strokeWidth="5" android:strokeLineCap="round"/>\n'
    '  <path android:pathData="M54,13L54,59" android:strokeColor="#FFFFFFFF" '
    'android:strokeWidth="5" android:strokeLineCap="round"/>\n'
    '  <path android:pathData="M66,23L66,49" android:strokeColor="#FFFFFFFF" '
    'android:strokeWidth="5" android:strokeLineCap="round"/>\n'
    '  <path android:pathData="M78,34L78,38" android:strokeColor="#FFFFFFFF" '
    'android:strokeWidth="5" android:strokeLineCap="round"/>\n'
)


def vector(width: str, height: str, viewport_height: int, dark: bool = False) -> str:
    paths = WAVE_PATHS
    if viewport_height == 108:
        paths = f'  <group android:translateY="18">\n{WAVE_PATHS}  </group>\n'
    background = ""
    if viewport_height == 108:
        fill = "#101629" if dark else "#6268BD"
        background = ('  <path android:fillColor="' + fill +
                      '" android:pathData="M18,42C18,28.7 28.7,18 42,18L66,18'
                      'C79.3,18 90,28.7 90,42L90,66C90,79.3 79.3,90 66,90'
                      'L42,90C28.7,90 18,79.3 18,66Z"/>\n')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{width}" android:height="{height}"\n'
            f'    android:viewportWidth="108" android:viewportHeight="{viewport_height}">\n'
            + background + paths + '</vector>\n')


def svg_asset(transparent: bool) -> str:
    rect = '' if transparent else '<rect width="1024" height="1024" rx="200" fill="#11172A"/>'
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'
            + rect + '<g fill="none" stroke="#E8EAFF" stroke-width="44" stroke-linecap="round">'
            '<path d="M310 492v40M410 390v244M512 308v408M614 390v244M714 492v40"/>'
            '</g></svg>\n')


def remove_startup_overlay(value: str) -> str:
    # Remove the extra full-screen Compose splash and its artificial
    # first-frame gate. Android's system-provided launch window remains.
    start = "        setContent {\n            AppSplash(\n"
    end = "            if (!contentReady) return@setContent\n"
    if value.count(start) != 1 or value.count(end) != 1:
        raise SystemExit("AppSplash integration changed upstream; review before release")
    prefix, rest = value.split(start, 1)
    discarded, suffix = rest.split(end, 1)
    if not all(token in discarded for token in (
        "setImageResource(R.drawable.splash_logo_dark_glass)",
        "contentReady = true", "withFrameNanos",
    )):
        raise SystemExit("Unexpected startup composition; refusing unsafe splash removal")
    value = prefix + "        setContent {\n" + suffix
    return once(value, "import com.music.zenith.ui.screens.AppSplash\n", "",
                "unused AppSplash import")


# Only Kotlin UI string literals, never package paths, identifiers, URLs, or API IDs.
QUOTED = re.compile(r'"(?:\\.|[^"\\\n])*"')
HUMAN_BRAND = re.compile(r'(?<![A-Za-z0-9_])Zenith(?![A-Za-z0-9_])')
SKIP_LITERALS = ("http:", "https:", "com.music.", ".firebaseapp.com", "zenith-canvas",
                 "com.zeux.", "github.com/", "Zenith/", "Music/Zenith",
                 "zenith://", "zenith-", "org.zenith")


def kotlin_ui_copy(value: str) -> str:
    def rewrite(match):
        original = match.group()
        if any(key in original for key in SKIP_LITERALS):
            if original in ('"Music/Zenith"',):
                return '"Music downloads"'
            return original
        return HUMAN_BRAND.sub(BRAND, original)
    return QUOTED.sub(rewrite, value)


def settings_footer(value: str) -> str:
    # The visible GitHub/developer links belong to this fork, while upstream
    # receives an explicit attribution link (not falsely labelled as our Reddit).
    value = once(value, 'context.openLink("https://github.com/rezapranata089-bit/Zenith")',
                 'context.openLink("https://github.com/Batmancpu/brrMusic")', "project GitHub URL")
    # Upstream moved the developer link to TikTok in the 2026-11-26 snapshot.
    # Accept either reviewed URL, and continue to label this fork's maintainer correctly.
    developer_urls = (
        'context.openLink("https://github.com/rezapranata089-bit")',
        'context.openLink("https://www.tiktok.com/@zeux.editz?_r=1&_t=ZS-9BgOcHcAans")',
    )
    matched = [url for url in developer_urls if value.count(url) == 1]
    if len(matched) != 1:
        raise SystemExit("Settings developer link changed beyond reviewed variants")
    value = once(value, matched[0], 'context.openLink("https://github.com/Batmancpu")', "maintainer URL")
    value = once(value, 'text = "Reddit",', 'text = "Upstream",', "upstream label")
    value = once(value, 'context.openLink("https://www.reddit.com/r/ZenithMusic/s/gN6chb7onR")',
                 'context.openLink("https://github.com/rezapranata089-bit/Zenith")', "upstream credit URL")
    value = once(value, 'text = "Made by Zeuxxi",', 'text = "A more personal way to listen.",', "identity footer")
    return value


def repair_resource_labels(path: Path) -> None:
    # Korean and other scripts join suffix characters directly to the product
    # name; ASCII word-boundary replacement silently leaves these untranslated.
    # Change ONLY text nodes, never XML attribute names, IDs or resource keys.
    def fix(value):
        value = re.sub(
            r'>([^<>]*)<',
            lambda match: '>' + match.group(1).replace("Zenith", BRAND) + '<',
            value,
        )
        # Historical storage must keep its actual on-disk path; describe it
        # without claiming the old files were moved to a new branded folder.
        value = value.replace("Music/brrMusic", "Music downloads")
        text_nodes = re.findall(r'>([^<>]*)<', value)
        if any("Zenith" in node for node in text_nodes):
            raise SystemExit(f"Visible Zenith resource remained: {path.relative_to(ROOT)}")
        ET.fromstring(value)
        return value
    edit(path, fix)


def main() -> None:
    config = (ROOT / "fc/branding/config.properties").read_text(encoding="utf-8")
    enabled = re.search(r"(?m)^enabled=(true|false)$", config)
    if not enabled:
        raise SystemExit("brrMusic brand flag missing")
    if enabled.group(1) == "false":
        print("brrMusic visual makeover disabled")
        return

    # Android 12+ shows the system launch surface before the Compose splash.
    # Keep its first frame in the same midnight palette as the animation.
    for qualifier in ("values", "values-night"):
        theme_file = APP / "res" / qualifier / "themes.xml"
        edit(theme_file, lambda value: once(value, "#FB4059", "#0B1020", "native splash background"))
        ET.fromstring(theme_file.read_text(encoding="utf-8"))

    # No marketing splash; Home shows only "Music" via the first branding layer.
    edit(ROOT / "app/src/main/java/com/music/zenith/MainActivity.kt", remove_startup_overlay)

    # All icon options and all startup mark variants cease displaying the Z.
    vector_files = {
        APP / "res/drawable/ic_logo.xml": vector("28dp", "18dp", 72),
        SHARED / "drawable/ic_logo.xml": vector("28dp", "18dp", 72),
        APP / "res/drawable/splash_logo_dark_glass.xml": vector("738dp", "488dp", 72),
        APP / "res/drawable/ic_zenith_glass_foreground.xml": vector("108dp", "108dp", 108),
        APP / "res/drawable/ic_zenith_dark_glass_foreground.xml": vector("108dp", "108dp", 108, dark=True),
    }
    for path, contents in vector_files.items():
        edit(path, lambda _previous, contents=contents: contents)
        ET.fromstring(path.read_text(encoding="utf-8"))

    for filename, transparent in (("Logo.svg", False), ("LogoTransparent.svg", True)):
        edit(APP / "assets" / filename, lambda _old, t=transparent: svg_asset(t))

    edit(ROOT / "app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt", settings_footer)
    edit(ROOT / "app/src/main/java/com/music/zenith/ui/components/StarPromptDialog.kt",
         lambda value: once(value,
             'STAR_REPO_URL = "https://github.com/rezapranata089-bit/Zenith"',
             'STAR_REPO_URL = "https://github.com/Batmancpu/brrMusic"',
             "star prompt destination"))

    for directory in UI_ROOTS:
        if not directory.is_dir():
            raise SystemExit(f"Missing UI directory: {directory.relative_to(ROOT)}")
        for file in sorted(directory.rglob("*.kt")):
            if file.name in ("AppSplash.kt", "Wordmark.kt"):
                continue
            edit(file, kotlin_ui_copy, required=False)
    # Earlier upstream text assets were processed by fc/branding/apply.py.
    for path in (
        APP / "res/values/strings.xml",
        APP / "res/values-in/strings.xml",
        APP / "res/values-ko/strings.xml",
        SHARED / "values/strings.xml",
        SHARED / "values-id/strings.xml",
        SHARED / "values-ko/strings.xml",
    ):
        repair_resource_labels(path)
    print("brrMusic visual makeover verified; package, services and storage identifiers unchanged")


if __name__ == "__main__":
    main()
