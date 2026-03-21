"""
Unit tests for BluetoothKeyboardProfile — BlueZ Profile1 HID Keyboard.

All D-Bus interactions are mocked so tests run without a real Bluetooth stack.
"""

import unittest
from unittest.mock import patch, MagicMock, PropertyMock, call
import sys

# Mock dbus and dbus.service before importing the module under test
mock_dbus = MagicMock()
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

# Mock dbus.service.Object so the class can inherit from it
mock_service = MagicMock()


class FakeDBusServiceObject:
    """Stand-in for dbus.service.Object that does nothing."""
    def __init__(self, bus=None, object_path=None, *args, **kwargs):
        pass


mock_service.Object = FakeDBusServiceObject


# Mock the dbus.service.method decorator to be a no-op
def fake_method(interface, in_signature="", out_signature=""):
    def decorator(func):
        return func
    return decorator


mock_service.method = fake_method
mock_dbus.service = mock_service

sys.modules["dbus"] = mock_dbus
sys.modules["dbus.mainloop"] = mock_dbus.mainloop
sys.modules["dbus.mainloop.glib"] = mock_dbus.mainloop.glib
sys.modules["dbus.exceptions"] = mock_dbus.exceptions
sys.modules["dbus.service"] = mock_service

from bluetooth_keyboard_profile import (
    BluetoothKeyboardProfile,
    HID_KEYBOARD_UUID,
    HID_KEYBOARD_UUID_SHORT,
    BLUEZ_PROFILE1_IFACE,
    DEFAULT_PROFILE_PATH,
    SDP_RECORD_XML,
)


class TestConstants(unittest.TestCase):
    """Verify module-level constants."""

    def test_hid_keyboard_uuid_full(self):
        self.assertEqual(HID_KEYBOARD_UUID, "00001124-0000-1000-8000-00805f9b34fb")

    def test_hid_keyboard_uuid_short(self):
        self.assertEqual(HID_KEYBOARD_UUID_SHORT, "0x1124")

    def test_profile1_iface(self):
        self.assertEqual(BLUEZ_PROFILE1_IFACE, "org.bluez.Profile1")

    def test_default_profile_path(self):
        self.assertEqual(DEFAULT_PROFILE_PATH, "/org/bluez/hid_keyboard_profile")

    def test_sdp_record_contains_hid_uuid(self):
        self.assertIn("0x1124", SDP_RECORD_XML)

    def test_sdp_record_contains_keyboard_name(self):
        self.assertIn("Bluetooth HID Keyboard", SDP_RECORD_XML)

    def test_sdp_record_is_valid_xml_structure(self):
        self.assertTrue(SDP_RECORD_XML.strip().startswith("<?xml"))
        self.assertTrue(SDP_RECORD_XML.strip().endswith("</record>"))


class TestBluetoothKeyboardProfileInit(unittest.TestCase):
    """Test profile initialization."""

    def test_default_object_path(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus)
        self.assertEqual(profile.object_path, DEFAULT_PROFILE_PATH)

    def test_custom_object_path(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus, "/custom/path")
        self.assertEqual(profile.object_path, "/custom/path")

    def test_not_registered_initially(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus)
        self.assertFalse(profile.is_registered)

    def test_no_active_connections_initially(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus)
        self.assertEqual(profile.active_connections, {})

    def test_uuid_property(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus)
        self.assertEqual(profile.uuid, HID_KEYBOARD_UUID)

    def test_uuid_short_property(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus)
        self.assertEqual(profile.uuid_short, "0x1124")


class TestProfileOptions(unittest.TestCase):
    """Test get_profile_options."""

    def setUp(self):
        self.bus = MagicMock()
        self.profile = BluetoothKeyboardProfile(self.bus)

    def test_options_contains_name(self):
        opts = self.profile.get_profile_options()
        self.assertEqual(opts["Name"], "HID Keyboard")

    def test_options_contains_role_server(self):
        opts = self.profile.get_profile_options()
        self.assertEqual(opts["Role"], "server")

    def test_options_auto_connect_true(self):
        opts = self.profile.get_profile_options()
        self.assertTrue(opts["AutoConnect"])

    def test_options_no_authentication_required(self):
        opts = self.profile.get_profile_options()
        self.assertFalse(opts["RequireAuthentication"])

    def test_options_no_authorization_required(self):
        opts = self.profile.get_profile_options()
        self.assertFalse(opts["RequireAuthorization"])

    def test_options_contains_service_record(self):
        opts = self.profile.get_profile_options()
        self.assertIn("ServiceRecord", opts)
        self.assertIn("0x1124", opts["ServiceRecord"])


class TestProfileRegistration(unittest.TestCase):
    """Test register/unregister methods."""

    def setUp(self):
        self.bus = MagicMock()
        self.profile = BluetoothKeyboardProfile(self.bus)
        self.mock_manager = MagicMock()

    def test_register_calls_bluetooth_manager(self):
        self.profile.register(self.mock_manager)
        self.mock_manager.register_profile.assert_called_once_with(
            DEFAULT_PROFILE_PATH,
            HID_KEYBOARD_UUID,
            self.profile.get_profile_options(),
        )

    def test_register_sets_registered_flag(self):
        self.profile.register(self.mock_manager)
        self.assertTrue(self.profile.is_registered)

    def test_register_idempotent(self):
        self.profile.register(self.mock_manager)
        self.profile.register(self.mock_manager)
        # Should only call register_profile once
        self.mock_manager.register_profile.assert_called_once()

    def test_unregister_calls_bluetooth_manager(self):
        self.profile.register(self.mock_manager)
        self.profile.unregister(self.mock_manager)
        self.mock_manager.unregister_profile.assert_called_once_with(DEFAULT_PROFILE_PATH)

    def test_unregister_clears_registered_flag(self):
        self.profile.register(self.mock_manager)
        self.profile.unregister(self.mock_manager)
        self.assertFalse(self.profile.is_registered)

    def test_unregister_when_not_registered_is_noop(self):
        self.profile.unregister(self.mock_manager)
        self.mock_manager.unregister_profile.assert_not_called()


class TestProfile1Interface(unittest.TestCase):
    """Test Profile1 D-Bus interface methods."""

    def setUp(self):
        self.bus = MagicMock()
        self.profile = BluetoothKeyboardProfile(self.bus)

    def test_release_clears_registered_flag(self):
        self.profile._registered = True
        self.profile.Release()
        self.assertFalse(self.profile.is_registered)

    def test_release_clears_connections(self):
        self.profile._connections = {"/org/bluez/hci0/dev_AA": 5}
        self.profile.Release()
        self.assertEqual(self.profile.active_connections, {})

    def test_new_connection_stores_fd(self):
        mock_fd = MagicMock()
        mock_fd.take.return_value = 42
        device_path = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF"

        self.profile.NewConnection(device_path, mock_fd, {})

        self.assertIn(device_path, self.profile.active_connections)
        self.assertEqual(self.profile.active_connections[device_path], 42)

    def test_new_connection_multiple_devices(self):
        mock_fd1 = MagicMock()
        mock_fd1.take.return_value = 10
        mock_fd2 = MagicMock()
        mock_fd2.take.return_value = 20

        self.profile.NewConnection("/dev1", mock_fd1, {})
        self.profile.NewConnection("/dev2", mock_fd2, {})

        self.assertEqual(len(self.profile.active_connections), 2)
        self.assertEqual(self.profile.active_connections["/dev1"], 10)
        self.assertEqual(self.profile.active_connections["/dev2"], 20)

    def test_request_disconnection_removes_device(self):
        self.profile._connections = {"/dev1": 42}
        with patch("bluetooth_keyboard_profile.os.close"):
            self.profile.RequestDisconnection("/dev1")
        self.assertNotIn("/dev1", self.profile.active_connections)

    def test_request_disconnection_closes_fd(self):
        self.profile._connections = {"/dev1": 42}
        with patch("bluetooth_keyboard_profile.os.close") as mock_close:
            self.profile.RequestDisconnection("/dev1")
        mock_close.assert_called_once_with(42)

    def test_request_disconnection_unknown_device_is_safe(self):
        # Should not raise when device is not in connections
        self.profile.RequestDisconnection("/unknown/device")
        self.assertEqual(self.profile.active_connections, {})

    def test_request_disconnection_handles_os_error(self):
        self.profile._connections = {"/dev1": 42}
        with patch("bluetooth_keyboard_profile.os.close", side_effect=OSError("Bad fd")):
            # Should not raise
            self.profile.RequestDisconnection("/dev1")
        self.assertNotIn("/dev1", self.profile.active_connections)


class TestActiveConnectionsIsolation(unittest.TestCase):
    """Test that active_connections returns a copy, not the internal dict."""

    def test_active_connections_returns_copy(self):
        bus = MagicMock()
        profile = BluetoothKeyboardProfile(bus)
        profile._connections = {"/dev1": 10}

        connections = profile.active_connections
        connections["/dev2"] = 20

        # Internal state should not be affected
        self.assertNotIn("/dev2", profile._connections)


class TestUUIDFormat(unittest.TestCase):
    """Verify the UUID matches the Bluetooth SIG standard 128-bit format for 0x1124."""

    def test_uuid_starts_with_1124(self):
        self.assertTrue(HID_KEYBOARD_UUID.startswith("00001124"))

    def test_uuid_has_bluetooth_base(self):
        # Bluetooth Base UUID: xxxxxxxx-0000-1000-8000-00805f9b34fb
        self.assertTrue(HID_KEYBOARD_UUID.endswith("-0000-1000-8000-00805f9b34fb"))

    def test_uuid_is_valid_format(self):
        parts = HID_KEYBOARD_UUID.split("-")
        self.assertEqual(len(parts), 5)
        self.assertEqual(len(parts[0]), 8)
        self.assertEqual(len(parts[1]), 4)
        self.assertEqual(len(parts[2]), 4)
        self.assertEqual(len(parts[3]), 4)
        self.assertEqual(len(parts[4]), 12)


if __name__ == "__main__":
    unittest.main()
