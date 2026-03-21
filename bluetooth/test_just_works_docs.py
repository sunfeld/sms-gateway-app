"""
Unit tests for Just Works pairing security parameters documentation.

Validates that the README correctly documents the JustWorksAgent, its
configuration flow, SSP constants, and that the source code has proper
docstrings for all Just Works-related components.
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


class TestJustWorksDocSectionExists(unittest.TestCase):
    """Verify README.md has a dedicated Just Works pairing section."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_readme_file_exists(self):
        readme_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "README.md",
        )
        self.assertTrue(os.path.exists(readme_path), "README.md not found")

    def test_just_works_section_exists(self):
        self.assertIn(
            "## Just Works Pairing Security Parameters",
            self.readme,
            "Just Works Pairing Security Parameters section missing from README",
        )

    def test_ssp_mentioned(self):
        """README should explain SSP (Secure Simple Pairing)."""
        self.assertIn(
            "Secure Simple Pairing",
            self.readme,
            "Secure Simple Pairing not mentioned in README",
        )

    def test_no_input_no_output_documented(self):
        """The NoInputNoOutput IO capability should be documented."""
        self.assertIn(
            "NoInputNoOutput",
            self.readme,
            "NoInputNoOutput IO capability not documented in README",
        )


class TestJustWorksAgentDocumented(unittest.TestCase):
    """Verify the JustWorksAgent D-Bus methods are documented in the README."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_just_works_agent_section_exists(self):
        self.assertIn(
            "### JustWorksAgent",
            self.readme,
            "JustWorksAgent section missing from README",
        )

    def test_request_pin_code_documented(self):
        self.assertIn(
            "RequestPinCode",
            self.readme,
            "RequestPinCode method not documented in README",
        )

    def test_request_passkey_documented(self):
        self.assertIn(
            "RequestPasskey",
            self.readme,
            "RequestPasskey method not documented in README",
        )

    def test_request_confirmation_documented(self):
        self.assertIn(
            "RequestConfirmation",
            self.readme,
            "RequestConfirmation method not documented in README",
        )

    def test_request_authorization_documented(self):
        self.assertIn(
            "RequestAuthorization",
            self.readme,
            "RequestAuthorization method not documented in README",
        )

    def test_authorize_service_documented(self):
        self.assertIn(
            "AuthorizeService",
            self.readme,
            "AuthorizeService method not documented in README",
        )

    def test_auto_accept_behavior_documented(self):
        """README should explain auto-accept behavior."""
        self.assertIn(
            "Auto-accept",
            self.readme,
            "Auto-accept behavior not documented in README",
        )


class TestJustWorksConfigurationFlow(unittest.TestCase):
    """Verify the configuration flow is documented in the README."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_configuration_flow_section_exists(self):
        self.assertIn(
            "### Configuration Flow",
            self.readme,
            "Configuration Flow section missing from README",
        )

    def test_configure_just_works_pairing_in_flow(self):
        self.assertIn(
            "configure_just_works_pairing()",
            self.readme,
            "configure_just_works_pairing() not in configuration flow",
        )

    def test_ensure_ready_in_flow(self):
        self.assertIn(
            "ensure_ready()",
            self.readme,
            "ensure_ready() not documented in configuration flow",
        )

    def test_register_agent_in_flow(self):
        self.assertIn(
            "RegisterAgent",
            self.readme,
            "RegisterAgent call not documented in configuration flow",
        )

    def test_request_default_agent_in_flow(self):
        self.assertIn(
            "RequestDefaultAgent",
            self.readme,
            "RequestDefaultAgent call not documented in configuration flow",
        )


class TestJustWorksConstantsDocumented(unittest.TestCase):
    """Verify Just Works SSP constants are documented in the README."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_constants_section_exists(self):
        self.assertIn(
            "### Constants",
            self.readme,
            "Constants section missing from Just Works documentation",
        )

    def test_agent_capability_constant_documented(self):
        self.assertIn(
            "AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT",
            self.readme,
            "AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT constant not documented",
        )

    def test_agent_path_constant_documented(self):
        self.assertIn(
            "AGENT_PATH_JUST_WORKS",
            self.readme,
            "AGENT_PATH_JUST_WORKS constant not documented",
        )

    def test_default_passkey_constant_documented(self):
        self.assertIn(
            "JUST_WORKS_DEFAULT_PASSKEY",
            self.readme,
            "JUST_WORKS_DEFAULT_PASSKEY constant not documented",
        )

    def test_agent_path_value_documented(self):
        self.assertIn(
            "/org/bluez/agent_just_works",
            self.readme,
            "Agent path value not documented in README",
        )


class TestJustWorksArchitectureIntegration(unittest.TestCase):
    """Verify Just Works is referenced in the architecture/component flow."""

    @classmethod
    def setUpClass(cls):
        cls.readme = _load_readme()

    def test_just_works_in_component_flow(self):
        """Component flow should reference Just Works SSP."""
        self.assertIn(
            '"Just Works" SSP',
            self.readme,
            "Just Works SSP not referenced in component flow",
        )

    def test_pairing_popups_mentioned(self):
        self.assertIn(
            "pairing pop-ups",
            self.readme,
            "Pairing pop-ups not mentioned in README",
        )

    def test_agent_manager_interface_documented(self):
        self.assertIn(
            "AgentManager1",
            self.readme,
            "AgentManager1 D-Bus interface not documented",
        )


class TestJustWorksSourceDocstrings(unittest.TestCase):
    """Verify bluetooth_manager.py has proper docstrings for Just Works components."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_just_works_agent_class_docstring(self):
        self.assertIn(
            "Just Works",
            self.source,
            "JustWorksAgent class should mention Just Works in docstring",
        )

    def test_agent_documents_no_input_no_output(self):
        self.assertIn(
            "NoInputNoOutput IO capability",
            self.source,
            "JustWorksAgent should document NoInputNoOutput IO capability",
        )

    def test_agent_documents_auto_accept(self):
        self.assertIn(
            "Auto-accepts all pairing confirmations",
            self.source,
            "JustWorksAgent should document auto-accept behavior",
        )

    def test_register_method_docstring(self):
        self.assertIn(
            "Register a \"Just Works\" pairing agent",
            self.source,
            "register_just_works_agent docstring missing",
        )

    def test_unregister_method_docstring(self):
        self.assertIn(
            "Unregister the Just Works pairing agent",
            self.source,
            "unregister_just_works_agent docstring missing",
        )

    def test_configure_method_docstring(self):
        self.assertIn(
            "Full setup for Just Works pairing",
            self.source,
            "configure_just_works_pairing docstring missing",
        )

    def test_configure_documents_return_dict(self):
        self.assertIn(
            "Dict with configuration status details",
            self.source,
            "configure_just_works_pairing return value not documented",
        )

    def test_ssp_constants_documented_in_source(self):
        """Source should have comments explaining SSP constants."""
        self.assertIn(
            "Just Works SSP (Secure Simple Pairing) Constants",
            self.source,
            "SSP constants section comment missing from source",
        )

    def test_io_capability_comment(self):
        """Source should explain the IO capability choice."""
        self.assertIn(
            "NoInputNoOutput",
            self.source,
            "NoInputNoOutput capability not documented in source comments",
        )

    def test_agent_interface_constant(self):
        """JustWorksAgent should define the AGENT_INTERFACE."""
        self.assertIn(
            'AGENT_INTERFACE = "org.bluez.Agent1"',
            self.source,
            "AGENT_INTERFACE constant missing from JustWorksAgent",
        )


class TestJustWorksRequestConfirmationDocstring(unittest.TestCase):
    """Verify RequestConfirmation auto-accept is properly documented in source."""

    @classmethod
    def setUpClass(cls):
        cls.source = _load_source()

    def test_request_confirmation_docstring(self):
        self.assertIn(
            "Auto-accept numeric confirmation",
            self.source,
            "RequestConfirmation docstring should describe auto-accept",
        )

    def test_request_authorization_docstring(self):
        self.assertIn(
            "Auto-authorize the device",
            self.source,
            "RequestAuthorization docstring missing",
        )

    def test_authorize_service_docstring(self):
        self.assertIn(
            "Auto-authorize service access",
            self.source,
            "AuthorizeService docstring missing",
        )

    def test_request_passkey_docstring(self):
        self.assertIn(
            "Return zero passkey",
            self.source,
            "RequestPasskey docstring should mention zero passkey",
        )

    def test_request_pin_code_docstring(self):
        self.assertIn(
            "Return an empty PIN code",
            self.source,
            "RequestPinCode docstring missing",
        )


if __name__ == "__main__":
    unittest.main()
