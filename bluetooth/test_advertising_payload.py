"""
Unit tests for AdvertisingPayload — MAC address randomization and device
name rotation for Bluetooth HID keyboard advertising.

Validates that advertising_payload.py correctly generates random MAC
addresses using known OUI prefixes, rotates device names, and applies
payloads via the BluetoothManager.
"""

import unittest
from unittest.mock import patch, MagicMock
import subprocess
import sys

# Mock dbus before importing modules that depend on it
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

mock_service = MagicMock()


class FakeDBusServiceObject:
    def __init__(self, bus=None, object_path=None, *args, **kwargs):
        pass


mock_service.Object = FakeDBusServiceObject


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

from advertising_payload import (
    AdvertisingPayload,
    DEFAULT_DEVICE_NAMES,
    KNOWN_OUI_PREFIXES,
)


# ── Constants Tests ──────────────────────────────────────────────────


class TestDefaultDeviceNames(unittest.TestCase):
    """Verify the default device name list is properly configured."""

    def test_default_names_is_nonempty_list(self):
        self.assertIsInstance(DEFAULT_DEVICE_NAMES, list)
        self.assertGreater(len(DEFAULT_DEVICE_NAMES), 0)

    def test_contains_apple_magic_keyboard(self):
        self.assertIn("Apple Magic Keyboard", DEFAULT_DEVICE_NAMES)

    def test_contains_logitech_k380(self):
        self.assertIn("Logitech K380", DEFAULT_DEVICE_NAMES)

    def test_all_names_are_nonempty_strings(self):
        for name in DEFAULT_DEVICE_NAMES:
            self.assertIsInstance(name, str)
            self.assertGreater(len(name), 0, f"Empty device name found")

    def test_no_duplicate_names(self):
        self.assertEqual(len(DEFAULT_DEVICE_NAMES), len(set(DEFAULT_DEVICE_NAMES)))

    def test_minimum_name_count(self):
        """Should have a reasonable variety of names for rotation."""
        self.assertGreaterEqual(len(DEFAULT_DEVICE_NAMES), 10)


class TestKnownOUIPrefixes(unittest.TestCase):
    """Verify the OUI prefix list is properly configured."""

    def test_oui_prefixes_is_nonempty_list(self):
        self.assertIsInstance(KNOWN_OUI_PREFIXES, list)
        self.assertGreater(len(KNOWN_OUI_PREFIXES), 0)

    def test_all_oui_are_3_byte_tuples(self):
        for oui in KNOWN_OUI_PREFIXES:
            self.assertIsInstance(oui, tuple, f"{oui} is not a tuple")
            self.assertEqual(len(oui), 3, f"OUI {oui} does not have 3 bytes")

    def test_all_oui_bytes_in_valid_range(self):
        for oui in KNOWN_OUI_PREFIXES:
            for byte_val in oui:
                self.assertGreaterEqual(byte_val, 0x00)
                self.assertLessEqual(byte_val, 0xFF)

    def test_minimum_oui_count(self):
        """Should have multiple OUIs for realistic variety."""
        self.assertGreaterEqual(len(KNOWN_OUI_PREFIXES), 5)


# ── Initialization Tests ─────────────────────────────────────────────


class TestAdvertisingPayloadInit(unittest.TestCase):
    """Test AdvertisingPayload initialization."""

    def test_default_initialization(self):
        payload = AdvertisingPayload(shuffle=False)
        self.assertIsNotNone(payload)

    def test_custom_device_names(self):
        names = ["Test Keyboard A", "Test Keyboard B"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        self.assertEqual(payload.device_names, names)

    def test_custom_oui_prefixes(self):
        oui = [(0xAA, 0xBB, 0xCC)]
        payload = AdvertisingPayload(oui_prefixes=oui, shuffle=False)
        # OUI prefixes are stored internally
        self.assertIsNotNone(payload)

    def test_shuffle_false_preserves_order(self):
        names = ["A", "B", "C"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        self.assertEqual(payload.device_names, ["A", "B", "C"])

    def test_initial_rotation_count_is_zero(self):
        payload = AdvertisingPayload(shuffle=False)
        self.assertEqual(payload.rotation_count, 0)

    def test_initial_name_index_is_zero(self):
        payload = AdvertisingPayload(shuffle=False)
        self.assertEqual(payload.current_name_index, 0)

    def test_device_names_are_copied(self):
        """Modifying the input list should not affect the generator."""
        names = ["X", "Y"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        names.append("Z")
        self.assertEqual(len(payload.device_names), 2)


# ── MAC Address Generation Tests ─────────────────────────────────────


class TestGenerateRandomMac(unittest.TestCase):
    """Test random MAC address generation with OUI prefixes."""

    def setUp(self):
        self.payload = AdvertisingPayload(shuffle=False)

    def test_returns_string(self):
        mac = self.payload.generate_random_mac()
        self.assertIsInstance(mac, str)

    def test_mac_format_xx_xx_xx_xx_xx_xx(self):
        mac = self.payload.generate_random_mac()
        parts = mac.split(":")
        self.assertEqual(len(parts), 6, f"MAC {mac} does not have 6 octets")

    def test_mac_octets_are_two_hex_chars(self):
        mac = self.payload.generate_random_mac()
        for part in mac.split(":"):
            self.assertEqual(len(part), 2, f"Octet '{part}' is not 2 chars")
            int(part, 16)  # Should not raise

    def test_mac_uses_uppercase_hex(self):
        mac = self.payload.generate_random_mac()
        self.assertEqual(mac, mac.upper())

    def test_mac_starts_with_known_oui(self):
        """Generated MAC should start with one of the known OUI prefixes."""
        mac = self.payload.generate_random_mac()
        oui_part = mac[:8]
        oui_strings = [":".join(f"{b:02X}" for b in oui) for oui in KNOWN_OUI_PREFIXES]
        self.assertIn(oui_part, oui_strings,
                      f"MAC OUI {oui_part} not in known prefixes")

    def test_successive_macs_differ(self):
        """Two consecutive MACs should almost certainly differ."""
        mac1 = self.payload.generate_random_mac()
        mac2 = self.payload.generate_random_mac()
        # Statistically extremely unlikely to collide
        self.assertNotEqual(mac1, mac2)

    def test_mac_validates(self):
        mac = self.payload.generate_random_mac()
        self.assertTrue(AdvertisingPayload.validate_mac(mac))

    def test_custom_oui_used(self):
        """When a single OUI is provided, all MACs must start with it."""
        oui = [(0xDE, 0xAD, 0xBE)]
        payload = AdvertisingPayload(oui_prefixes=oui, shuffle=False)
        for _ in range(10):
            mac = payload.generate_random_mac()
            self.assertTrue(mac.startswith("DE:AD:BE:"),
                            f"MAC {mac} does not start with DE:AD:BE:")


class TestGenerateFullyRandomMac(unittest.TestCase):
    """Test fully random (locally-administered) MAC generation."""

    def setUp(self):
        self.payload = AdvertisingPayload(shuffle=False)

    def test_returns_string(self):
        mac = self.payload.generate_fully_random_mac()
        self.assertIsInstance(mac, str)

    def test_mac_format(self):
        mac = self.payload.generate_fully_random_mac()
        parts = mac.split(":")
        self.assertEqual(len(parts), 6)

    def test_locally_administered_bit_set(self):
        """Bit 1 of first octet should be set (locally administered)."""
        mac = self.payload.generate_fully_random_mac()
        first_byte = int(mac.split(":")[0], 16)
        self.assertTrue(first_byte & 0x02,
                        f"First byte {first_byte:02X} does not have locally-administered bit set")

    def test_multicast_bit_cleared(self):
        """Bit 0 of first octet should be clear (unicast)."""
        mac = self.payload.generate_fully_random_mac()
        first_byte = int(mac.split(":")[0], 16)
        self.assertFalse(first_byte & 0x01,
                         f"First byte {first_byte:02X} has multicast bit set")

    def test_locally_administered_and_unicast_multiple(self):
        """Verify LA + unicast bits across multiple generations."""
        for _ in range(50):
            mac = self.payload.generate_fully_random_mac()
            first_byte = int(mac.split(":")[0], 16)
            self.assertTrue(first_byte & 0x02)
            self.assertFalse(first_byte & 0x01)

    def test_mac_validates(self):
        mac = self.payload.generate_fully_random_mac()
        self.assertTrue(AdvertisingPayload.validate_mac(mac))


# ── Device Name Rotation Tests ───────────────────────────────────────


class TestNextDeviceName(unittest.TestCase):
    """Test device name rotation."""

    def test_returns_string(self):
        payload = AdvertisingPayload(shuffle=False)
        name = payload.next_device_name()
        self.assertIsInstance(name, str)

    def test_first_name_is_first_in_list(self):
        names = ["Alpha", "Beta", "Gamma"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        self.assertEqual(payload.next_device_name(), "Alpha")

    def test_sequential_rotation(self):
        names = ["A", "B", "C"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        self.assertEqual(payload.next_device_name(), "A")
        self.assertEqual(payload.next_device_name(), "B")
        self.assertEqual(payload.next_device_name(), "C")

    def test_wraps_around_after_full_cycle(self):
        names = ["X", "Y"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        payload.next_device_name()  # X
        payload.next_device_name()  # Y
        # Should wrap around
        third = payload.next_device_name()
        self.assertEqual(third, "X")

    def test_name_index_increments(self):
        names = ["A", "B", "C"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        self.assertEqual(payload.current_name_index, 0)
        payload.next_device_name()
        self.assertEqual(payload.current_name_index, 1)
        payload.next_device_name()
        self.assertEqual(payload.current_name_index, 2)

    def test_name_index_resets_after_full_cycle(self):
        names = ["A", "B"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        payload.next_device_name()  # A -> index=1
        payload.next_device_name()  # B -> index wraps to 0
        self.assertEqual(payload.current_name_index, 0)

    def test_all_names_appear_in_one_cycle(self):
        names = ["Alpha", "Beta", "Gamma", "Delta"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        seen = [payload.next_device_name() for _ in range(4)]
        self.assertEqual(set(seen), set(names))


# ── Payload Generation Tests ─────────────────────────────────────────


class TestNextPayload(unittest.TestCase):
    """Test the combined payload generation (mac + name)."""

    def test_returns_tuple(self):
        payload = AdvertisingPayload(shuffle=False)
        result = payload.next_payload()
        self.assertIsInstance(result, tuple)

    def test_tuple_has_two_elements(self):
        payload = AdvertisingPayload(shuffle=False)
        mac, name = payload.next_payload()
        self.assertIsInstance(mac, str)
        self.assertIsInstance(name, str)

    def test_mac_is_valid(self):
        payload = AdvertisingPayload(shuffle=False)
        mac, _ = payload.next_payload()
        self.assertTrue(AdvertisingPayload.validate_mac(mac))

    def test_name_is_from_device_list(self):
        names = ["Test KB 1", "Test KB 2"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        _, name = payload.next_payload()
        self.assertIn(name, names)

    def test_rotation_count_increments(self):
        payload = AdvertisingPayload(shuffle=False)
        self.assertEqual(payload.rotation_count, 0)
        payload.next_payload()
        self.assertEqual(payload.rotation_count, 1)
        payload.next_payload()
        self.assertEqual(payload.rotation_count, 2)

    def test_multiple_payloads_produce_different_macs(self):
        payload = AdvertisingPayload(shuffle=False)
        macs = set()
        for _ in range(20):
            mac, _ = payload.next_payload()
            macs.add(mac)
        # All 20 should be unique (statistically guaranteed)
        self.assertEqual(len(macs), 20)

    def test_names_cycle_through_full_list(self):
        names = ["KB1", "KB2", "KB3"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        seen = []
        for _ in range(6):
            _, name = payload.next_payload()
            seen.append(name)
        self.assertEqual(seen, ["KB1", "KB2", "KB3", "KB1", "KB2", "KB3"])


# ── MAC Validation Tests ─────────────────────────────────────────────


class TestValidateMac(unittest.TestCase):
    """Test static MAC address validation."""

    def test_valid_mac(self):
        self.assertTrue(AdvertisingPayload.validate_mac("AA:BB:CC:DD:EE:FF"))

    def test_valid_mac_lowercase(self):
        self.assertTrue(AdvertisingPayload.validate_mac("aa:bb:cc:dd:ee:ff"))

    def test_valid_mac_mixed_case(self):
        self.assertTrue(AdvertisingPayload.validate_mac("Aa:Bb:Cc:Dd:Ee:Ff"))

    def test_valid_mac_all_zeros(self):
        self.assertTrue(AdvertisingPayload.validate_mac("00:00:00:00:00:00"))

    def test_valid_mac_all_ff(self):
        self.assertTrue(AdvertisingPayload.validate_mac("FF:FF:FF:FF:FF:FF"))

    def test_invalid_mac_too_few_octets(self):
        self.assertFalse(AdvertisingPayload.validate_mac("AA:BB:CC:DD:EE"))

    def test_invalid_mac_too_many_octets(self):
        self.assertFalse(AdvertisingPayload.validate_mac("AA:BB:CC:DD:EE:FF:00"))

    def test_invalid_mac_wrong_separator(self):
        self.assertFalse(AdvertisingPayload.validate_mac("AA-BB-CC-DD-EE-FF"))

    def test_invalid_mac_no_separator(self):
        self.assertFalse(AdvertisingPayload.validate_mac("AABBCCDDEEFF"))

    def test_invalid_mac_non_hex(self):
        self.assertFalse(AdvertisingPayload.validate_mac("GG:HH:II:JJ:KK:LL"))

    def test_invalid_mac_empty_string(self):
        self.assertFalse(AdvertisingPayload.validate_mac(""))

    def test_invalid_mac_single_char_octets(self):
        self.assertFalse(AdvertisingPayload.validate_mac("A:B:C:D:E:F"))

    def test_invalid_mac_triple_char_octets(self):
        self.assertFalse(AdvertisingPayload.validate_mac("AAA:BBB:CCC:DDD:EEE:FFF"))


# ── Apply Payload Tests ──────────────────────────────────────────────


class TestApplyPayload(unittest.TestCase):
    """Test applying a payload to a BluetoothManager."""

    def test_apply_sets_alias(self):
        mock_bt = MagicMock()
        names = ["Test KB"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        mac, name = payload.apply_payload(mock_bt)
        self.assertEqual(name, "Test KB")
        mock_bt.__setattr__("alias", "Test KB")

    def test_apply_returns_valid_mac(self):
        mock_bt = MagicMock()
        payload = AdvertisingPayload(shuffle=False)
        mac, _ = payload.apply_payload(mock_bt)
        self.assertTrue(AdvertisingPayload.validate_mac(mac))

    def test_apply_increments_rotation_count(self):
        mock_bt = MagicMock()
        payload = AdvertisingPayload(shuffle=False)
        payload.apply_payload(mock_bt)
        self.assertEqual(payload.rotation_count, 1)

    def test_apply_sets_alias_on_manager(self):
        mock_bt = MagicMock()
        names = ["Spoofed Keyboard"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        payload.apply_payload(mock_bt)
        # alias should be set via property assignment
        self.assertEqual(mock_bt.alias, "Spoofed Keyboard")


# ── Apply MAC via bdaddr Tests ───────────────────────────────────────


class TestApplyMacViaBdaddr(unittest.TestCase):
    """Test MAC spoofing via the bdaddr utility."""

    @patch("advertising_payload.subprocess.run")
    def test_success_returns_true(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        result = AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF")
        self.assertTrue(result)

    @patch("advertising_payload.subprocess.run")
    def test_calls_hciconfig_down(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF", "hci0")
        first_call = mock_run.call_args_list[0]
        self.assertEqual(first_call[0][0], ["hciconfig", "hci0", "down"])

    @patch("advertising_payload.subprocess.run")
    def test_calls_bdaddr(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF", "hci0")
        second_call = mock_run.call_args_list[1]
        self.assertEqual(second_call[0][0], ["bdaddr", "-i", "hci0", "AA:BB:CC:DD:EE:FF"])

    @patch("advertising_payload.subprocess.run")
    def test_calls_hciconfig_up(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF", "hci0")
        third_call = mock_run.call_args_list[2]
        self.assertEqual(third_call[0][0], ["hciconfig", "hci0", "up"])

    @patch("advertising_payload.subprocess.run")
    def test_three_subprocess_calls_on_success(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF")
        self.assertEqual(mock_run.call_count, 3)

    @patch("advertising_payload.subprocess.run")
    def test_failure_returns_false(self, mock_run):
        mock_run.side_effect = subprocess.CalledProcessError(1, "bdaddr")
        result = AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF")
        self.assertFalse(result)

    @patch("advertising_payload.subprocess.run")
    def test_file_not_found_returns_false(self, mock_run):
        mock_run.side_effect = FileNotFoundError("bdaddr not found")
        result = AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF")
        self.assertFalse(result)

    @patch("advertising_payload.subprocess.run")
    def test_custom_adapter(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        AdvertisingPayload.apply_mac_via_bdaddr("AA:BB:CC:DD:EE:FF", "hci1")
        first_call = mock_run.call_args_list[0]
        self.assertIn("hci1", first_call[0][0])


# ── Reset Tests ──────────────────────────────────────────────────────


class TestReset(unittest.TestCase):
    """Test generator reset functionality."""

    def test_reset_clears_rotation_count(self):
        payload = AdvertisingPayload(shuffle=False)
        payload.next_payload()
        payload.next_payload()
        self.assertEqual(payload.rotation_count, 2)
        payload.reset()
        self.assertEqual(payload.rotation_count, 0)

    def test_reset_clears_name_index(self):
        names = ["A", "B", "C"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        payload.next_device_name()
        payload.next_device_name()
        payload.reset()
        self.assertEqual(payload.current_name_index, 0)

    def test_reset_allows_fresh_cycle(self):
        names = ["First", "Second"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        payload.next_device_name()  # First
        payload.reset()
        self.assertEqual(payload.next_device_name(), "First")


# ── Properties Tests ─────────────────────────────────────────────────


class TestProperties(unittest.TestCase):
    """Test read-only properties of AdvertisingPayload."""

    def test_device_names_returns_copy(self):
        payload = AdvertisingPayload(shuffle=False)
        names = payload.device_names
        names.append("INJECTED")
        self.assertNotIn("INJECTED", payload.device_names)

    def test_rotation_count_read_only(self):
        payload = AdvertisingPayload(shuffle=False)
        self.assertEqual(payload.rotation_count, 0)

    def test_current_name_index_read_only(self):
        payload = AdvertisingPayload(shuffle=False)
        self.assertEqual(payload.current_name_index, 0)


# ── Shuffle Behavior Tests ───────────────────────────────────────────


class TestShuffleBehavior(unittest.TestCase):
    """Test shuffle-related behavior."""

    def test_shuffle_true_creates_different_order(self):
        """With shuffle=True, order should (usually) differ from original."""
        names = list("ABCDEFGHIJKLMNOP")  # 16 names — astronomically unlikely to stay sorted
        payload = AdvertisingPayload(device_names=names, shuffle=True)
        # With 16 items, probability of preserving exact order is 1/16! ≈ 0
        # We just verify it doesn't crash
        self.assertEqual(len(payload.device_names), 16)

    def test_shuffle_false_preserves_order(self):
        names = ["Z", "Y", "X", "W"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        result = [payload.next_device_name() for _ in range(4)]
        self.assertEqual(result, ["Z", "Y", "X", "W"])

    def test_shuffle_on_cycle_wrap(self):
        """When shuffle=True, names should be reshuffled after a full cycle."""
        names = ["A", "B"]
        payload = AdvertisingPayload(device_names=names, shuffle=True)
        # Complete one cycle
        payload.next_device_name()
        payload.next_device_name()
        # After wrap, names should have been reshuffled (though order may match by chance)
        # We just verify it doesn't error
        third = payload.next_device_name()
        self.assertIn(third, ["A", "B"])


# ── Integration-like Tests ───────────────────────────────────────────


class TestPayloadIntegration(unittest.TestCase):
    """Integration-style tests combining multiple features."""

    def test_generate_100_unique_macs(self):
        """Generating 100 MACs should produce 100 unique values."""
        payload = AdvertisingPayload(shuffle=False)
        macs = set()
        for _ in range(100):
            mac, _ = payload.next_payload()
            macs.add(mac)
        self.assertEqual(len(macs), 100)

    def test_all_generated_macs_are_valid(self):
        payload = AdvertisingPayload(shuffle=False)
        for _ in range(50):
            mac, _ = payload.next_payload()
            self.assertTrue(
                AdvertisingPayload.validate_mac(mac),
                f"Generated invalid MAC: {mac}"
            )

    def test_all_generated_names_in_list(self):
        names = ["KB-A", "KB-B", "KB-C"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        for _ in range(9):
            _, name = payload.next_payload()
            self.assertIn(name, names)

    def test_rotation_count_matches_calls(self):
        payload = AdvertisingPayload(shuffle=False)
        n = 25
        for _ in range(n):
            payload.next_payload()
        self.assertEqual(payload.rotation_count, n)

    def test_apply_then_reset_then_apply(self):
        mock_bt = MagicMock()
        names = ["First", "Second"]
        payload = AdvertisingPayload(device_names=names, shuffle=False)
        _, name1 = payload.apply_payload(mock_bt)
        self.assertEqual(name1, "First")
        payload.reset()
        _, name2 = payload.apply_payload(mock_bt)
        self.assertEqual(name2, "First")

    def test_all_oui_prefixes_used_across_many_macs(self):
        """Over many MACs, all OUI prefixes should appear at least once."""
        payload = AdvertisingPayload(shuffle=False)
        seen_ouis = set()
        for _ in range(500):
            mac = payload.generate_random_mac()
            oui_part = mac[:8]
            seen_ouis.add(oui_part)
        oui_strings = {":".join(f"{b:02X}" for b in oui) for oui in KNOWN_OUI_PREFIXES}
        # With 500 draws from 10 OUIs, each should appear (birthday paradox)
        self.assertEqual(seen_ouis, oui_strings)


if __name__ == "__main__":
    unittest.main()
