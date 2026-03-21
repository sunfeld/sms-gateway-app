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


if __name__ == "__main__":
    unittest.main()
