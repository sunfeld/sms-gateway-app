"""
Unit tests for start_rapid_advertising() documentation in README.md.

Validates that the README correctly documents the start_rapid_advertising()
function including its role in high-frequency BLE advertising, hcitool/mgmt API
usage, HCI LE command details, advertising interval configuration, and
placement in the system architecture.
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


class TestRapidAdvertisingDocExists(unittest.TestCase):
    """Verify README.md exists and mentions start_rapid_advertising."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_readme_file_exists(self):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        self.assertTrue(os.path.exists(readme_path), "README.md not found")

    def test_start_rapid_advertising_mentioned(self):
        self.assertIn(
            "start_rapid_advertising()",
            self.readme,
            "start_rapid_advertising() not documented in README",
        )

    def test_hcitool_mgmt_api_context(self):
        """The function should be documented alongside hcitool/mgmt API."""
        self.assertIn(
            "hcitool/mgmt API",
            self.readme,
            "hcitool/mgmt API context for start_rapid_advertising not documented",
        )


class TestRapidAdvertisingArchitectureDiagram(unittest.TestCase):
    """Verify start_rapid_advertising appears in the architecture diagram."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_architecture_section_exists(self):
        self.assertIn(
            "### Architecture",
            self.readme,
            "Architecture section missing from README",
        )

    def test_rapid_advertising_in_architecture(self):
        """start_rapid_advertising should be listed under attack_orchestrator."""
        self.assertIn(
            "start_rapid_advertising() (hcitool/mgmt API)",
            self.readme,
            "start_rapid_advertising() not in architecture diagram",
        )

    def test_attack_orchestrator_in_architecture(self):
        self.assertIn(
            "attack_orchestrator.py",
            self.readme,
            "attack_orchestrator.py not in architecture diagram",
        )


class TestRapidAdvertisingComponentFlow(unittest.TestCase):
    """Verify the component flow documents start_rapid_advertising's role."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_component_flow_section_exists(self):
        self.assertIn(
            "**Component flow:**",
            self.readme,
            "Component flow section missing from README",
        )

    def test_rapid_advertising_step_in_flow(self):
        """Step 5 describes start_rapid_advertising beginning high-frequency ads."""
        self.assertIn(
            "`start_rapid_advertising()` begins high-frequency BLE advertisements",
            self.readme,
            "start_rapid_advertising step missing from component flow",
        )

    def test_configured_interval_mentioned(self):
        """The component flow step should mention configured interval."""
        self.assertIn(
            "at the configured interval",
            self.readme,
            "Configured interval not mentioned in rapid advertising flow step",
        )


class TestRapidAdvertisingIntensityLevels(unittest.TestCase):
    """Verify intensity levels document advertising intervals."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_intensity_levels_section(self):
        self.assertIn(
            "Intensity levels",
            self.readme,
            "Intensity levels section missing from README",
        )

    def test_advertising_interval_column(self):
        """Intensity table should have an Advertising Interval column."""
        self.assertIn(
            "Advertising Interval",
            self.readme,
            "Advertising Interval column missing from intensity levels table",
        )

    def test_20ms_interval_documented(self):
        """Minimum 20ms advertising interval should appear for high intensity."""
        self.assertIn(
            "20ms",
            self.readme,
            "20ms advertising interval not documented",
        )

    def test_100ms_interval_documented(self):
        """Baseline 100ms interval for level 1."""
        self.assertIn(
            "100ms",
            self.readme,
            "100ms advertising interval not documented",
        )

    def test_concurrent_connections_column(self):
        """Intensity table should document concurrent connection counts."""
        self.assertIn(
            "Concurrent Connections",
            self.readme,
            "Concurrent Connections column missing from intensity levels table",
        )


class TestRapidAdvertisingDependencies(unittest.TestCase):
    """Verify backend dependencies for rapid advertising are documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_hcitool_listed_as_dependency(self):
        self.assertIn(
            "hcitool",
            self.readme,
            "hcitool not listed in dependencies",
        )

    def test_hcitool_purpose_documented(self):
        self.assertIn(
            "Low-level HCI advertising control",
            self.readme,
            "hcitool purpose not documented",
        )

    def test_bluez_dependency_listed(self):
        self.assertIn(
            "bluez",
            self.readme,
            "bluez dependency not documented",
        )

    def test_dbus_python_dependency_listed(self):
        self.assertIn(
            "dbus-python",
            self.readme,
            "dbus-python dependency not documented",
        )


class TestRapidAdvertisingAPIEndpoint(unittest.TestCase):
    """Verify the API endpoint documentation covers rapid advertising context."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_stress_test_api_section_exists(self):
        self.assertIn(
            "## Bluetooth Stress Test API",
            self.readme,
            "Bluetooth Stress Test API section missing",
        )

    def test_endpoint_documented(self):
        self.assertIn(
            "POST /api/bluetooth/dos/start",
            self.readme,
            "Stress test start endpoint not documented",
        )

    def test_intensity_controls_advertising_interval(self):
        """Intensity parameter should be documented as controlling advertising interval frequency."""
        self.assertIn(
            "advertising interval frequency",
            self.readme,
            "Intensity-to-advertising-interval relationship not documented",
        )

    def test_duration_parameter_documented(self):
        self.assertIn(
            "duration",
            self.readme,
            "Duration parameter not documented in API endpoint",
        )


class TestRapidAdvertisingCodeDocstrings(unittest.TestCase):
    """Verify that start_rapid_advertising has proper docstrings in the source."""

    @classmethod
    def setUpClass(cls):
        source_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "bluetooth_manager.py",
        )
        with open(source_path, "r") as f:
            cls.source = f.read()

    def test_start_rapid_advertising_has_docstring(self):
        self.assertIn(
            "Start BLE advertising with high-frequency intervals",
            self.source,
            "start_rapid_advertising missing docstring",
        )

    def test_docstring_documents_hci_commands(self):
        self.assertIn(
            "LE Set Advertising Parameters",
            self.source,
            "HCI LE Set Advertising Parameters not documented in docstring",
        )

    def test_docstring_documents_enable_command(self):
        self.assertIn(
            "LE Set Advertise Enable",
            self.source,
            "HCI LE Set Advertise Enable not documented in docstring",
        )

    def test_docstring_documents_interval_args(self):
        self.assertIn(
            "interval_min_ms",
            self.source,
            "interval_min_ms argument not documented",
        )
        self.assertIn(
            "interval_max_ms",
            self.source,
            "interval_max_ms argument not documented",
        )

    def test_docstring_documents_return_type(self):
        self.assertIn(
            "Dict with advertising configuration details",
            self.source,
            "Return type not documented in docstring",
        )

    def test_docstring_documents_value_error(self):
        self.assertIn(
            "ValueError",
            self.source,
            "ValueError raise condition not documented",
        )

    def test_stop_rapid_advertising_has_docstring(self):
        self.assertIn(
            "Stop BLE advertising on the adapter",
            self.source,
            "stop_rapid_advertising missing docstring",
        )


if __name__ == "__main__":
    unittest.main()
