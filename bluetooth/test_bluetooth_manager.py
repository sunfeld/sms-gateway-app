"""
Unit tests for BluetoothManager — D-Bus/BlueZ adapter interface.

All D-Bus interactions are mocked so tests run without a real Bluetooth stack.
"""

import unittest
from unittest.mock import patch, MagicMock, PropertyMock, call
import subprocess


# We need to mock dbus before importing bluetooth_manager,
# since the module imports dbus at the top level.
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

import sys
sys.modules["dbus"] = mock_dbus
sys.modules["dbus.mainloop"] = mock_dbus.mainloop
sys.modules["dbus.mainloop.glib"] = mock_dbus.mainloop.glib
sys.modules["dbus.exceptions"] = mock_dbus.exceptions

from bluetooth_manager import (
    BluetoothManager,
    BLUEZ_SERVICE,
    BLUEZ_ADAPTER_IFACE,
    BLUEZ_DEVICE_IFACE,
    BLUEZ_PROFILE_MANAGER_IFACE,
    BLUEZ_AGENT_MANAGER_IFACE,
    DBUS_PROPERTIES_IFACE,
    DBUS_OBJECT_MANAGER_IFACE,
    HID_KEYBOARD_UUID,
    DEVICE_CLASS_PERIPHERAL_KEYBOARD,
    DEVICE_CLASS_MAJOR_PERIPHERAL,
    DEVICE_CLASS_MINOR_KEYBOARD,
    KEYBOARD_SDP_RECORD_XML,
    BLE_ADV_INTERVAL_UNIT_MS,
    BLE_ADV_INTERVAL_MIN_MS,
    DEFAULT_RAPID_ADV_INTERVAL_MS,
    HCI_OGF_LE,
    HCI_OCF_LE_SET_ADV_PARAMS,
    HCI_OCF_LE_SET_ADV_ENABLE,
    ADV_TYPE_IND,
    ADV_TYPE_NONCONN_IND,
    OWN_ADDR_PUBLIC,
    OWN_ADDR_RANDOM,
    ADV_CHANNEL_ALL,
    ADV_FILTER_ALLOW_ALL,
)


class TestBluetoothManagerConstants(unittest.TestCase):
    """Verify D-Bus/BlueZ constants are correctly defined."""

    def test_bluez_service(self):
        self.assertEqual(BLUEZ_SERVICE, "org.bluez")

    def test_adapter_iface(self):
        self.assertEqual(BLUEZ_ADAPTER_IFACE, "org.bluez.Adapter1")

    def test_device_iface(self):
        self.assertEqual(BLUEZ_DEVICE_IFACE, "org.bluez.Device1")

    def test_profile_manager_iface(self):
        self.assertEqual(BLUEZ_PROFILE_MANAGER_IFACE, "org.bluez.ProfileManager1")

    def test_agent_manager_iface(self):
        self.assertEqual(BLUEZ_AGENT_MANAGER_IFACE, "org.bluez.AgentManager1")

    def test_dbus_properties_iface(self):
        self.assertEqual(DBUS_PROPERTIES_IFACE, "org.freedesktop.DBus.Properties")

    def test_object_manager_iface(self):
        self.assertEqual(DBUS_OBJECT_MANAGER_IFACE, "org.freedesktop.DBus.ObjectManager")


class TestBluetoothManagerInit(unittest.TestCase):
    """Test BluetoothManager initialization."""

    def _make_manager(self, adapter_name="hci0"):
        """Helper to create a BluetoothManager with mocked D-Bus."""
        mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        mock_bus.get_object.return_value = mock_adapter_obj

        mock_adapter_iface = MagicMock()
        mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return mock_props_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                mgr = BluetoothManager(adapter_name)

        return mgr, mock_bus, mock_adapter_iface, mock_props_iface

    def test_default_adapter_name(self):
        mgr, _, _, _ = self._make_manager()
        self.assertEqual(mgr._adapter_name, "hci0")
        self.assertEqual(mgr._adapter_path, "/org/bluez/hci0")

    def test_custom_adapter_name(self):
        mgr, _, _, _ = self._make_manager("hci1")
        self.assertEqual(mgr._adapter_name, "hci1")
        self.assertEqual(mgr._adapter_path, "/org/bluez/hci1")

    def test_adapter_path_property(self):
        mgr, _, _, _ = self._make_manager()
        self.assertEqual(mgr.adapter_path, "/org/bluez/hci0")

    def test_bus_property(self):
        mgr, mock_bus, _, _ = self._make_manager()
        self.assertIs(mgr.bus, mock_bus)

    def test_dbus_connection_established(self):
        mgr, mock_bus, _, _ = self._make_manager()
        mock_bus.get_object.assert_called_with(BLUEZ_SERVICE, "/org/bluez/hci0")

    def test_init_dbus_exception_propagates(self):
        mock_bus = MagicMock()
        mock_bus.get_object.side_effect = Exception("No adapter")

        with patch.object(mock_dbus, "SystemBus", return_value=mock_bus):
            with self.assertRaises(Exception):
                BluetoothManager()


class TestAdapterProperties(unittest.TestCase):
    """Test adapter property getters and setters."""

    def setUp(self):
        mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

    def test_get_adapter_property(self):
        self.mock_props_iface.Get.return_value = "test_value"
        result = self.mgr.get_adapter_property("SomeProp")
        self.mock_props_iface.Get.assert_called_with(BLUEZ_ADAPTER_IFACE, "SomeProp")
        self.assertEqual(result, "test_value")

    def test_set_adapter_property(self):
        self.mgr.set_adapter_property("SomeProp", "new_value")
        self.mock_props_iface.Set.assert_called_with(BLUEZ_ADAPTER_IFACE, "SomeProp", "new_value")

    def test_address_property(self):
        self.mock_props_iface.Get.return_value = "AA:BB:CC:DD:EE:FF"
        self.assertEqual(self.mgr.address, "AA:BB:CC:DD:EE:FF")

    def test_name_property(self):
        self.mock_props_iface.Get.return_value = "MyAdapter"
        self.assertEqual(self.mgr.name, "MyAdapter")

    def test_powered_getter_true(self):
        self.mock_props_iface.Get.return_value = True
        self.assertTrue(self.mgr.powered)

    def test_powered_getter_false(self):
        self.mock_props_iface.Get.return_value = False
        self.assertFalse(self.mgr.powered)

    def test_powered_setter(self):
        self.mgr.powered = True
        self.mock_props_iface.Set.assert_called_once()
        args = self.mock_props_iface.Set.call_args
        self.assertEqual(args[0][0], BLUEZ_ADAPTER_IFACE)
        self.assertEqual(args[0][1], "Powered")

    def test_discoverable_getter(self):
        self.mock_props_iface.Get.return_value = True
        self.assertTrue(self.mgr.discoverable)

    def test_discoverable_setter(self):
        self.mgr.discoverable = False
        self.mock_props_iface.Set.assert_called_once()
        args = self.mock_props_iface.Set.call_args
        self.assertEqual(args[0][1], "Discoverable")

    def test_pairable_getter(self):
        self.mock_props_iface.Get.return_value = False
        self.assertFalse(self.mgr.pairable)

    def test_pairable_setter(self):
        self.mgr.pairable = True
        self.mock_props_iface.Set.assert_called_once()
        args = self.mock_props_iface.Set.call_args
        self.assertEqual(args[0][1], "Pairable")

    def test_alias_getter(self):
        self.mock_props_iface.Get.return_value = "TestAlias"
        self.assertEqual(self.mgr.alias, "TestAlias")

    def test_alias_setter(self):
        self.mgr.alias = "NewAlias"
        self.mock_props_iface.Set.assert_called_once()
        args = self.mock_props_iface.Set.call_args
        self.assertEqual(args[0][1], "Alias")

    def test_discoverable_timeout_getter(self):
        self.mock_props_iface.Get.return_value = 300
        self.assertEqual(self.mgr.discoverable_timeout, 300)

    def test_discoverable_timeout_setter(self):
        self.mgr.discoverable_timeout = 0
        self.mock_props_iface.Set.assert_called_once()
        args = self.mock_props_iface.Set.call_args
        self.assertEqual(args[0][1], "DiscoverableTimeout")


class TestDeviceDiscovery(unittest.TestCase):
    """Test device discovery methods."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            elif iface == DBUS_OBJECT_MANAGER_IFACE:
                return self.mock_obj_manager
            return MagicMock()

        self.mock_obj_manager = MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        # Rebind Interface mock for subsequent calls
        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_start_discovery(self):
        self.mgr.start_discovery()
        self.mock_adapter_iface.StartDiscovery.assert_called_once()

    def test_stop_discovery(self):
        self.mgr.stop_discovery()
        self.mock_adapter_iface.StopDiscovery.assert_called_once()

    def test_stop_discovery_ignores_exception(self):
        self.mock_adapter_iface.StopDiscovery.side_effect = Exception("Not discovering")
        # Should not raise
        self.mgr.stop_discovery()

    def test_get_discovered_devices_empty(self):
        self.mock_obj_manager.GetManagedObjects.return_value = {}
        devices = self.mgr.get_discovered_devices()
        self.assertEqual(devices, [])

    def test_get_discovered_devices_filters_by_adapter(self):
        managed = {
            "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF": {
                BLUEZ_DEVICE_IFACE: {
                    "Address": "AA:BB:CC:DD:EE:FF",
                    "Name": "TestDevice",
                    "Paired": False,
                    "Connected": False,
                    "RSSI": -50,
                    "UUIDs": [],
                    "ManufacturerData": {},
                }
            },
            "/org/bluez/hci1/dev_11_22_33_44_55_66": {
                BLUEZ_DEVICE_IFACE: {
                    "Address": "11:22:33:44:55:66",
                    "Name": "OtherDevice",
                    "Paired": False,
                    "Connected": False,
                    "RSSI": -70,
                    "UUIDs": [],
                    "ManufacturerData": {},
                }
            },
        }
        self.mock_obj_manager.GetManagedObjects.return_value = managed
        devices = self.mgr.get_discovered_devices()
        # Should only return the device under hci0
        self.assertEqual(len(devices), 1)
        self.assertEqual(devices[0]["address"], "AA:BB:CC:DD:EE:FF")

    def test_get_discovered_devices_fields(self):
        managed = {
            "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF": {
                BLUEZ_DEVICE_IFACE: {
                    "Address": "AA:BB:CC:DD:EE:FF",
                    "Name": "Keyboard",
                    "Paired": True,
                    "Connected": True,
                    "RSSI": -30,
                    "UUIDs": ["0x1124"],
                    "ManufacturerData": {76: b"\x01\x02"},
                }
            },
        }
        self.mock_obj_manager.GetManagedObjects.return_value = managed
        devices = self.mgr.get_discovered_devices()
        self.assertEqual(len(devices), 1)
        dev = devices[0]
        self.assertEqual(dev["path"], "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")
        self.assertEqual(dev["address"], "AA:BB:CC:DD:EE:FF")
        self.assertEqual(dev["name"], "Keyboard")
        self.assertTrue(dev["paired"])
        self.assertTrue(dev["connected"])
        self.assertEqual(dev["rssi"], -30)
        self.assertEqual(dev["uuids"], ["0x1124"])

    def test_get_discovered_devices_skips_non_device_interfaces(self):
        managed = {
            "/org/bluez/hci0/something": {
                "org.bluez.SomeOtherInterface": {"Foo": "Bar"}
            }
        }
        self.mock_obj_manager.GetManagedObjects.return_value = managed
        devices = self.mgr.get_discovered_devices()
        self.assertEqual(devices, [])

    def test_get_discovered_devices_name_fallback_to_alias(self):
        managed = {
            "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF": {
                BLUEZ_DEVICE_IFACE: {
                    "Address": "AA:BB:CC:DD:EE:FF",
                    "Alias": "AliasName",
                    "Paired": False,
                    "Connected": False,
                    "RSSI": -60,
                    "UUIDs": [],
                    "ManufacturerData": {},
                }
            },
        }
        self.mock_obj_manager.GetManagedObjects.return_value = managed
        devices = self.mgr.get_discovered_devices()
        self.assertEqual(devices[0]["name"], "AliasName")

    def test_remove_device(self):
        self.mgr.remove_device("/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")
        self.mock_adapter_iface.RemoveDevice.assert_called_once()


class TestProfileManager(unittest.TestCase):
    """Test profile registration methods."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_profile_mgr = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            elif iface == BLUEZ_PROFILE_MANAGER_IFACE:
                return self.mock_profile_mgr
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_get_profile_manager(self):
        pm = self.mgr.get_profile_manager()
        self.assertIs(pm, self.mock_profile_mgr)

    def test_get_profile_manager_cached(self):
        pm1 = self.mgr.get_profile_manager()
        pm2 = self.mgr.get_profile_manager()
        self.assertIs(pm1, pm2)

    def test_register_profile(self):
        self.mgr.register_profile("/test/profile", "0x1124", {"Name": "HID"})
        self.mock_profile_mgr.RegisterProfile.assert_called_once()

    def test_unregister_profile(self):
        # First get the profile manager cached
        self.mgr.get_profile_manager()
        self.mgr.unregister_profile("/test/profile")
        self.mock_profile_mgr.UnregisterProfile.assert_called_once()


class TestAgentManager(unittest.TestCase):
    """Test agent manager retrieval."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_agent_mgr = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            elif iface == BLUEZ_AGENT_MANAGER_IFACE:
                return self.mock_agent_mgr
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_get_agent_manager(self):
        am = self.mgr.get_agent_manager()
        self.assertIs(am, self.mock_agent_mgr)


class TestHCIUtilities(unittest.TestCase):
    """Test static HCI utility methods."""

    @patch("bluetooth_manager.subprocess.run")
    def test_hci_set_device_class(self, mock_run):
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        mock_run.assert_called_once()
        args = mock_run.call_args
        self.assertIn("hcitool", args[0][0])
        self.assertTrue(args[1].get("check", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_hci_set_device_class_hex_format(self, mock_run):
        # major=5, minor=64 -> (5 << 8 | 64) = 1344 = 0x000540
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        cmd = mock_run.call_args[0][0]
        self.assertIn("0x000540", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_hci_reset_adapter(self, mock_run):
        BluetoothManager.hci_reset_adapter("hci0")
        mock_run.assert_called_once_with(
            ["hciconfig", "hci0", "reset"],
            check=True,
            capture_output=True,
        )

    @patch("bluetooth_manager.subprocess.run")
    def test_hci_reset_adapter_default(self, mock_run):
        BluetoothManager.hci_reset_adapter()
        cmd = mock_run.call_args[0][0]
        self.assertEqual(cmd[1], "hci0")


class TestAdapterInfo(unittest.TestCase):
    """Test get_adapter_info and ensure_ready."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

    def test_get_adapter_info_keys(self):
        # Mock property reads
        prop_values = {
            ("Name",): "TestAdapter",
            ("Address",): "AA:BB:CC:DD:EE:FF",
            ("Powered",): True,
            ("Discoverable",): False,
            ("Pairable",): True,
            ("Alias",): "TestAlias",
        }

        def get_side_effect(iface, prop):
            return prop_values.get((prop,), None)

        self.mock_props_iface.Get.side_effect = get_side_effect

        info = self.mgr.get_adapter_info()
        self.assertIn("name", info)
        self.assertIn("address", info)
        self.assertIn("powered", info)
        self.assertIn("discoverable", info)
        self.assertIn("pairable", info)
        self.assertIn("alias", info)
        self.assertIn("adapter", info)
        self.assertEqual(info["adapter"], "hci0")

    def test_ensure_ready_powers_on(self):
        call_count = [0]
        prop_values = []

        # Sequence: powered=False, then True after set; discoverable=True, pairable=True
        def get_side_effect(iface, prop):
            if prop == "Powered":
                call_count[0] += 1
                if call_count[0] <= 1:
                    return False
                return True
            if prop == "Discoverable":
                return True
            if prop == "Pairable":
                return True
            if prop == "DiscoverableTimeout":
                return 0
            return "mock"

        self.mock_props_iface.Get.side_effect = get_side_effect

        self.mgr.ensure_ready()
        # Powered should have been set
        set_calls = self.mock_props_iface.Set.call_args_list
        powered_set = any(
            c[0][1] == "Powered" for c in set_calls
        )
        self.assertTrue(powered_set)

    def test_ensure_ready_makes_discoverable(self):
        call_count = {"Discoverable": 0}

        def get_side_effect(iface, prop):
            if prop == "Powered":
                return True
            if prop == "Discoverable":
                call_count["Discoverable"] += 1
                if call_count["Discoverable"] <= 1:
                    return False
                return True
            if prop == "Pairable":
                return True
            return "mock"

        self.mock_props_iface.Get.side_effect = get_side_effect

        self.mgr.ensure_ready()
        set_calls = self.mock_props_iface.Set.call_args_list
        disc_set = any(c[0][1] == "Discoverable" for c in set_calls)
        timeout_set = any(c[0][1] == "DiscoverableTimeout" for c in set_calls)
        self.assertTrue(disc_set)
        self.assertTrue(timeout_set)


class TestDeviceClassConstants(unittest.TestCase):
    """Verify Peripheral/Keyboard device class constants."""

    def test_device_class_value(self):
        self.assertEqual(DEVICE_CLASS_PERIPHERAL_KEYBOARD, 0x000540)

    def test_major_peripheral(self):
        self.assertEqual(DEVICE_CLASS_MAJOR_PERIPHERAL, 0x05)

    def test_minor_keyboard(self):
        self.assertEqual(DEVICE_CLASS_MINOR_KEYBOARD, 0x40)

    def test_class_composition(self):
        # Major << 8 | Minor should equal the full CoD
        composed = (DEVICE_CLASS_MAJOR_PERIPHERAL << 8) | DEVICE_CLASS_MINOR_KEYBOARD
        self.assertEqual(composed, DEVICE_CLASS_PERIPHERAL_KEYBOARD)


class TestKeyboardSDPRecord(unittest.TestCase):
    """Verify the keyboard SDP record XML content."""

    def test_sdp_record_is_xml(self):
        self.assertIn('<?xml version="1.0"', KEYBOARD_SDP_RECORD_XML)
        self.assertIn("<record>", KEYBOARD_SDP_RECORD_XML)
        self.assertIn("</record>", KEYBOARD_SDP_RECORD_XML)

    def test_sdp_record_contains_hid_uuid(self):
        self.assertIn('0x1124', KEYBOARD_SDP_RECORD_XML)

    def test_sdp_record_contains_keyboard_subclass(self):
        # HIDDeviceSubclass 0x40 = Keyboard
        self.assertIn('value="0x40"', KEYBOARD_SDP_RECORD_XML)

    def test_sdp_record_contains_l2cap_psm(self):
        # L2CAP control PSM 0x0011 and interrupt PSM 0x0013
        self.assertIn('value="0x0011"', KEYBOARD_SDP_RECORD_XML)
        self.assertIn('value="0x0013"', KEYBOARD_SDP_RECORD_XML)

    def test_sdp_record_contains_hid_descriptor(self):
        # Should contain the HID report descriptor hex string
        self.assertIn("05010906a101", KEYBOARD_SDP_RECORD_XML)

    def test_sdp_record_boot_device(self):
        # HIDBootDevice should be true
        self.assertIn('id="0x020e"', KEYBOARD_SDP_RECORD_XML)

    def test_sdp_record_service_name(self):
        self.assertIn("Bluetooth Keyboard", KEYBOARD_SDP_RECORD_XML)


class TestConfigureKeyboardSDP(unittest.TestCase):
    """Test configure_keyboard_sdp method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_profile_mgr = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            elif iface == BLUEZ_PROFILE_MANAGER_IFACE:
                return self.mock_profile_mgr
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_sets_device_class(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        mock_run.assert_called_once()
        cmd = mock_run.call_args[0][0]
        self.assertIn("hcitool", cmd)
        self.assertIn("0x000540", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_registers_profile(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        self.mock_profile_mgr.RegisterProfile.assert_called_once()

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_uses_hid_uuid(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        register_args = self.mock_profile_mgr.RegisterProfile.call_args[0]
        uuid = register_args[1]
        self.assertEqual(uuid, "00001124-0000-1000-8000-00805f9b34fb")

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_default_path(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        register_args = self.mock_profile_mgr.RegisterProfile.call_args[0]
        profile_path = register_args[0]
        self.assertEqual(profile_path, "/org/bluez/sdp_keyboard")

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_custom_path(self, mock_run):
        self.mgr.configure_keyboard_sdp("/org/bluez/custom_kb")
        register_args = self.mock_profile_mgr.RegisterProfile.call_args[0]
        profile_path = register_args[0]
        self.assertEqual(profile_path, "/org/bluez/custom_kb")

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_options_contain_service_record(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        register_args = self.mock_profile_mgr.RegisterProfile.call_args[0]
        options = register_args[2]
        self.assertIn("ServiceRecord", options)
        self.assertIn("0x1124", options["ServiceRecord"])

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_options_server_role(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        register_args = self.mock_profile_mgr.RegisterProfile.call_args[0]
        options = register_args[2]
        self.assertEqual(options["Role"], "server")

    @patch("bluetooth_manager.subprocess.run")
    def test_configure_keyboard_sdp_no_auth_required(self, mock_run):
        self.mgr.configure_keyboard_sdp()
        register_args = self.mock_profile_mgr.RegisterProfile.call_args[0]
        options = register_args[2]
        self.assertFalse(options["RequireAuthentication"])
        self.assertFalse(options["RequireAuthorization"])


class TestStaticSDPHelpers(unittest.TestCase):
    """Test static helper methods for device class and SDP record."""

    def test_get_keyboard_device_class(self):
        self.assertEqual(BluetoothManager.get_keyboard_device_class(), 0x000540)

    def test_get_keyboard_sdp_record_returns_xml(self):
        record = BluetoothManager.get_keyboard_sdp_record()
        self.assertIn("<record>", record)
        self.assertIn("0x1124", record)

    def test_get_keyboard_sdp_record_matches_constant(self):
        self.assertEqual(
            BluetoothManager.get_keyboard_sdp_record(),
            KEYBOARD_SDP_RECORD_XML,
        )


class TestBLEAdvertisingConstants(unittest.TestCase):
    """Verify BLE advertising constants are correctly defined."""

    def test_adv_interval_unit(self):
        self.assertEqual(BLE_ADV_INTERVAL_UNIT_MS, 0.625)

    def test_adv_interval_min(self):
        self.assertEqual(BLE_ADV_INTERVAL_MIN_MS, 20)

    def test_default_rapid_interval(self):
        self.assertEqual(DEFAULT_RAPID_ADV_INTERVAL_MS, 20)

    def test_hci_ogf_le(self):
        self.assertEqual(HCI_OGF_LE, 0x08)

    def test_hci_ocf_set_adv_params(self):
        self.assertEqual(HCI_OCF_LE_SET_ADV_PARAMS, 0x0006)

    def test_hci_ocf_set_adv_enable(self):
        self.assertEqual(HCI_OCF_LE_SET_ADV_ENABLE, 0x000A)

    def test_adv_type_ind(self):
        self.assertEqual(ADV_TYPE_IND, 0x00)

    def test_adv_type_nonconn(self):
        self.assertEqual(ADV_TYPE_NONCONN_IND, 0x03)

    def test_own_addr_public(self):
        self.assertEqual(OWN_ADDR_PUBLIC, 0x00)

    def test_own_addr_random(self):
        self.assertEqual(OWN_ADDR_RANDOM, 0x01)

    def test_adv_channel_all(self):
        self.assertEqual(ADV_CHANNEL_ALL, 0x07)

    def test_adv_filter_allow_all(self):
        self.assertEqual(ADV_FILTER_ALLOW_ALL, 0x00)


class TestMsToAdvInterval(unittest.TestCase):
    """Test _ms_to_adv_interval static method."""

    def test_minimum_interval_20ms(self):
        # 20ms / 0.625ms = 32 units
        result = BluetoothManager._ms_to_adv_interval(20)
        self.assertEqual(result, 32)

    def test_100ms_interval(self):
        # 100ms / 0.625ms = 160 units
        result = BluetoothManager._ms_to_adv_interval(100)
        self.assertEqual(result, 160)

    def test_1000ms_interval(self):
        # 1000ms / 0.625ms = 1600 units
        result = BluetoothManager._ms_to_adv_interval(1000)
        self.assertEqual(result, 1600)

    def test_exact_boundary_20ms(self):
        result = BluetoothManager._ms_to_adv_interval(20.0)
        self.assertEqual(result, 32)

    def test_50ms_interval(self):
        # 50ms / 0.625ms = 80 units
        result = BluetoothManager._ms_to_adv_interval(50)
        self.assertEqual(result, 80)

    def test_below_minimum_raises_value_error(self):
        with self.assertRaises(ValueError) as ctx:
            BluetoothManager._ms_to_adv_interval(19)
        self.assertIn("below BLE minimum", str(ctx.exception))

    def test_zero_raises_value_error(self):
        with self.assertRaises(ValueError):
            BluetoothManager._ms_to_adv_interval(0)

    def test_negative_raises_value_error(self):
        with self.assertRaises(ValueError):
            BluetoothManager._ms_to_adv_interval(-10)

    def test_returns_int(self):
        result = BluetoothManager._ms_to_adv_interval(25)
        self.assertIsInstance(result, int)


class TestBuildAdvParamsArgs(unittest.TestCase):
    """Test _build_adv_params_args static method."""

    def test_returns_list_of_hex_strings(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        self.assertIsInstance(args, list)
        for arg in args:
            self.assertTrue(arg.startswith("0x"), f"Expected hex string, got {arg}")

    def test_returns_15_bytes(self):
        # HCI LE Set Advertising Parameters is 15 bytes
        args = BluetoothManager._build_adv_params_args(32, 32)
        self.assertEqual(len(args), 15)

    def test_interval_min_encoded_correctly(self):
        # interval_min=32 (0x0020) in little-endian: 0x20, 0x00
        args = BluetoothManager._build_adv_params_args(32, 32)
        self.assertEqual(args[0], "0x20")
        self.assertEqual(args[1], "0x00")

    def test_interval_max_encoded_correctly(self):
        # interval_max=160 (0x00A0) in little-endian: 0xa0, 0x00
        args = BluetoothManager._build_adv_params_args(32, 160)
        self.assertEqual(args[2], "0xa0")
        self.assertEqual(args[3], "0x00")

    def test_default_adv_type_ind(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte 4 is adv_type, default ADV_TYPE_IND = 0x00
        self.assertEqual(args[4], "0x00")

    def test_custom_adv_type(self):
        args = BluetoothManager._build_adv_params_args(
            32, 32, adv_type=ADV_TYPE_NONCONN_IND
        )
        self.assertEqual(args[4], "0x03")

    def test_default_own_addr_public(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte 5 is own_addr_type
        self.assertEqual(args[5], "0x00")

    def test_random_own_addr(self):
        args = BluetoothManager._build_adv_params_args(
            32, 32, own_addr_type=OWN_ADDR_RANDOM
        )
        self.assertEqual(args[5], "0x01")

    def test_channel_map_byte(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte 13 is channel_map (ADV_CHANNEL_ALL = 0x07)
        self.assertEqual(args[13], "0x07")

    def test_filter_policy_byte(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte 14 is filter_policy (ADV_FILTER_ALLOW_ALL = 0x00)
        self.assertEqual(args[14], "0x00")


class TestStartRapidAdvertising(unittest.TestCase):
    """Test start_rapid_advertising method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

    @patch("bluetooth_manager.subprocess.run")
    def test_sends_two_hcitool_commands(self, mock_run):
        self.mgr.start_rapid_advertising()
        self.assertEqual(mock_run.call_count, 2)

    @patch("bluetooth_manager.subprocess.run")
    def test_first_command_sets_adv_params(self, mock_run):
        self.mgr.start_rapid_advertising()
        first_call = mock_run.call_args_list[0]
        cmd = first_call[0][0]
        self.assertEqual(cmd[0], "hcitool")
        self.assertIn(f"0x{HCI_OGF_LE:02x}", cmd)
        self.assertIn(f"0x{HCI_OCF_LE_SET_ADV_PARAMS:04x}", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_second_command_enables_advertising(self, mock_run):
        self.mgr.start_rapid_advertising()
        second_call = mock_run.call_args_list[1]
        cmd = second_call[0][0]
        self.assertEqual(cmd[0], "hcitool")
        self.assertIn(f"0x{HCI_OGF_LE:02x}", cmd)
        self.assertIn(f"0x{HCI_OCF_LE_SET_ADV_ENABLE:04x}", cmd)
        self.assertIn("0x01", cmd)  # enable = 0x01

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_default_adapter(self, mock_run):
        self.mgr.start_rapid_advertising()
        first_call = mock_run.call_args_list[0]
        cmd = first_call[0][0]
        self.assertIn("hci0", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_custom_adapter(self, mock_run):
        self.mgr.start_rapid_advertising(adapter="hci1")
        first_call = mock_run.call_args_list[0]
        cmd = first_call[0][0]
        self.assertIn("hci1", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_returns_config_dict(self, mock_run):
        result = self.mgr.start_rapid_advertising()
        self.assertIsInstance(result, dict)
        self.assertIn("adapter", result)
        self.assertIn("interval_min_ms", result)
        self.assertIn("interval_max_ms", result)
        self.assertIn("interval_min_units", result)
        self.assertIn("interval_max_units", result)
        self.assertIn("adv_type", result)
        self.assertIn("own_addr_type", result)
        self.assertIn("enabled", result)

    @patch("bluetooth_manager.subprocess.run")
    def test_default_interval_20ms(self, mock_run):
        result = self.mgr.start_rapid_advertising()
        self.assertEqual(result["interval_min_ms"], 20)
        self.assertEqual(result["interval_max_ms"], 20)
        self.assertEqual(result["interval_min_units"], 32)
        self.assertEqual(result["interval_max_units"], 32)

    @patch("bluetooth_manager.subprocess.run")
    def test_custom_interval(self, mock_run):
        result = self.mgr.start_rapid_advertising(
            interval_min_ms=50, interval_max_ms=100
        )
        self.assertEqual(result["interval_min_ms"], 50)
        self.assertEqual(result["interval_max_ms"], 100)
        self.assertEqual(result["interval_min_units"], 80)
        self.assertEqual(result["interval_max_units"], 160)

    @patch("bluetooth_manager.subprocess.run")
    def test_enabled_true_in_result(self, mock_run):
        result = self.mgr.start_rapid_advertising()
        self.assertTrue(result["enabled"])

    @patch("bluetooth_manager.subprocess.run")
    def test_adv_type_in_result(self, mock_run):
        result = self.mgr.start_rapid_advertising(adv_type=ADV_TYPE_NONCONN_IND)
        self.assertEqual(result["adv_type"], ADV_TYPE_NONCONN_IND)

    @patch("bluetooth_manager.subprocess.run")
    def test_own_addr_type_in_result(self, mock_run):
        result = self.mgr.start_rapid_advertising(own_addr_type=OWN_ADDR_RANDOM)
        self.assertEqual(result["own_addr_type"], OWN_ADDR_RANDOM)

    def test_below_minimum_interval_raises(self):
        with self.assertRaises(ValueError):
            self.mgr.start_rapid_advertising(interval_min_ms=10)

    def test_interval_max_below_min_raises(self):
        with self.assertRaises(ValueError):
            self.mgr.start_rapid_advertising(
                interval_min_ms=100, interval_max_ms=50
            )

    @patch("bluetooth_manager.subprocess.run")
    def test_check_true_on_subprocess_calls(self, mock_run):
        self.mgr.start_rapid_advertising()
        for call_item in mock_run.call_args_list:
            self.assertTrue(call_item[1].get("check", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_capture_output_on_subprocess_calls(self, mock_run):
        self.mgr.start_rapid_advertising()
        for call_item in mock_run.call_args_list:
            self.assertTrue(call_item[1].get("capture_output", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_subprocess_error_propagates(self, mock_run):
        mock_run.side_effect = subprocess.CalledProcessError(1, "hcitool")
        with self.assertRaises(subprocess.CalledProcessError):
            self.mgr.start_rapid_advertising()

    @patch("bluetooth_manager.subprocess.run")
    def test_adv_params_command_has_15_hex_args(self, mock_run):
        self.mgr.start_rapid_advertising()
        first_call = mock_run.call_args_list[0]
        cmd = first_call[0][0]
        # Command: hcitool -i hci0 cmd 0x08 0x0006 + 15 hex bytes
        hex_args = [a for a in cmd if a.startswith("0x") and a not in (
            f"0x{HCI_OGF_LE:02x}", f"0x{HCI_OCF_LE_SET_ADV_PARAMS:04x}"
        )]
        self.assertEqual(len(hex_args), 15)


class TestStopRapidAdvertising(unittest.TestCase):
    """Test stop_rapid_advertising method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

    @patch("bluetooth_manager.subprocess.run")
    def test_sends_one_hcitool_command(self, mock_run):
        self.mgr.stop_rapid_advertising()
        mock_run.assert_called_once()

    @patch("bluetooth_manager.subprocess.run")
    def test_sends_disable_command(self, mock_run):
        self.mgr.stop_rapid_advertising()
        cmd = mock_run.call_args[0][0]
        self.assertEqual(cmd[0], "hcitool")
        self.assertIn(f"0x{HCI_OGF_LE:02x}", cmd)
        self.assertIn(f"0x{HCI_OCF_LE_SET_ADV_ENABLE:04x}", cmd)
        self.assertIn("0x00", cmd)  # disable = 0x00

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_default_adapter(self, mock_run):
        self.mgr.stop_rapid_advertising()
        cmd = mock_run.call_args[0][0]
        self.assertIn("hci0", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_custom_adapter(self, mock_run):
        self.mgr.stop_rapid_advertising(adapter="hci1")
        cmd = mock_run.call_args[0][0]
        self.assertIn("hci1", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_check_true_on_subprocess(self, mock_run):
        self.mgr.stop_rapid_advertising()
        self.assertTrue(mock_run.call_args[1].get("check", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_capture_output_on_subprocess(self, mock_run):
        self.mgr.stop_rapid_advertising()
        self.assertTrue(mock_run.call_args[1].get("capture_output", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_subprocess_error_propagates(self, mock_run):
        mock_run.side_effect = subprocess.CalledProcessError(1, "hcitool")
        with self.assertRaises(subprocess.CalledProcessError):
            self.mgr.stop_rapid_advertising()


class TestAddressToDevicePath(unittest.TestCase):
    """Test _address_to_device_path helper method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

    def test_converts_colons_to_underscores(self):
        path = self.mgr._address_to_device_path("AA:BB:CC:DD:EE:FF")
        self.assertEqual(path, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_uppercases_address(self):
        path = self.mgr._address_to_device_path("aa:bb:cc:dd:ee:ff")
        self.assertEqual(path, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_uses_adapter_path(self):
        mock_bus = MagicMock()
        mock_bus.get_object.return_value = MagicMock()

        def iface_side_effect(obj, iface):
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                mgr = BluetoothManager("hci1")

        path = mgr._address_to_device_path("11:22:33:44:55:66")
        self.assertEqual(path, "/org/bluez/hci1/dev_11_22_33_44_55_66")


class TestGetDeviceInterface(unittest.TestCase):
    """Test get_device_interface and get_device_properties_interface."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_device_iface = MagicMock()
        self.mock_device_props = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_device_props
            elif iface == BLUEZ_DEVICE_IFACE:
                return self.mock_device_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_get_device_interface_calls_get_object(self):
        self.mgr.get_device_interface("AA:BB:CC:DD:EE:FF")
        self.mock_bus.get_object.assert_called_with(
            BLUEZ_SERVICE, "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF"
        )

    def test_get_device_interface_returns_device1_interface(self):
        result = self.mgr.get_device_interface("AA:BB:CC:DD:EE:FF")
        self.assertIs(result, self.mock_device_iface)

    def test_get_device_properties_interface_returns_props(self):
        result = self.mgr.get_device_properties_interface("AA:BB:CC:DD:EE:FF")
        self.assertIs(result, self.mock_device_props)


class TestSetDeviceTrusted(unittest.TestCase):
    """Test set_device_trusted method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_device_props = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_device_props
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_sets_trusted_true(self):
        self.mgr.set_device_trusted("AA:BB:CC:DD:EE:FF", True)
        self.mock_device_props.Set.assert_called_once_with(
            BLUEZ_DEVICE_IFACE, "Trusted", True
        )

    def test_sets_trusted_false(self):
        self.mgr.set_device_trusted("AA:BB:CC:DD:EE:FF", False)
        self.mock_device_props.Set.assert_called_once_with(
            BLUEZ_DEVICE_IFACE, "Trusted", False
        )

    def test_default_trusted_is_true(self):
        self.mgr.set_device_trusted("AA:BB:CC:DD:EE:FF")
        self.mock_device_props.Set.assert_called_once_with(
            BLUEZ_DEVICE_IFACE, "Trusted", True
        )


class TestTriggerPairingRequest(unittest.TestCase):
    """Test trigger_pairing_request method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_device_iface = MagicMock()
        self.mock_device_props = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_device_props
            elif iface == BLUEZ_DEVICE_IFACE:
                return self.mock_device_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_returns_dict_with_expected_keys(self):
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertIn("address", result)
        self.assertIn("device_path", result)
        self.assertIn("status", result)
        self.assertIn("paired", result)
        self.assertIn("connected", result)
        self.assertIn("error", result)

    def test_successful_pairing_sets_paired_true(self):
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertTrue(result["paired"])

    def test_successful_pairing_status_is_connected(self):
        # When both Pair() and ConnectProfile() succeed
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertEqual(result["status"], "connected")
        self.assertTrue(result["connected"])

    def test_calls_pair_on_device(self):
        self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.mock_device_iface.Pair.assert_called_once()

    def test_calls_connect_profile_after_pairing(self):
        from bluetooth_manager import HID_KEYBOARD_UUID
        self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.mock_device_iface.ConnectProfile.assert_called_once_with(HID_KEYBOARD_UUID)

    def test_sets_device_trusted_before_pairing(self):
        self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.mock_device_props.Set.assert_called_with(
            BLUEZ_DEVICE_IFACE, "Trusted", True
        )

    def test_set_trusted_false_skips_trust(self):
        self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF", set_trusted=False)
        # Should not call Set for Trusted
        trusted_calls = [
            c for c in self.mock_device_props.Set.call_args_list
            if len(c[0]) >= 2 and c[0][1] == "Trusted"
        ]
        self.assertEqual(len(trusted_calls), 0)

    def test_address_in_result(self):
        result = self.mgr.trigger_pairing_request("11:22:33:44:55:66")
        self.assertEqual(result["address"], "11:22:33:44:55:66")

    def test_device_path_in_result(self):
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertEqual(result["device_path"], "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF")

    def test_error_is_none_on_success(self):
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertIsNone(result["error"])

    def test_device_not_found_returns_error(self):
        self.mock_bus.get_object.side_effect = Exception("Device not found")
        # Need to re-mock Interface since get_object will fail
        mock_dbus.Interface = MagicMock(side_effect=Exception("No object"))
        result = self.mgr.trigger_pairing_request("FF:FF:FF:FF:FF:FF")
        self.assertEqual(result["status"], "failed")
        self.assertFalse(result["paired"])
        self.assertIsNotNone(result["error"])

    def test_pair_exception_sets_error(self):
        self.mock_device_iface.Pair.side_effect = Exception("Authentication rejected")
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertEqual(result["status"], "failed")
        self.assertFalse(result["paired"])
        self.assertIsNotNone(result["error"])

    def test_already_paired_treated_as_success(self):
        exc = Exception("Already paired")
        exc.get_dbus_name = lambda: "org.bluez.Error.AlreadyExists"
        self.mock_device_iface.Pair.side_effect = exc
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertTrue(result["paired"])
        # ConnectProfile still succeeds on mock, so status is "connected"
        self.assertIn(result["status"], ("paired", "connected"))

    def test_connect_profile_failure_still_paired(self):
        self.mock_device_iface.ConnectProfile.side_effect = Exception("Connection failed")
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        self.assertTrue(result["paired"])
        self.assertFalse(result["connected"])
        self.assertEqual(result["status"], "paired")

    def test_trust_failure_still_attempts_pairing(self):
        self.mock_device_props.Set.side_effect = Exception("Cannot set property")
        result = self.mgr.trigger_pairing_request("AA:BB:CC:DD:EE:FF")
        # Pairing should still be attempted even if trust fails
        self.mock_device_iface.Pair.assert_called_once()


class TestTriggerPairingRequests(unittest.TestCase):
    """Test trigger_pairing_requests batch method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_device_iface = MagicMock()
        self.mock_device_props = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_device_props
            elif iface == BLUEZ_DEVICE_IFACE:
                return self.mock_device_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_returns_list_of_results(self):
        results = self.mgr.trigger_pairing_requests([
            "AA:BB:CC:DD:EE:FF",
            "11:22:33:44:55:66",
        ])
        self.assertIsInstance(results, list)
        self.assertEqual(len(results), 2)

    def test_each_result_has_correct_address(self):
        addresses = ["AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"]
        results = self.mgr.trigger_pairing_requests(addresses)
        for i, result in enumerate(results):
            self.assertEqual(result["address"], addresses[i])

    def test_empty_list_returns_empty(self):
        results = self.mgr.trigger_pairing_requests([])
        self.assertEqual(results, [])

    def test_single_address(self):
        results = self.mgr.trigger_pairing_requests(["AA:BB:CC:DD:EE:FF"])
        self.assertEqual(len(results), 1)

    def test_passes_set_trusted_param(self):
        with patch.object(self.mgr, "trigger_pairing_request") as mock_trigger:
            mock_trigger.return_value = {"address": "AA:BB:CC:DD:EE:FF", "status": "paired"}
            self.mgr.trigger_pairing_requests(["AA:BB:CC:DD:EE:FF"], set_trusted=False)
            mock_trigger.assert_called_once_with("AA:BB:CC:DD:EE:FF", set_trusted=False)

    def test_error_on_one_does_not_stop_batch(self):
        call_count = [0]
        original_pair = self.mock_device_iface.Pair

        def pair_side_effect(*args, **kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                raise Exception("First device failed")

        self.mock_device_iface.Pair.side_effect = pair_side_effect
        results = self.mgr.trigger_pairing_requests([
            "AA:BB:CC:DD:EE:FF",
            "11:22:33:44:55:66",
        ])
        self.assertEqual(len(results), 2)
        # First should fail, second should succeed
        self.assertFalse(results[0]["paired"])
        self.assertTrue(results[1]["paired"])


class TestCancelPairing(unittest.TestCase):
    """Test cancel_pairing method."""

    def setUp(self):
        self.mock_bus = MagicMock()
        mock_adapter_obj = MagicMock()
        self.mock_bus.get_object.return_value = mock_adapter_obj

        self.mock_adapter_iface = MagicMock()
        self.mock_props_iface = MagicMock()
        self.mock_device_iface = MagicMock()

        def iface_side_effect(obj, iface):
            if iface == BLUEZ_ADAPTER_IFACE:
                return self.mock_adapter_iface
            elif iface == DBUS_PROPERTIES_IFACE:
                return self.mock_props_iface
            elif iface == BLUEZ_DEVICE_IFACE:
                return self.mock_device_iface
            return MagicMock()

        with patch.object(mock_dbus, "SystemBus", return_value=self.mock_bus):
            with patch.object(mock_dbus, "Interface", side_effect=iface_side_effect):
                self.mgr = BluetoothManager()

        mock_dbus.Interface = MagicMock(side_effect=iface_side_effect)

    def test_cancel_pairing_returns_true_on_success(self):
        result = self.mgr.cancel_pairing("AA:BB:CC:DD:EE:FF")
        self.assertTrue(result)

    def test_cancel_pairing_calls_cancel(self):
        self.mgr.cancel_pairing("AA:BB:CC:DD:EE:FF")
        self.mock_device_iface.CancelPairing.assert_called_once()

    def test_cancel_pairing_returns_false_on_error(self):
        self.mock_device_iface.CancelPairing.side_effect = Exception("Not pairing")
        result = self.mgr.cancel_pairing("AA:BB:CC:DD:EE:FF")
        self.assertFalse(result)


if __name__ == "__main__":
    unittest.main()
