#!/usr/bin/env python3
"""A single-release, fail-closed touch-elasticity experiment.

Only augments interactive glass surfaces that have no existing elastic
feedback. Navigation pills, sliders, mini-player, segmented controls and
top-bar buttons already have custom spring/rubber interactions upstream;
adding another global scale would double-animate them and distort layout.
Never changes the component's hit testing, backdrop renderer, or lifecycle.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "fc/branding/config.properties"
MORPH = ROOT / "app/src/main/java/com/music/zenith/ui/components/GlassSearchMorph.kt"
SEARCH = ROOT / "app/src/main/java/com/music/zenith/ui/components/GlassSearchBar.kt"


def enabled(config_text: str) -> bool:
    values = re.findall(r"(?m)^oneReleaseElastic=(true|false)$", config_text)
    if len(values) != 1:
        raise SystemExit("Exactly one oneReleaseElastic boolean is required")
    return values[0] == "true"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Glass experiment stopped: {label}: expected once, found {count}")
    return source.replace(old, new, 1)


def search_morph(source: str) -> str:
    # Only the tappable collapsed circle, not the input field that already
    # morphs as the IME rises. Preserve sizes and click area.
    source = replace_once(
        source,
        "                .size(MORPH_CIRCLE)\n                .clip(shape)\n                .then(glass)",
        "                .size(MORPH_CIRCLE)\n"
        "                .glassJelly(GLASS_JELLY_PRESS_WIDE)\n"
        "                .clip(shape)\n                .then(glass)",
        "search-morph closed tab circle",
    )
    # An elastic tap on the separate, not-yet-animated back control. The
    # capsule itself already springs during opening and must not be scaled.
    return replace_once(
        source,
        "                        .size(MORPH_CONTROL)\n"
        "                        .graphicsLayer { alpha = openFraction }\n"
        "                        .clickable(",
        "                        .size(MORPH_CONTROL)\n"
        "                        .glassJelly()\n"
        "                        .graphicsLayer { alpha = openFraction }\n"
        "                        .clickable(",
        "search-morph back control",
    )


def search_bar(source: str) -> str:
    # Last-tab circle gets the same bounded width-aware elastic response.
    # Disabled during morph transitions or when that surface isn't clickable.
    return replace_once(
        source,
        "                .clip(shape)\n"
        "                .then(Modifier.liquidGlass(shape = shape))\n"
        "                .clickable(",
        "                .then(if (interactive && tabClickable) "
        "Modifier.glassJelly(GLASS_JELLY_PRESS_WIDE) else Modifier)\n"
        "                .clip(shape)\n"
        "                .then(Modifier.liquidGlass(shape = shape))\n"
        "                .clickable(",
        "search bar last-tab control",
    )


def apply() -> None:
    if not enabled(CONFIG.read_text(encoding="utf-8")):
        print("One-release glass elasticity disabled; upstream effects unchanged")
        return
    for path, transform in ((MORPH, search_morph), (SEARCH, search_bar)):
        initial = path.read_text(encoding="utf-8")
        amended = transform(initial)
        if initial == amended:
            raise SystemExit(f"Glass experiment made no change to {path.name}")
        path.write_text(amended, encoding="utf-8")
        print(f"One-release glass elasticity: {path.relative_to(ROOT)}")


if __name__ == "__main__":
    apply()
