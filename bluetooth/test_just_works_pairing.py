"""
Tests for "Just Works" pairing security parameters in BluetoothManager.

Verifies the JustWorksAgent D-Bus service object, its registration/unregistration
with BlueZ AgentManager, and the configure_just_works_pairing() convenience method.

All D-Bus and BlueZ interactions are mocked since tests run without
a real Bluetooth adapter.
"""

import sys
import unittest
from unittest.mock import MagicMock, patch, call

# Ensure dbus mock is in place (compatible with existing test patterns)
if "dbus" not in sys.modules or not hasattr(sys.modules["dbus"], "_is_mock"):
    mock_dbus = MagicMock()
    mock_dbus._is_mock = True
    mock_dbus.mainloop = MagicMock()
    mock_dbus.mainloop.glib = MagicMock()
    mock_dbus.mainloop.glib.DBusGMainLoop = MagicMock()
    mock_dbus.SystemBus = MagicMock
    mock_dbus.Boolean = lambda v: v
    mock_dbus.String = lambda v: v
    mock_dbus.UInt32 = lambda v: int(v)
    mock_dbus.ObjectPath = lambda v: v
    mock_dbus.Dictionary = lambda d, signature=None: d
    mock_dbus.Interface = MagicMock()
    mock_dbus.exceptions = MagicMock()
    mock_dbus.exceptions.DBusException = Exception

    # Mock dbus.service for JustWorksAgent (it inherits dbus.service.Object)
    mock_service = MagicMock()
    mock_service.Object = object  # Base class — tests won't use real D-Bus
    mock_service.method = lambda iface, **kw: lambda fn: fn  # No-op decorator
    mock_dbus.service = mock_service

    sys.modules["dbus"] = mock_dbus
    sys.modules["dbus.mainloop"] = mock_dbus.mainloop
    sys.modules["dbus.mainloop.glib"] = mock_dbus.mainloop.glib
    sys.modules["dbus.service"] = mock_service
    sys.modules["dbus.exceptions"] = mock_dbus.exceptions

import dbus

from bluetooth_manager import (
    BluetoothManager,
    JustWorksAgent,
    AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT,
    AGENT_PATH_JUST_WORKS,
    JUST_WORKS_DEFAULT_PASSKEY,
    BLUEZ_AGENT_MANAGER_IFACE,
    BLUEZ_SERVICE,
)


def make_manager_with_mocks():
    """Create a BluetoothManager with fully mocked D-Bus."""
    mock_bus = MagicMock()
    mock_adapter_obj = MagicMock()
    mock_bus.get_object.return_value = mock_adapter_obj

    mgr = BluetoothManager.__new__(BluetoothManager)
    mgr._adapter_name = "hci0"
    mgr._adapter_path = "/org/bluez/hci0"
    mgr._bus = mock_bus
    mgr._adapter = MagicMock()
    mgr._adapter_props = MagicMock()
    mgr._profile_manager = None
    mgr._just_works_agent = None
    return mgr


# ── Constants Tests ────────────────────────────────────────────────────


class TestJustWorksConstants(unittest.TestCase):
    """Verify Just Works SSP constants are correct."""

    def test_capability_string(self):
        self.assertEqual(AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT, "NoInputNoOutput")

    def test_agent_path(self):
        self.assertEqual(AGENT_PATH_JUST_WORKS, "/org/bluez/agent_just_works")
        # Must be a valid D-Bus object path
        self.assertTrue(AGENT_PATH_JUST_WORKS.startswith("/"))

    def test_default_passkey_is_zero(self):
        self.assertEqual(JUST_WORKS_DEFAULT_PASSKEY, 0)


# ── JustWorksAgent Tests ──────────────────────────────────────────────


class TestJustWorksAgent(unittest.TestCase):
    """Tests for the JustWorksAgent D-Bus service object."""

    def _make_agent(self, path=AGENT_PATH_JUST_WORKS):
        """Create a JustWorksAgent with mocked bus."""
        agent = JustWorksAgent.__new__(JustWorksAgent)
        agent._path = path
        return agent

    def test_path_property(self):
        agent = self._make_agent()
        self.assertEqual(agent.path, AGENT_PATH_JUST_WORKS)

    def test_custom_path(self):
        agent = self._make_agent("/test/custom_agent")
        self.assertEqual(agent.path, "/test/custom_agent")

    def test_release_does_not_raise(self):
        agent = self._make_agent()
        agent.Release()  # Should not raise

    def test_request_pin_code_returns_empty(self):
        agent = self._make_agent()
        result = agent.RequestPinCode("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")
        self.assertEqual(result, "")

    def test_display_pin_code_does_not_raise(self):
        agent = self._make_agent()
        agent.DisplayPinCode("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF", "123456")

    def test_request_passkey_returns_zero(self):
        agent = self._make_agent()
        result = agent.RequestPasskey("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")
        self.assertEqual(result, JUST_WORKS_DEFAULT_PASSKEY)

    def test_display_passkey_does_not_raise(self):
        agent = self._make_agent()
        agent.DisplayPasskey("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF", 123456, 0)

    def test_request_confirmation_auto_accepts(self):
        """RequestConfirmation should return without raising (auto-accept)."""
        agent = self._make_agent()
        # If it raises, the test fails — auto-accept means silent return
        agent.RequestConfirmation("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF", 123456)

    def test_request_authorization_auto_accepts(self):
        agent = self._make_agent()
        agent.RequestAuthorization("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_authorize_service_auto_accepts(self):
        agent = self._make_agent()
        agent.AuthorizeService(
            "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
            "00001124-0000-1000-8000-00805f9b34fb",
        )

    def test_cancel_does_not_raise(self):
        agent = self._make_agent()
        agent.Cancel()


# ── BluetoothManager Registration Tests ──────────────────────────────


class TestRegisterJustWorksAgent(unittest.TestCase):
    """Tests for BluetoothManager.register_just_works_agent()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_registers_agent_with_agent_manager(self):
        mock_agent_mgr = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                mock_agent_instance = MagicMock()
                mock_agent_instance.path = AGENT_PATH_JUST_WORKS
                MockAgent.return_value = mock_agent_instance
                self.mgr.register_just_works_agent()

        mock_agent_mgr.RegisterAgent.assert_called_once_with(
            AGENT_PATH_JUST_WORKS,
            AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT,
        )

    def test_sets_default_agent(self):
        mock_agent_mgr = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                mock_agent_instance = MagicMock()
                mock_agent_instance.path = AGENT_PATH_JUST_WORKS
                MockAgent.return_value = mock_agent_instance
                self.mgr.register_just_works_agent()

        mock_agent_mgr.RequestDefaultAgent.assert_called_once_with(AGENT_PATH_JUST_WORKS)

    def test_returns_agent_instance(self):
        mock_agent_mgr = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                mock_agent_instance = MagicMock()
                mock_agent_instance.path = AGENT_PATH_JUST_WORKS
                MockAgent.return_value = mock_agent_instance
                result = self.mgr.register_just_works_agent()

        self.assertEqual(result, mock_agent_instance)

    def test_stores_agent_reference(self):
        mock_agent_mgr = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                mock_agent_instance = MagicMock()
                mock_agent_instance.path = AGENT_PATH_JUST_WORKS
                MockAgent.return_value = mock_agent_instance
                self.mgr.register_just_works_agent()

        self.assertEqual(self.mgr._just_works_agent, mock_agent_instance)
        self.assertTrue(self.mgr.just_works_enabled)

    def test_custom_agent_path(self):
        mock_agent_mgr = MagicMock()
        custom_path = "/org/bluez/custom_agent"
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                mock_agent_instance = MagicMock()
                mock_agent_instance.path = custom_path
                MockAgent.return_value = mock_agent_instance
                self.mgr.register_just_works_agent(agent_path=custom_path)

        MockAgent.assert_called_once_with(self.mgr._bus, custom_path)
        mock_agent_mgr.RegisterAgent.assert_called_once_with(
            custom_path, AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT,
        )

    def test_unregisters_existing_agent_before_registering_new(self):
        """If an agent is already registered, it should be unregistered first."""
        self.mgr._just_works_agent = MagicMock()
        mock_agent_mgr = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch.object(self.mgr, "unregister_just_works_agent") as mock_unreg:
                with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                    mock_agent_instance = MagicMock()
                    mock_agent_instance.path = AGENT_PATH_JUST_WORKS
                    MockAgent.return_value = mock_agent_instance
                    self.mgr.register_just_works_agent()

        mock_unreg.assert_called_once()

    def test_raises_on_dbus_failure(self):
        mock_agent_mgr = MagicMock()
        mock_agent_mgr.RegisterAgent.side_effect = dbus.exceptions.DBusException("refused")
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            with patch("bluetooth_manager.JustWorksAgent") as MockAgent:
                mock_agent_instance = MagicMock()
                mock_agent_instance.path = AGENT_PATH_JUST_WORKS
                MockAgent.return_value = mock_agent_instance
                with self.assertRaises(dbus.exceptions.DBusException):
                    self.mgr.register_just_works_agent()


class TestUnregisterJustWorksAgent(unittest.TestCase):
    """Tests for BluetoothManager.unregister_just_works_agent()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_calls_unregister_on_agent_manager(self):
        mock_agent_mgr = MagicMock()
        self.mgr._just_works_agent = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            self.mgr.unregister_just_works_agent()

        mock_agent_mgr.UnregisterAgent.assert_called_once_with(AGENT_PATH_JUST_WORKS)

    def test_clears_agent_reference(self):
        mock_agent_mgr = MagicMock()
        self.mgr._just_works_agent = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            self.mgr.unregister_just_works_agent()

        self.assertIsNone(self.mgr._just_works_agent)
        self.assertFalse(self.mgr.just_works_enabled)

    def test_handles_dbus_failure_gracefully(self):
        """Unregister should not raise even if D-Bus call fails."""
        mock_agent_mgr = MagicMock()
        mock_agent_mgr.UnregisterAgent.side_effect = dbus.exceptions.DBusException("not found")
        self.mgr._just_works_agent = MagicMock()
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            self.mgr.unregister_just_works_agent()  # Should not raise

        self.assertIsNone(self.mgr._just_works_agent)

    def test_custom_path(self):
        mock_agent_mgr = MagicMock()
        self.mgr._just_works_agent = MagicMock()
        custom_path = "/org/bluez/custom_agent"
        with patch.object(self.mgr, "get_agent_manager", return_value=mock_agent_mgr):
            self.mgr.unregister_just_works_agent(agent_path=custom_path)

        mock_agent_mgr.UnregisterAgent.assert_called_once_with(custom_path)


class TestJustWorksProperties(unittest.TestCase):
    """Tests for just_works_agent and just_works_enabled properties."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_agent_none_by_default(self):
        self.assertIsNone(self.mgr.just_works_agent)

    def test_not_enabled_by_default(self):
        self.assertFalse(self.mgr.just_works_enabled)

    def test_enabled_when_agent_set(self):
        self.mgr._just_works_agent = MagicMock()
        self.assertTrue(self.mgr.just_works_enabled)

    def test_agent_property_returns_instance(self):
        mock_agent = MagicMock()
        self.mgr._just_works_agent = mock_agent
        self.assertEqual(self.mgr.just_works_agent, mock_agent)


class TestConfigureJustWorksPairing(unittest.TestCase):
    """Tests for configure_just_works_pairing() convenience method."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_calls_ensure_ready(self):
        mock_agent = MagicMock()
        mock_agent.path = AGENT_PATH_JUST_WORKS
        with patch.object(self.mgr, "ensure_ready") as mock_ready:
            with patch.object(self.mgr, "register_just_works_agent", return_value=mock_agent):
                self.mgr.configure_just_works_pairing()

        mock_ready.assert_called_once()

    def test_registers_agent(self):
        mock_agent = MagicMock()
        mock_agent.path = AGENT_PATH_JUST_WORKS
        with patch.object(self.mgr, "ensure_ready"):
            with patch.object(self.mgr, "register_just_works_agent", return_value=mock_agent) as mock_reg:
                self.mgr.configure_just_works_pairing()

        mock_reg.assert_called_once_with(AGENT_PATH_JUST_WORKS)

    def test_returns_config_dict(self):
        mock_agent = MagicMock()
        mock_agent.path = AGENT_PATH_JUST_WORKS
        with patch.object(self.mgr, "ensure_ready"):
            with patch.object(self.mgr, "register_just_works_agent", return_value=mock_agent):
                result = self.mgr.configure_just_works_pairing()

        self.assertEqual(result["adapter"], "hci0")
        self.assertEqual(result["agent_path"], AGENT_PATH_JUST_WORKS)
        self.assertEqual(result["capability"], AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT)
        self.assertTrue(result["just_works_enabled"])
        self.assertTrue(result["pairable"])
        self.assertTrue(result["discoverable"])

    def test_custom_agent_path(self):
        custom_path = "/org/bluez/custom"
        mock_agent = MagicMock()
        mock_agent.path = custom_path
        with patch.object(self.mgr, "ensure_ready"):
            with patch.object(self.mgr, "register_just_works_agent", return_value=mock_agent) as mock_reg:
                result = self.mgr.configure_just_works_pairing(agent_path=custom_path)

        mock_reg.assert_called_once_with(custom_path)
        self.assertEqual(result["agent_path"], custom_path)


if __name__ == "__main__":
    unittest.main()
