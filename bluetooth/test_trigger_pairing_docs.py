"""
Unit tests for trigger_pairing_request() documentation in README.md.

Validates that the README correctly documents the trigger_pairing_request()
function including its role in the architecture, component flow, pairing
mechanism, and that the source code has proper docstrings.
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


def _load_source():
    """Load bluetooth_manager.py source content."""
    source_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "bluetooth_manager.py",
    )
    with open(source_path, "r") as f:
        return f.read()


class TestTriggerPairingDocExists(unittest.TestCase):
    """Verify README.md exists and mentions trigger_pairing_request."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_readme_file_exists(self):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        self.assertTrue(os.path.exists(readme_path), "README.md not found")

    def test_trigger_pairing_request_mentioned_in_readme(self):
        self.assertIn(
            "trigger_pairing_request",
            self.readme,
            "trigger_pairing_request not documented in README",
        )

    def test_hid_connection_attempts_documented(self):
        """trigger_pairing_request role as HID connection initiator should be documented."""
        self.assertIn(
            "HID connection attempts",
            self.readme,
            "HID connection attempts not documented in README",
        )


class TestTriggerPairingArchitectureDiagram(unittest.TestCase):
    """Verify trigger_pairing_request appears in the architecture diagram."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_architecture_section_exists(self):
        self.assertIn(
            "### Architecture",
            self.readme,
            "Architecture section missing from README",
        )

    def test_trigger_pairing_in_architecture(self):
        """trigger_pairing_request() should be listed in the architecture diagram."""
        self.assertIn(
            "trigger_pairing_request()",
            self.readme,
            "trigger_pairing_request() not in architecture diagram",
        )

    def test_attack_orchestrator_in_architecture(self):
        self.assertIn(
            "attack_orchestrator.py",
            self.readme,
            "attack_orchestrator.py not in architecture diagram",
        )

    def test_trigger_pairing_under_orchestrator(self):
        """trigger_pairing_request should be listed as a sub-component of the orchestrator."""
        # Find the architecture block and confirm trigger_pairing_request is listed under it
        self.assertIn(
            "trigger_pairing_request() (HID connection attempts)",
            self.readme,
            "trigger_pairing_request not listed under orchestrator in architecture",
        )


class TestTriggerPairingComponentFlow(unittest.TestCase):
    """Verify the component flow documents trigger_pairing_request's role."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_component_flow_section_exists(self):
        self.assertIn(
            "**Component flow:**",
            self.readme,
            "Component flow section missing from README",
        )

    def test_trigger_pairing_step_in_flow(self):
        """A step should describe trigger_pairing_request initiating outbound HID connections."""
        self.assertIn(
            "`trigger_pairing_request()`",
            self.readme,
            "trigger_pairing_request() step missing from component flow",
        )

    def test_pairing_popups_in_flow(self):
        """Component flow should mention forcing pairing pop-ups on targets."""
        self.assertIn(
            "pairing pop-ups",
            self.readme,
            "Pairing pop-ups not mentioned in component flow",
        )

    def test_just_works_ssp_in_flow(self):
        """Component flow should mention Just Works SSP pairing method."""
        self.assertIn(
            "Just Works",
            self.readme,
            "Just Works SSP not mentioned in component flow",
        )


class TestTriggerPairingAsyncOrchestration(unittest.TestCase):
    """Verify trigger_pairing_request integration in async orchestration."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_async_orchestration_section_exists(self):
        self.assertIn(
            "Async Orchestration Loop",
            self.readme,
            "Async orchestration section missing from README",
        )

    def test_send_pairing_request_in_orchestration(self):
        """Async orchestration should reference send_pairing_request in the gather call."""
        self.assertIn(
            "send_pairing_request",
            self.readme,
            "send_pairing_request not documented in async orchestration",
        )

    def test_asyncio_gather_documented(self):
        """Async orchestration should document asyncio.gather for concurrent pairing."""
        self.assertIn(
            "asyncio.gather",
            self.readme,
            "asyncio.gather concurrency model not documented",
        )


class TestTriggerPairingDependencies(unittest.TestCase):
    """Verify backend dependencies for trigger_pairing_request are documented."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_dependencies_section_exists(self):
        self.assertIn(
            "### Dependencies (Backend)",
            self.readme,
            "Dependencies (Backend) section missing from README",
        )

    def test_dbus_python_dependency_listed(self):
        self.assertIn(
            "dbus-python",
            self.readme,
            "dbus-python dependency not documented (required for trigger_pairing_request)",
        )

    def test_dbus_bluez_interface_documented(self):
        """D-Bus interface to BlueZ should be documented as dependency purpose."""
        self.assertIn(
            "D-Bus interface to BlueZ",
            self.readme,
            "D-Bus interface to BlueZ purpose not documented",
        )

    def test_bluez_dependency_listed(self):
        self.assertIn(
            "bluez",
            self.readme,
            "bluez dependency not documented",
        )


class TestTriggerPairingSourceDocstrings(unittest.TestCase):
    """Verify that bluetooth_manager.py has proper docstrings for trigger_pairing_request."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_module_docstring_present(self):
        """Module should have a docstring."""
        self.assertIn(
            "Bluetooth Manager",
            self.source,
            "Module docstring should mention Bluetooth Manager",
        )

    def test_module_documents_pairing(self):
        """Module docstring should mention pairing operations."""
        self.assertIn(
            "pairing operations",
            self.source,
            "Module docstring should mention pairing operations",
        )

    def test_trigger_pairing_request_docstring_present(self):
        """trigger_pairing_request should have a docstring."""
        self.assertIn(
            "Initiate an outbound HID pairing request",
            self.source,
            "trigger_pairing_request docstring missing",
        )

    def test_docstring_documents_address_param(self):
        """Docstring should document the address parameter."""
        self.assertIn(
            "address: Target device Bluetooth address",
            self.source,
            "address parameter not documented in trigger_pairing_request",
        )

    def test_docstring_documents_set_trusted_param(self):
        """Docstring should document the set_trusted parameter."""
        self.assertIn(
            "set_trusted: Whether to mark the device as trusted",
            self.source,
            "set_trusted parameter not documented in trigger_pairing_request",
        )

    def test_docstring_documents_return_dict_keys(self):
        """Docstring should document the return dict keys."""
        for key in ["address", "device_path", "status", "paired", "connected", "error"]:
            self.assertIn(
                key,
                self.source,
                f"Return dict key '{key}' not documented in trigger_pairing_request",
            )

    def test_docstring_documents_status_values(self):
        """Docstring should document the possible status values."""
        for status in ["connected", "paired", "failed"]:
            self.assertIn(
                f'"{status}"',
                self.source,
                f"Status value '{status}' not documented in trigger_pairing_request",
            )

    def test_docstring_documents_steps(self):
        """Docstring should describe the steps performed."""
        self.assertIn(
            "Steps performed:",
            self.source,
            "Steps performed section missing from trigger_pairing_request docstring",
        )

    def test_docstring_documents_pair_call(self):
        """Docstring should mention calling Pair() on the device."""
        self.assertIn(
            "Call Pair() to initiate the pairing handshake",
            self.source,
            "Pair() step not documented in trigger_pairing_request docstring",
        )

    def test_docstring_documents_connect_profile(self):
        """Docstring should mention ConnectProfile with HID UUID."""
        self.assertIn(
            "ConnectProfile()",
            self.source,
            "ConnectProfile() step not documented in trigger_pairing_request docstring",
        )

    def test_docstring_documents_trusted_bypass(self):
        """Docstring should explain that trusted bypasses local auth dialog."""
        self.assertIn(
            "bypasses our local auth dialog",
            self.source,
            "Trusted bypass explanation missing from trigger_pairing_request docstring",
        )


class TestTriggerPairingRequestsDocstrings(unittest.TestCase):
    """Verify trigger_pairing_requests (batch) has proper docstrings."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_batch_method_docstring(self):
        """trigger_pairing_requests should have a docstring."""
        self.assertIn(
            "Send pairing requests to multiple discovered peer addresses",
            self.source,
            "trigger_pairing_requests batch method docstring missing",
        )

    def test_batch_documents_addresses_param(self):
        """Docstring should document the addresses list parameter."""
        self.assertIn(
            "addresses: List of target Bluetooth addresses",
            self.source,
            "addresses parameter not documented in trigger_pairing_requests",
        )

    def test_batch_documents_error_isolation(self):
        """Docstring should mention errors on one address don't stop others."""
        self.assertIn(
            "Errors on one address do not prevent attempts to remaining addresses",
            self.source,
            "Error isolation not documented in trigger_pairing_requests",
        )


class TestCancelPairingDocstrings(unittest.TestCase):
    """Verify cancel_pairing has proper docstrings."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_cancel_pairing_docstring(self):
        """cancel_pairing should have a docstring."""
        self.assertIn(
            "Cancel an in-progress pairing attempt",
            self.source,
            "cancel_pairing docstring missing",
        )

    def test_cancel_returns_bool_documented(self):
        """Docstring should document boolean return value."""
        self.assertIn(
            "True if cancellation succeeded, False otherwise",
            self.source,
            "cancel_pairing return value not documented",
        )


if __name__ == "__main__":
    unittest.main()
