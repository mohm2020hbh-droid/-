import json
import os
import tempfile
import unittest
import zipfile

from fx.discover import discover
from fx.ledger import Ledger, cap_confidence
from fx.validate import validate


class LedgerTest(unittest.TestCase):
    def test_caps(self):
        self.assertEqual(cap_confidence("UNKNOWN", "HIGH"), "NONE")
        self.assertEqual(cap_confidence("ESTIMATED", "HIGH"), "MEDIUM")
        self.assertEqual(cap_confidence("INFERRED", "HIGH", [{"confidence": "HIGH"}]), "MEDIUM")
        self.assertEqual(cap_confidence("DERIVED", "HIGH", [{"confidence": "LOW"}]), "LOW")
        self.assertEqual(cap_confidence("EXTRACTED", "HIGH"), "HIGH")

    def test_rules_enforced(self):
        with tempfile.TemporaryDirectory() as out:
            led = Ledger(out)
            with self.assertRaises(ValueError):
                led.add(stage="s", key="k", value=1, status="EXTRACTED",
                        confidence="HIGH", method="m")
            with self.assertRaises(ValueError):
                led.add(stage="s", key="k", value=None, status="UNKNOWN",
                        confidence="NONE", method="m")

    def test_replace_stage_cascades(self):
        with tempfile.TemporaryDirectory() as out:
            led = Ledger(out)
            a = led.add(stage="a", key="x", value=1, status="DERIVED",
                        confidence="HIGH", method="m")
            led.add(stage="b", key="y", value=2, status="DERIVED",
                    confidence="HIGH", method="m", parents=[a])
            led.replace_stage("a")
            self.assertEqual(led.records(), [])


class DiscoverTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.target = os.path.join(self.tmp.name, "target")
        self.out = os.path.join(self.tmp.name, "out")
        os.makedirs(self.target)
        with open(os.path.join(self.target, "lib.so"), "wb") as f:
            f.write(b"\x7fELF" + b"\x00" * 60)
        with zipfile.ZipFile(os.path.join(self.target, "game.apk"), "w") as z:
            z.writestr("AndroidManifest.xml", "<manifest/>")
            z.writestr("../evil.txt", "x")

    def tearDown(self):
        self.tmp.cleanup()

    def test_discover_and_validate(self):
        inv = {os.path.basename(e["path"]): e for e in discover(self.target, self.out)}
        self.assertEqual(inv["lib.so"]["type"], "elf")
        self.assertEqual(inv["game.apk"]["archive"]["flavour"], "apk")
        self.assertEqual(inv["game.apk"]["archive"]["unsafe_paths"], ["../evil.txt"])
        self.assertTrue(validate(self.out)["ok"])

    def test_validate_catches_tampering(self):
        discover(self.target, self.out)
        with open(os.path.join(self.target, "lib.so"), "wb") as f:
            f.write(b"NOPE")
        rep = validate(self.out)
        self.assertFalse(rep["ok"])
        self.assertTrue(any("no longer match" in e for e in rep["errors"]))

    def test_rerun_replaces_records(self):
        discover(self.target, self.out)
        n = len(Ledger(self.out).records())
        discover(self.target, self.out)
        self.assertEqual(len(Ledger(self.out).records()), n)
        with open(os.path.join(self.out, "work", "inventory.json")) as f:
            self.assertEqual(len(json.load(f)), 2)


if __name__ == "__main__":
    unittest.main()
