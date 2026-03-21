"""
Unit tests for start_rapid_advertising() and stop_rapid_advertising() —
BLE high-frequency advertising via HCI LE commands.

All HCI/subprocess interactions are mocked so tests run without hardware.
"""

import struct
import unittest
from unittest.mock import patch, MagicMock, call

# Mock dbus before importing bluetooth_manager
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
    HCI_OGF_LE,
    HCI_OCF_LE_SET_ADV_PARAMS,
    HCI_OCF_LE_SET_ADV_ENABLE,
    BLE_ADV_INTERVAL_UNIT_MS,
    BLE_ADV_INTERVAL_MIN_MS,
    DEFAULT_RAPID_ADV_INTERVAL_MS,
    ADV_TYPE_IND,
    ADV_TYPE_NONCONN_IND,
    OWN_ADDR_PUBLIC,
    OWN_ADDR_RANDOM,
    ADV_CHANNEL_ALL,
    ADV_FILTER_ALLOW_ALL,
    BLUEZ_ADAPTER_IFACE,
    DBUS_PROPERTIES_IFACE,
)


def _make_manager(adapter_name="hci0"):
    """Create a BluetoothManager with fully mocked D-Bus."""
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

    return mgr


class TestConstants(unittest.TestCase):
    """Verify BLE advertising constants."""

    def test_hci_ogf_le(self):
        self.assertEqual(HCI_OGF_LE, 0x08)

    def test_hci_ocf_adv_params(self):
        self.assertEqual(HCI_OCF_LE_SET_ADV_PARAMS, 0x0006)

    def test_hci_ocf_adv_enable(self):
        self.assertEqual(HCI_OCF_LE_SET_ADV_ENABLE, 0x000A)

    def test_adv_interval_unit(self):
        self.assertAlmostEqual(BLE_ADV_INTERVAL_UNIT_MS, 0.625)

    def test_adv_interval_min(self):
        self.assertEqual(BLE_ADV_INTERVAL_MIN_MS, 20)

    def test_default_rapid_interval(self):
        self.assertEqual(DEFAULT_RAPID_ADV_INTERVAL_MS, 20)

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
    """Test _ms_to_adv_interval conversion."""

    def test_20ms_converts_to_32(self):
        # 20ms / 0.625ms = 32
        result = BluetoothManager._ms_to_adv_interval(20)
        self.assertEqual(result, 32)

    def test_100ms_converts_to_160(self):
        # 100ms / 0.625ms = 160
        result = BluetoothManager._ms_to_adv_interval(100)
        self.assertEqual(result, 160)

    def test_1000ms_converts_to_1600(self):
        # 1000ms / 0.625ms = 1600
        result = BluetoothManager._ms_to_adv_interval(1000)
        self.assertEqual(result, 1600)

    def test_below_minimum_raises_value_error(self):
        with self.assertRaises(ValueError) as ctx:
            BluetoothManager._ms_to_adv_interval(10)
        self.assertIn("below BLE minimum", str(ctx.exception))

    def test_zero_raises_value_error(self):
        with self.assertRaises(ValueError):
            BluetoothManager._ms_to_adv_interval(0)

    def test_negative_raises_value_error(self):
        with self.assertRaises(ValueError):
            BluetoothManager._ms_to_adv_interval(-5)

    def test_exact_minimum_succeeds(self):
        result = BluetoothManager._ms_to_adv_interval(20)
        self.assertEqual(result, 32)

    def test_fractional_ms_rounds_down(self):
        # 25ms / 0.625 = 40
        result = BluetoothManager._ms_to_adv_interval(25)
        self.assertEqual(result, 40)


class TestBuildAdvParamsArgs(unittest.TestCase):
    """Test _build_adv_params_args hex byte generation."""

    def test_returns_15_hex_bytes(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        self.assertEqual(len(args), 15)

    def test_all_args_are_hex_strings(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        for arg in args:
            self.assertTrue(arg.startswith("0x"), f"{arg} is not hex")

    def test_interval_min_encoding(self):
        # 32 = 0x0020 little-endian = 0x20 0x00
        args = BluetoothManager._build_adv_params_args(32, 32)
        self.assertEqual(args[0], "0x20")
        self.assertEqual(args[1], "0x00")

    def test_interval_max_encoding(self):
        # 32 = 0x0020 little-endian = 0x20 0x00
        args = BluetoothManager._build_adv_params_args(32, 32)
        self.assertEqual(args[2], "0x20")
        self.assertEqual(args[3], "0x00")

    def test_default_adv_type_is_ind(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte index 4 = adv_type
        self.assertEqual(args[4], "0x00")

    def test_custom_adv_type(self):
        args = BluetoothManager._build_adv_params_args(
            32, 32, adv_type=ADV_TYPE_NONCONN_IND
        )
        self.assertEqual(args[4], "0x03")

    def test_default_own_addr_public(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte index 5 = own_addr_type
        self.assertEqual(args[5], "0x00")

    def test_random_own_addr(self):
        args = BluetoothManager._build_adv_params_args(
            32, 32, own_addr_type=OWN_ADDR_RANDOM
        )
        self.assertEqual(args[5], "0x01")

    def test_channel_map_all(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte index 13 = channel_map
        self.assertEqual(args[13], "0x07")

    def test_filter_policy_allow_all(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # byte index 14 = filter_policy
        self.assertEqual(args[14], "0x00")

    def test_larger_interval_encoding(self):
        # 160 = 0x00A0 little-endian = 0xA0 0x00
        args = BluetoothManager._build_adv_params_args(160, 160)
        self.assertEqual(args[0], "0xa0")
        self.assertEqual(args[1], "0x00")

    def test_peer_address_zeros(self):
        args = BluetoothManager._build_adv_params_args(32, 32)
        # bytes 7-12 are peer address (6 zero bytes)
        for i in range(7, 13):
            self.assertEqual(args[i], "0x00")


class TestStartRapidAdvertising(unittest.TestCase):
    """Test start_rapid_advertising method."""

    def setUp(self):
        self.mgr = _make_manager()

    @patch("bluetooth_manager.subprocess.run")
    def test_sends_two_hcitool_commands(self, mock_run):
        self.mgr.start_rapid_advertising()
        self.assertEqual(mock_run.call_count, 2)

    @patch("bluetooth_manager.subprocess.run")
    def test_first_command_sets_adv_params(self, mock_run):
        self.mgr.start_rapid_advertising()
        first_cmd = mock_run.call_args_list[0][0][0]
        self.assertEqual(first_cmd[0], "hcitool")
        self.assertIn("0x08", first_cmd)
        self.assertIn("0x0006", first_cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_second_command_enables_advertising(self, mock_run):
        self.mgr.start_rapid_advertising()
        second_cmd = mock_run.call_args_list[1][0][0]
        self.assertEqual(second_cmd[0], "hcitool")
        self.assertIn("0x08", second_cmd)
        self.assertIn("0x000a", second_cmd)
        self.assertIn("0x01", second_cmd)

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
    def test_returns_enabled_true(self, mock_run):
        result = self.mgr.start_rapid_advertising()
        self.assertTrue(result["enabled"])

    @patch("bluetooth_manager.subprocess.run")
    def test_returns_adapter_name(self, mock_run):
        result = self.mgr.start_rapid_advertising()
        self.assertEqual(result["adapter"], "hci0")

    @patch("bluetooth_manager.subprocess.run")
    def test_returns_adv_type(self, mock_run):
        result = self.mgr.start_rapid_advertising()
        self.assertEqual(result["adv_type"], ADV_TYPE_IND)

    @patch("bluetooth_manager.subprocess.run")
    def test_custom_adv_type(self, mock_run):
        result = self.mgr.start_rapid_advertising(adv_type=ADV_TYPE_NONCONN_IND)
        self.assertEqual(result["adv_type"], ADV_TYPE_NONCONN_IND)

    @patch("bluetooth_manager.subprocess.run")
    def test_custom_own_addr_type(self, mock_run):
        result = self.mgr.start_rapid_advertising(own_addr_type=OWN_ADDR_RANDOM)
        self.assertEqual(result["own_addr_type"], OWN_ADDR_RANDOM)

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_instance_adapter_by_default(self, mock_run):
        mgr = _make_manager("hci1")
        mgr.start_rapid_advertising()
        first_cmd = mock_run.call_args_list[0][0][0]
        self.assertIn("hci1", first_cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_adapter_override(self, mock_run):
        self.mgr.start_rapid_advertising(adapter="hci2")
        first_cmd = mock_run.call_args_list[0][0][0]
        self.assertIn("hci2", first_cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_commands_use_check_true(self, mock_run):
        self.mgr.start_rapid_advertising()
        for c in mock_run.call_args_list:
            self.assertTrue(c[1].get("check", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_commands_capture_output(self, mock_run):
        self.mgr.start_rapid_advertising()
        for c in mock_run.call_args_list:
            self.assertTrue(c[1].get("capture_output", False))

    def test_interval_below_minimum_raises(self):
        with self.assertRaises(ValueError):
            self.mgr.start_rapid_advertising(interval_min_ms=10)

    def test_max_below_min_raises(self):
        with self.assertRaises(ValueError):
            self.mgr.start_rapid_advertising(
                interval_min_ms=100, interval_max_ms=50
            )

    @patch("bluetooth_manager.subprocess.run")
    def test_hcitool_failure_propagates(self, mock_run):
        import subprocess
        mock_run.side_effect = subprocess.CalledProcessError(1, "hcitool")
        with self.assertRaises(subprocess.CalledProcessError):
            self.mgr.start_rapid_advertising()

    @patch("bluetooth_manager.subprocess.run")
    def test_adv_params_contain_interval_bytes(self, mock_run):
        """Verify the actual HCI bytes encode 20ms (0x0020 LE = 0x20 0x00)."""
        self.mgr.start_rapid_advertising()
        first_cmd = mock_run.call_args_list[0][0][0]
        # After the hcitool preamble (6 args), the first two params are interval_min
        param_start = 6  # hcitool -i hci0 cmd 0x08 0x0006
        self.assertEqual(first_cmd[param_start], "0x20")
        self.assertEqual(first_cmd[param_start + 1], "0x00")


class TestStopRapidAdvertising(unittest.TestCase):
    """Test stop_rapid_advertising method."""

    def setUp(self):
        self.mgr = _make_manager()

    @patch("bluetooth_manager.subprocess.run")
    def test_sends_one_command(self, mock_run):
        self.mgr.stop_rapid_advertising()
        mock_run.assert_called_once()

    @patch("bluetooth_manager.subprocess.run")
    def test_disables_advertising(self, mock_run):
        self.mgr.stop_rapid_advertising()
        cmd = mock_run.call_args[0][0]
        self.assertEqual(cmd[0], "hcitool")
        self.assertIn("0x08", cmd)
        self.assertIn("0x000a", cmd)
        self.assertIn("0x00", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_instance_adapter(self, mock_run):
        mgr = _make_manager("hci1")
        mgr.stop_rapid_advertising()
        cmd = mock_run.call_args[0][0]
        self.assertIn("hci1", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_adapter_override(self, mock_run):
        self.mgr.stop_rapid_advertising(adapter="hci3")
        cmd = mock_run.call_args[0][0]
        self.assertIn("hci3", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_check_true(self, mock_run):
        self.mgr.stop_rapid_advertising()
        self.assertTrue(mock_run.call_args[1].get("check", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_uses_capture_output(self, mock_run):
        self.mgr.stop_rapid_advertising()
        self.assertTrue(mock_run.call_args[1].get("capture_output", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_hcitool_failure_propagates(self, mock_run):
        import subprocess
        mock_run.side_effect = subprocess.CalledProcessError(1, "hcitool")
        with self.assertRaises(subprocess.CalledProcessError):
            self.mgr.stop_rapid_advertising()


class TestStartStopRoundTrip(unittest.TestCase):
    """Test start followed by stop sends correct sequence."""

    def setUp(self):
        self.mgr = _make_manager()

    @patch("bluetooth_manager.subprocess.run")
    def test_start_then_stop_sends_three_commands(self, mock_run):
        self.mgr.start_rapid_advertising()
        self.mgr.stop_rapid_advertising()
        self.assertEqual(mock_run.call_count, 3)

    @patch("bluetooth_manager.subprocess.run")
    def test_last_command_disables(self, mock_run):
        self.mgr.start_rapid_advertising()
        self.mgr.stop_rapid_advertising()
        last_cmd = mock_run.call_args_list[-1][0][0]
        self.assertIn("0x00", last_cmd)  # disable byte


if __name__ == "__main__":
    unittest.main()
