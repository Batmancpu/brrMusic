"""One-release glass elasticity is intentionally narrow and reversible."""
import importlib.util
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("glass_once.py")
SPEC = importlib.util.spec_from_file_location("brr_glass_once", MODULE)
glass = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(glass)


class OneReleaseGlassTests(unittest.TestCase):
    def test_flag_parsing_and_disarm(self):
        self.assertTrue(glass.enabled("enabled=true\noneReleaseElastic=true\n"))
        self.assertFalse(glass.enabled("oneReleaseElastic=false\n"))
        with self.assertRaises(SystemExit):
            glass.enabled("enabled=true\n")
        with self.assertRaises(SystemExit):
            glass.enabled("oneReleaseElastic=true\noneReleaseElastic=false\n")

    def test_morph_uses_existing_bounded_jelly_only_on_controls(self):
        source = (
            'Box(Modifier.size(MORPH_CIRCLE)\n'
            '                .size(MORPH_CIRCLE)\n'
            '                .clip(shape)\n'
            '                .then(glass))\n'
            '                        .size(MORPH_CONTROL)\n'
            '                        .graphicsLayer { alpha = openFraction }\n'
            '                        .clickable('
        )
        amended = glass.search_morph(source)
        self.assertEqual(amended.count(".glassJelly("), 2)
        self.assertIn(".glassJelly(GLASS_JELLY_PRESS_WIDE)", amended)
        self.assertIn(".glassJelly()", amended)

    def test_search_bar_stretch_only_when_interactive(self):
        source = (
            "                .clip(shape)\n"
            "                .then(Modifier.liquidGlass(shape = shape))\n"
            "                .clickable("
        )
        amended = glass.search_bar(source)
        self.assertIn("if (interactive && tabClickable)", amended)
        self.assertEqual(amended.count(".glassJelly("), 1)

    def test_fails_closed_on_unrecognized_upstream_code(self):
        with self.assertRaises(SystemExit):
            glass.search_morph("unrelated upstream code")
        with self.assertRaises(SystemExit):
            glass.search_bar("unrelated upstream code")


if __name__ == "__main__":
    unittest.main()
