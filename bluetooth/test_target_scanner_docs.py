"""
Unit tests for TargetScanner documentation in README.md.

Validates that the README correctly documents the TargetScanner
module including its role in BLE scanning, Apple device identification
via manufacturer data (0x004C), architecture placement, component flow
description, and source code docstrings.
"""

import unittest
import os


def _load_readme():
    """Load README.md content from the project root."""
    readme_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "README.md",
    )
    with open(readme_path, "r") as f:
        return f.read()


class TestTargetScannerDocExists(unittest.TestCase):
    """Verify README.md exists and mentions TargetScanner."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_readme_file_exists(self):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        self.assertTrue(os.path.exists(readme_path), "README.md not found")

    def test_target_scanner_mentioned_in_readme(self):
        self.assertIn(
            "TargetScanner",
            self.readme,
            "TargetScanner not documented in README",
        )

    def test_bleak_library_association(self):
        """TargetScanner should be documented alongside bleak library."""
        self.assertIn(
            "TargetScanner (bleak)",
            self.readme,
            "TargetScanner/bleak association not documented",
        )

    def test_apple_manufacturer_data_documented(self):
        """Apple manufacturer data filtering should be documented."""
        self.assertIn(
            "0x004c",
            self.readme,
            "Apple manufacturer data ID 0x004c not documented",
        )


class TestTargetScannerArchitectureDiagram(unittest.TestCase):
    """Verify TargetScanner appears in the architecture diagram."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_architecture_section_exists(self):
        self.assertIn(
            "### Architecture",
            self.readme,
            "Architecture section missing from README",
        )

    def test_target_scanner_in_architecture(self):
        """TargetScanner should be listed under attack_orchestrator."""
        self.assertIn(
            "TargetScanner (bleak)",
            self.readme,
            "TargetScanner not in architecture diagram",
        )

    def test_attack_orchestrator_in_architecture(self):
        self.assertIn(
            "attack_orchestrator.py",
            self.readme,
            "attack_orchestrator.py not in architecture diagram",
        )

    def test_target_scanner_feeds_target_list(self):
        """Architecture should show TargetScanner feeding a target list."""
        self.assertIn(
            "target_list",
            self.readme,
            "TargetScanner target_list flow not documented in architecture",
        )


class TestTargetScannerComponentFlow(unittest.TestCase):
    """Verify the component flow documents TargetScanner's role."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_component_flow_section_exists(self):
        self.assertIn(
            "**Component flow:**",
            self.readme,
            "Component flow section missing from README",
        )

    def test_target_scanner_step_in_flow(self):
        """Step 3 should describe TargetScanner discovering nearby devices."""
        self.assertIn(
            "`TargetScanner` discovers nearby devices via BLE scan",
            self.readme,
            "TargetScanner step missing from component flow",
        )

    def test_apple_filtering_in_flow(self):
        """Component flow should mention filtering Apple manufacturer data."""
        self.assertIn(
            "filters Apple manufacturer data",
            self.readme,
            "Apple manufacturer data filtering not mentioned in component flow",
        )

    def test_ios_targets_mentioned_in_flow(self):
        """Component flow should mention iOS targets."""
        self.assertIn(
            "iOS targets",
            self.readme,
            "iOS targets not mentioned in component flow step",
        )


class TestTargetScannerAsyncOrchestration(unittest.TestCase):
    """Verify the async orchestration section documents TargetScanner."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_async_orchestration_section_exists(self):
        self.assertIn(
            "Async Orchestration Loop",
            self.readme,
            "Async orchestration section missing from README",
        )

    def test_target_scanner_populates_targets(self):
        """Step 1 should describe TargetScanner populating the target list."""
        self.assertIn(
            "`TargetScanner` populates the target list",
            self.readme,
            "TargetScanner target list population not documented",
        )

    def test_asyncio_gather_documented(self):
        """Async orchestration should document asyncio.gather for concurrency."""
        self.assertIn(
            "asyncio.gather",
            self.readme,
            "asyncio.gather concurrency model not documented",
        )


class TestTargetScannerDependencies(unittest.TestCase):
    """Verify backend dependencies for TargetScanner are documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_dependencies_section_exists(self):
        self.assertIn(
            "### Dependencies (Backend)",
            self.readme,
            "Dependencies (Backend) section missing from README",
        )

    def test_bleak_dependency_listed(self):
        self.assertIn(
            "bleak",
            self.readme,
            "bleak dependency not documented",
        )

    def test_bleak_purpose_documented(self):
        self.assertIn(
            "BLE scanning and device discovery",
            self.readme,
            "bleak purpose (BLE scanning and device discovery) not documented",
        )

    def test_dbus_python_dependency_listed(self):
        self.assertIn(
            "dbus-python",
            self.readme,
            "dbus-python dependency not documented",
        )


class TestTargetScannerSourceDocstrings(unittest.TestCase):
    """Verify that target_scanner.py has proper docstrings."""

    @classmethod
    def setUpClass(cls):
        source_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "target_scanner.py",
        )
        with open(source_path, "r") as f:
            cls.source = f.read()

    def test_module_docstring_present(self):
        self.assertIn(
            "TargetScanner",
            self.source,
            "Module docstring should mention TargetScanner",
        )

    def test_module_documents_bleak(self):
        self.assertIn(
            "bleak",
            self.source,
            "Module docstring should mention bleak library",
        )

    def test_module_documents_apple_company_id(self):
        self.assertIn(
            "0x004C",
            self.source,
            "Apple company ID 0x004C not documented in source",
        )

    def test_class_docstring_present(self):
        self.assertIn(
            "BLE scanner that identifies nearby iOS/Apple devices",
            self.source,
            "TargetScanner class docstring missing",
        )

    def test_scan_method_docstring(self):
        self.assertIn(
            "Perform a BLE scan and return detected Apple/iOS devices",
            self.source,
            "scan() method docstring missing",
        )

    def test_is_apple_device_docstring(self):
        self.assertIn(
            "Check if an advertisement contains Apple manufacturer data",
            self.source,
            "is_apple_device() docstring missing",
        )

    def test_parse_apple_msg_type_docstring(self):
        self.assertIn(
            "Parse the Apple continuity protocol message type",
            self.source,
            "parse_apple_msg_type() docstring missing",
        )

    def test_detection_callback_docstring(self):
        self.assertIn(
            "Callback invoked by BleakScanner",
            self.source,
            "_detection_callback() docstring missing",
        )

    def test_apple_msg_types_documented(self):
        """Source should document known Apple message types."""
        for msg_type in ["AirDrop", "Handoff", "Nearby Action", "AirPods", "iBeacon", "Find My"]:
            self.assertIn(
                msg_type,
                self.source,
                f"Apple message type '{msg_type}' not documented in source",
            )


if __name__ == "__main__":
    unittest.main()
