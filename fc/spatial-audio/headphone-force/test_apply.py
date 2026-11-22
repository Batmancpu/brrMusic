"""Regression tests for safe, explicitly labeled headphone surround."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("headphone_force", Path(__file__).with_name("apply.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class HeadphoneForceTests(unittest.TestCase):
    def test_settings_only_adds_software_mode(self):
        sample = (
            "    val spatialAudioOnPhoneSpeaker by AppSettings.spatialAudioOnPhoneSpeaker.collectAsStateWithLifecycle()\n"
            "            val spatialLearningTitle = stringResource(R.string.spatial_audio_learning_enabled)\n"
        )
        altered = module.patch(sample)
        self.assertIn("Force headphone surround", altered)
        self.assertIn("not Dolby Atmos", altered)
        self.assertIn("SpatialAudioMode.ON", altered)
        self.assertIn("SpatialAudioMode.AUTO", altered)
        self.assertNotIn("DeviceCodecs.forced", altered)
        self.assertNotIn("setDolbyAtmos", altered)

    def test_missing_upstream_anchor_aborts(self):
        with self.assertRaises(SystemExit):
            module.patch("unrecognized layout")

if __name__ == "__main__":
    unittest.main()
