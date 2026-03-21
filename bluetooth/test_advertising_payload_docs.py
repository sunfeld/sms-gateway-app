"""
Unit tests for AdvertisingPayload documentation in README.md.

Validates that the README correctly documents the AdvertisingPayload
component including its role in MAC rotation, device name spoofing,
architecture placement, and component flow description.
"""

import unittest
import os


class TestAdvertisingPayloadDocumentation(unittest.TestCase):
    """Verify README.md contains complete AdvertisingPayload documentation."""

    @classmethod
    def setUpClass(cls):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        with open(readme_path, "r") as f:
            cls.readme_content = f.read()
        cls.readme_lines = cls.readme_content.splitlines()

    def test_readme_file_exists(self):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        self.assertTrue(os.path.exists(readme_path), "README.md not found")

    def test_advertising_payload_mentioned_in_readme(self):
        self.assertIn(
            "AdvertisingPayload",
            self.readme_content,
            "AdvertisingPayload not documented in README",
        )

    def test_mac_rotation_documented(self):
        self.assertIn(
            "MAC rotation",
            self.readme_content,
            "MAC rotation capability not documented",
        )

    def test_device_name_spoofing_documented(self):
        self.assertIn(
            "device name spoofing",
            self.readme_content,
            "Device name spoofing capability not documented",
        )

    def test_randomized_mac_addresses_in_component_flow(self):
        self.assertIn(
            "randomized MAC addresses",
            self.readme_content,
            "Component flow should describe randomized MAC address generation",
        )

    def test_device_name_rotation_in_component_flow(self):
        self.assertIn(
            "rotates device names",
            self.readme_content,
            "Component flow should describe device name rotation",
        )


class TestAdvertisingPayloadArchitectureDiagram(unittest.TestCase):
    """Verify AdvertisingPayload appears in the architecture diagram."""

    @classmethod
    def setUpClass(cls):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        with open(readme_path, "r") as f:
            cls.readme_content = f.read()

    def test_architecture_section_exists(self):
        self.assertIn(
            "### Architecture",
            self.readme_content,
            "Architecture section missing from README",
        )

    def test_advertising_payload_in_architecture_diagram(self):
        """AdvertisingPayload should be listed as a component under attack_orchestrator."""
        self.assertIn(
            "AdvertisingPayload (MAC rotation + device name spoofing)",
            self.readme_content,
            "AdvertisingPayload not shown in architecture diagram",
        )

    def test_architecture_shows_orchestrator_hierarchy(self):
        """AdvertisingPayload should appear under attack_orchestrator.py in the diagram."""
        self.assertIn(
            "attack_orchestrator.py",
            self.readme_content,
            "attack_orchestrator.py not in architecture diagram",
        )


class TestAdvertisingPayloadComponentFlow(unittest.TestCase):
    """Verify the component flow documents AdvertisingPayload's role."""

    @classmethod
    def setUpClass(cls):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        with open(readme_path, "r") as f:
            cls.readme_content = f.read()

    def test_component_flow_section_exists(self):
        self.assertIn(
            "**Component flow:**",
            self.readme_content,
            "Component flow section missing from README",
        )

    def test_advertising_payload_step_in_flow(self):
        """Step 4 should describe AdvertisingPayload generating MACs and rotating names."""
        self.assertIn(
            "`AdvertisingPayload` generates randomized MAC addresses and rotates device names",
            self.readme_content,
            "AdvertisingPayload step missing from component flow",
        )

    def test_example_device_names_documented(self):
        """Documentation should include example device names for rotation."""
        self.assertIn(
            "Apple Magic Keyboard",
            self.readme_content,
            "Example device name 'Apple Magic Keyboard' not documented",
        )
        self.assertIn(
            "Logitech K380",
            self.readme_content,
            "Example device name 'Logitech K380' not documented",
        )


class TestAdvertisingPayloadIntensityLevels(unittest.TestCase):
    """Verify intensity level documentation covers MAC rotation context."""

    @classmethod
    def setUpClass(cls):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        with open(readme_path, "r") as f:
            cls.readme_content = f.read()

    def test_intensity_levels_documented(self):
        self.assertIn(
            "Intensity levels",
            self.readme_content,
            "Intensity levels section missing from README",
        )

    def test_mac_rotation_at_high_intensity(self):
        """Level 4 description should mention MAC rotation."""
        self.assertIn(
            "MAC rotation",
            self.readme_content,
            "MAC rotation not mentioned in intensity levels",
        )

    def test_five_intensity_levels(self):
        """Should document 5 intensity levels."""
        for level in range(1, 6):
            self.assertIn(
                f"| {level} |",
                self.readme_content,
                f"Intensity level {level} not documented",
            )


class TestAdvertisingPayloadDependencies(unittest.TestCase):
    """Verify backend dependencies needed by AdvertisingPayload are documented."""

    @classmethod
    def setUpClass(cls):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        with open(readme_path, "r") as f:
            cls.readme_content = f.read()

    def test_dependencies_section_exists(self):
        self.assertIn(
            "### Dependencies (Backend)",
            self.readme_content,
            "Dependencies section missing",
        )

    def test_dbus_python_dependency_listed(self):
        self.assertIn(
            "dbus-python",
            self.readme_content,
            "dbus-python dependency not documented",
        )

    def test_bleak_dependency_listed(self):
        self.assertIn(
            "bleak",
            self.readme_content,
            "bleak dependency not documented",
        )

    def test_hcitool_dependency_listed(self):
        self.assertIn(
            "hcitool",
            self.readme_content,
            "hcitool dependency not documented",
        )


class TestStressTestAPIDocumentation(unittest.TestCase):
    """Verify the Bluetooth Stress Test API section documents AdvertisingPayload context."""

    @classmethod
    def setUpClass(cls):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        with open(readme_path, "r") as f:
            cls.readme_content = f.read()

    def test_stress_test_api_section_exists(self):
        self.assertIn(
            "## Bluetooth Stress Test API",
            self.readme_content,
            "Bluetooth Stress Test API section missing",
        )

    def test_endpoint_documented(self):
        self.assertIn(
            "POST /api/bluetooth/dos/start",
            self.readme_content,
            "Stress test endpoint not documented",
        )

    def test_duration_parameter_documented(self):
        self.assertIn(
            "duration",
            self.readme_content,
            "Duration parameter not documented",
        )

    def test_intensity_parameter_documented(self):
        self.assertIn(
            "intensity",
            self.readme_content,
            "Intensity parameter not documented",
        )

    def test_response_format_documented(self):
        self.assertIn(
            "session_id",
            self.readme_content,
            "Response format (session_id) not documented",
        )

    def test_conflict_response_documented(self):
        self.assertIn(
            "already_running",
            self.readme_content,
            "409 conflict response not documented",
        )


if __name__ == "__main__":
    unittest.main()
