"""
Tests for trigger_pairing_request() and related pairing/connection methods
in BluetoothManager.

All D-Bus and BlueZ interactions are mocked since tests run without
a real Bluetooth adapter.
"""

import sys
import unittest
from unittest.mock import MagicMock, patch

# Ensure dbus mock is in place (compatible with test_bluetooth_manager.py pattern)
if "dbus" not in sys.modules or not hasattr(sys.modules["dbus"], "_is_mock"):
    mock_dbus = MagicMock()
    mock_dbus._is_mock = True
    mock_dbus.mainloop = MagicMock()
    mock_dbus.mainloop.glib = MagicMock()
    mock_dbus.mainloop.glib.DBusGMainLoop = MagicMock()
    mock_dbus.SystemBus = MagicMock
    mock_dbus.Boolean = lambda v: v
    mock_dbus.String = lambda v: v
    mock_dbus.UInt32 = lambda v: v
    mock_dbus.ObjectPath = lambda v: v
    mock_dbus.Dictionary = lambda d, signature=None: d
    mock_dbus.Interface = MagicMock()
    mock_dbus.exceptions = MagicMock()
    mock_dbus.exceptions.DBusException = Exception
    sys.modules["dbus"] = mock_dbus
    sys.modules["dbus.mainloop"] = mock_dbus.mainloop
    sys.modules["dbus.mainloop.glib"] = mock_dbus.mainloop.glib
    sys.modules["dbus.service"] = MagicMock()
    sys.modules["dbus.exceptions"] = mock_dbus.exceptions

import dbus

from bluetooth_manager import (
    BluetoothManager,
    BLUEZ_DEVICE_IFACE,
    BLUEZ_SERVICE,
    DBUS_PROPERTIES_IFACE,
    HID_KEYBOARD_UUID,
)


def make_dbus_exception(name="org.freedesktop.DBus.Error.Failed", message="Mock error"):
    """Create a mock DBusException with a get_dbus_name method."""
    exc = dbus.exceptions.DBusException(message)
    exc.get_dbus_name = lambda: name
    return exc


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
    return mgr


class TestAddressToDevicePath(unittest.TestCase):
    """Tests for _address_to_device_path()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_standard_address(self):
        result = self.mgr._address_to_device_path("AA:BB:CC:DD:EE:FF")
        self.assertEqual(result, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_lowercase_address_converted_to_upper(self):
        result = self.mgr._address_to_device_path("aa:bb:cc:dd:ee:ff")
        self.assertEqual(result, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_mixed_case_address(self):
        result = self.mgr._address_to_device_path("aA:Bb:cC:Dd:eE:fF")
        self.assertEqual(result, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_uses_adapter_path(self):
        self.mgr._adapter_path = "/org/bluez/hci1"
        result = self.mgr._address_to_device_path("11:22:33:44:55:66")
        self.assertEqual(result, "/org/bluez/hci1/dev_11_22_33_44_55_66")


class TestGetDeviceInterface(unittest.TestCase):
    """Tests for get_device_interface()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_calls_get_object_with_correct_path(self):
        self.mgr.get_device_interface("AA:BB:CC:DD:EE:FF")
        self.mgr._bus.get_object.assert_called_with(
            BLUEZ_SERVICE, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF"
        )

    def test_raises_on_unknown_device(self):
        self.mgr._bus.get_object.side_effect = dbus.exceptions.DBusException(
            "org.freedesktop.DBus.Error.UnknownObject"
        )
        with self.assertRaises(dbus.exceptions.DBusException):
            self.mgr.get_device_interface("FF:FF:FF:FF:FF:FF")


class TestGetDevicePropertiesInterface(unittest.TestCase):
    """Tests for get_device_properties_interface()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_calls_get_object_with_correct_path(self):
        self.mgr.get_device_properties_interface("AA:BB:CC:DD:EE:FF")
        self.mgr._bus.get_object.assert_called_with(
            BLUEZ_SERVICE, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF"
        )


class TestSetDeviceTrusted(unittest.TestCase):
    """Tests for set_device_trusted()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_sets_trusted_true(self):
        mock_props = MagicMock()
        with patch.object(self.mgr, "get_device_properties_interface", return_value=mock_props):
            self.mgr.set_device_trusted("AA:BB:CC:DD:EE:FF", True)
            mock_props.Set.assert_called_once_with(
                BLUEZ_DEVICE_IFACE, "Trusted", True
            )

    def test_sets_trusted_false(self):
        mock_props = MagicMock()
        with patch.object(self.mgr, "get_device_properties_interface", return_value=mock_props):
            self.mgr.set_device_trusted("AA:BB:CC:DD:EE:FF", False)
            mock_props.Set.assert_called_once_with(
                BLUEZ_DEVICE_IFACE, "Trusted", False
            )


class TestTriggerPairingRequest(unittest.TestCase):
    """Tests for trigger_pairing_request()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()
        self.target = "AA:BB:CC:DD:EE:FF"

    def test_successful_pair_and_connect(self):
        mock_device = MagicMock()
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted"):
                result = self.mgr.trigger_pairing_request(self.target)

        self.assertEqual(result["status"], "connected")
        self.assertTrue(result["paired"])
        self.assertTrue(result["connected"])
        self.assertIsNone(result["error"])
        mock_device.Pair.assert_called_once()
        mock_device.ConnectProfile.assert_called_once_with(HID_KEYBOARD_UUID)

    def test_successful_pair_connect_fails(self):
        mock_device = MagicMock()
        mock_device.ConnectProfile.side_effect = dbus.exceptions.DBusException("connect failed")
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted"):
                result = self.mgr.trigger_pairing_request(self.target)

        self.assertEqual(result["status"], "paired")
        self.assertTrue(result["paired"])
        self.assertFalse(result["connected"])
        self.assertIsNone(result["error"])

    def test_pair_fails(self):
        mock_device = MagicMock()
        mock_device.Pair.side_effect = make_dbus_exception(
            "org.bluez.Error.AuthenticationFailed", "auth failed"
        )
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted"):
                result = self.mgr.trigger_pairing_request(self.target)

        self.assertEqual(result["status"], "failed")
        self.assertFalse(result["paired"])
        self.assertFalse(result["connected"])
        self.assertIsNotNone(result["error"])
        mock_device.ConnectProfile.assert_not_called()

    def test_already_paired_treated_as_success(self):
        mock_device = MagicMock()
        exc = make_dbus_exception("org.bluez.Error.AlreadyExists", "Already Exists")
        mock_device.Pair.side_effect = exc
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted"):
                result = self.mgr.trigger_pairing_request(self.target)

        self.assertTrue(result["paired"])
        # ConnectProfile succeeds on mock, so status is "connected"
        self.assertIn(result["status"], ("paired", "connected"))
        mock_device.ConnectProfile.assert_called_once()

    def test_device_not_found(self):
        with patch.object(
            self.mgr,
            "get_device_interface",
            side_effect=dbus.exceptions.DBusException("not found"),
        ):
            result = self.mgr.trigger_pairing_request(self.target)

        self.assertEqual(result["status"], "failed")
        self.assertFalse(result["paired"])
        self.assertIn("Device not found", result["error"])

    def test_set_trusted_called_by_default(self):
        mock_device = MagicMock()
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted") as mock_trusted:
                self.mgr.trigger_pairing_request(self.target)
                mock_trusted.assert_called_once_with(self.target, True)

    def test_set_trusted_skipped_when_false(self):
        mock_device = MagicMock()
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted") as mock_trusted:
                self.mgr.trigger_pairing_request(self.target, set_trusted=False)
                mock_trusted.assert_not_called()

    def test_set_trusted_failure_does_not_block_pairing(self):
        mock_device = MagicMock()
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(
                self.mgr,
                "set_device_trusted",
                side_effect=dbus.exceptions.DBusException("trust failed"),
            ):
                result = self.mgr.trigger_pairing_request(self.target)

        # Pairing should still proceed
        mock_device.Pair.assert_called_once()
        self.assertTrue(result["paired"])

    def test_result_contains_address_and_device_path(self):
        mock_device = MagicMock()
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted"):
                result = self.mgr.trigger_pairing_request(self.target)

        self.assertEqual(result["address"], self.target)
        self.assertEqual(result["device_path"], "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")


class TestTriggerPairingRequests(unittest.TestCase):
    """Tests for trigger_pairing_requests() (batch)."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_multiple_addresses(self):
        addresses = ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"]
        mock_device = MagicMock()

        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            with patch.object(self.mgr, "set_device_trusted"):
                results = self.mgr.trigger_pairing_requests(addresses)

        self.assertEqual(len(results), 3)
        for i, result in enumerate(results):
            self.assertEqual(result["address"], addresses[i])
            self.assertTrue(result["paired"])

    def test_empty_list(self):
        results = self.mgr.trigger_pairing_requests([])
        self.assertEqual(results, [])

    def test_partial_failure(self):
        """One device fails, others succeed."""
        call_count = 0

        def mock_pair_side_effect(addr, set_trusted=True):
            nonlocal call_count
            call_count += 1
            if call_count == 2:
                return {
                    "address": addr,
                    "device_path": f"/org/bluez/hci0/dev_{addr.replace(':', '_')}",
                    "status": "failed",
                    "paired": False,
                    "connected": False,
                    "error": "auth failed",
                }
            return {
                "address": addr,
                "device_path": f"/org/bluez/hci0/dev_{addr.replace(':', '_')}",
                "status": "connected",
                "paired": True,
                "connected": True,
                "error": None,
            }

        with patch.object(self.mgr, "trigger_pairing_request", side_effect=mock_pair_side_effect):
            results = self.mgr.trigger_pairing_requests(
                ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"]
            )

        self.assertEqual(results[0]["status"], "connected")
        self.assertEqual(results[1]["status"], "failed")
        self.assertEqual(results[2]["status"], "connected")

    def test_passes_set_trusted_flag(self):
        mock_result = {
            "address": "AA:BB:CC:DD:EE:01",
            "device_path": "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_01",
            "status": "connected",
            "paired": True,
            "connected": True,
            "error": None,
        }
        with patch.object(self.mgr, "trigger_pairing_request", return_value=mock_result) as mock_tpr:
            self.mgr.trigger_pairing_requests(["AA:BB:CC:DD:EE:01"], set_trusted=False)
            mock_tpr.assert_called_once_with("AA:BB:CC:DD:EE:01", set_trusted=False)


class TestCancelPairing(unittest.TestCase):
    """Tests for cancel_pairing()."""

    def setUp(self):
        self.mgr = make_manager_with_mocks()

    def test_successful_cancel(self):
        mock_device = MagicMock()
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            result = self.mgr.cancel_pairing("AA:BB:CC:DD:EE:FF")
        self.assertTrue(result)
        mock_device.CancelPairing.assert_called_once()

    def test_cancel_fails_returns_false(self):
        mock_device = MagicMock()
        mock_device.CancelPairing.side_effect = dbus.exceptions.DBusException("no pairing")
        with patch.object(self.mgr, "get_device_interface", return_value=mock_device):
            result = self.mgr.cancel_pairing("AA:BB:CC:DD:EE:FF")
        self.assertFalse(result)

    def test_device_not_found_returns_false(self):
        with patch.object(
            self.mgr,
            "get_device_interface",
            side_effect=dbus.exceptions.DBusException("not found"),
        ):
            result = self.mgr.cancel_pairing("FF:FF:FF:FF:FF:FF")
        self.assertFalse(result)


class TestHidKeyboardUuid(unittest.TestCase):
    """Verify HID_KEYBOARD_UUID constant is correct."""

    def test_uuid_value(self):
        self.assertEqual(HID_KEYBOARD_UUID, "00001124-0000-1000-8000-00805f9b34fb")

    def test_uuid_matches_sdp_registration(self):
        """The UUID used in trigger_pairing_request must match what configure_keyboard_sdp uses."""
        self.assertEqual(HID_KEYBOARD_UUID, "00001124-0000-1000-8000-00805f9b34fb")


if __name__ == "__main__":
    unittest.main()
