"""Regression checks for the build-time brrMusic visual identity."""
import importlib.util
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("makeover.py")
SPEC = importlib.util.spec_from_file_location("brr_makeover", MODULE)
makeover = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(makeover)


class MakeoverTests(unittest.TestCase):
    def test_ui_brand_text_only(self):
        sample = (
            'val heading = "Welcome to Zenith"\n'
            'val internal = "com.music.zenith"\n'
            'val upstream = "https://github.com/rezapranata089-bit/Zenith"\n'
            'val uri = "Music/Zenith"\n'
            'val type = ZenithIcons.Play\n'
        )
        edited = makeover.kotlin_ui_copy(sample)
        self.assertIn('"Welcome to brrMusic"', edited)
        self.assertIn('"com.music.zenith"', edited)
        self.assertIn('rezapranata089-bit/Zenith', edited)
        self.assertIn('"Music downloads"', edited)
        self.assertIn('ZenithIcons.Play', edited)

    def test_direct_launch_removes_branded_splash_and_frame_gate(self):
        sample = (
            "import com.music.zenith.ui.screens.AppSplash\n"
            "class MainActivity {\n"
            "        setContent {\n"
            "            AppSplash(\n"
            "                mark = { size ->\n"
            "                    setImageResource(R.drawable.splash_logo_dark_glass)\n"
            "                },\n"
            "            )\n"
            "            // The splash is drawn alone on first frame\n"
            "            var contentReady = true\n"
            "            withFrameNanos { }\n"
            "            if (!contentReady) return@setContent\n"
            "            DrawMainApp()\n"
            "        }\n"
            "}\n"
        )
        edited = makeover.remove_startup_overlay(sample)
        self.assertIn("setContent {\n            DrawMainApp()", edited)
        self.assertNotIn("AppSplash", edited)
        self.assertNotIn("contentReady", edited)
        self.assertNotIn("withFrameNanos", edited)

    def test_direct_launch_fail_closed_on_changed_upstream(self):
        with self.assertRaises(SystemExit):
            makeover.remove_startup_overlay("setContent { AppSplash() }")

    def test_all_mark_variants_are_original_waveforms(self):
        for h in (72, 108):
            for dark in (False, True):
                v = makeover.vector("108dp", "108dp", h, dark)
                self.assertIn("android:viewportWidth=", v)
                self.assertEqual(v.count("android:strokeLineCap="), 5)
                self.assertNotIn("M163.6,278.8", v)

    def test_asset_logo_is_not_upstream_z(self):
        for transparent in (True, False):
            image = makeover.svg_asset(transparent)
            self.assertIn("<svg", image)
            self.assertIn('stroke="#E8EAFF"', image)
            self.assertNotIn("Zenith", image)

    def test_fail_closed_for_missing_single_match(self):
        with self.assertRaises(SystemExit):
            makeover.once("no matching text", "unavailable", "new", "unit test")


if __name__ == "__main__":
    unittest.main()
