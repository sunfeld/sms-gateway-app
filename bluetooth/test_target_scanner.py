"""
Tests for TargetScanner — BLE scanner for Apple/iOS device identification.

Tests cover:
- Apple device detection via manufacturer data (company ID 0x004C)
- Apple continuity protocol message type parsing
- RSSI filtering
- Device deduplication and update logic
- Scan lifecycle (start, stop, clear, summary)
- Device filtering by type and signal strength
- Edge cases (empty data, no devices, unknown types)
"""

import asyncio
import time
from dataclasses import dataclass
from typing import Optional
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from target_scanner import (
    APPLE_COMPANY_ID,
    APPLE_MSG_TYPES,
    AppleDevice,
    TargetScanner,
)


# ── Helpers / Fixtures ────────────────────────────────────────────────


class FakeAdvertisementData:
    """Minimal stand-in for bleak AdvertisementData."""

    def __init__(self, manufacturer_data: dict, rssi: int = -50):
        self.manufacturer_data = manufacturer_data
        self.rssi = rssi
        self.local_name = None
        self.service_uuids = []
        self.service_data = {}
        self.tx_power = None
        self.platform_data = ()


class FakeBLEDevice:
    """Minimal stand-in for bleak BLEDevice."""

    def __init__(self, address: str, name: Optional[str] = None):
        self.address = address
        self.name = name
        self.details = {}
        self.metadata = {}


@pytest.fixture
def scanner():
    """Return a fresh TargetScanner with default settings."""
    return TargetScanner(scan_duration=5.0)


@pytest.fixture
def apple_adv():
    """Return advertisement data with Apple manufacturer data (AirDrop type)."""
    return FakeAdvertisementData(
        manufacturer_data={APPLE_COMPANY_ID: bytes([0x05, 0x01, 0x02, 0x03])},
        rssi=-45,
    )


@pytest.fixture
def non_apple_adv():
    """Return advertisement data with non-Apple manufacturer data."""
    return FakeAdvertisementData(
        manufacturer_data={0x0006: bytes([0x01, 0x02])},  # Microsoft
        rssi=-60,
    )


@pytest.fixture
def apple_device():
    """Return a fake BLE device."""
    return FakeBLEDevice(address="AA:BB:CC:DD:EE:FF", name="iPhone")


# ── APPLE_COMPANY_ID Constant ─────────────────────────────────────────


class TestAppleCompanyID:
    """Verify the Apple company ID constant."""

    def test_apple_company_id_value(self):
        assert APPLE_COMPANY_ID == 0x004C

    def test_apple_company_id_decimal(self):
        assert APPLE_COMPANY_ID == 76


# ── AppleDevice Dataclass ─────────────────────────────────────────────


class TestAppleDevice:
    """Tests for the AppleDevice data class."""

    def test_create_device(self):
        dev = AppleDevice(
            address="AA:BB:CC:DD:EE:FF",
            name="iPhone",
            rssi=-50,
            apple_data=b"\x05\x01",
        )
        assert dev.address == "AA:BB:CC:DD:EE:FF"
        assert dev.name == "iPhone"
        assert dev.rssi == -50
        assert dev.apple_data == b"\x05\x01"
        assert dev.seen_count == 1

    def test_create_device_no_name(self):
        dev = AppleDevice(
            address="11:22:33:44:55:66",
            name=None,
            rssi=-80,
            apple_data=b"\x0c",
        )
        assert dev.name is None

    def test_to_dict(self):
        dev = AppleDevice(
            address="AA:BB:CC:DD:EE:FF",
            name="iPad",
            rssi=-55,
            apple_data=b"\x0f\xaa\xbb",
            apple_msg_type="Nearby Action",
            apple_msg_type_id=0x0F,
            first_seen=1000.0,
            last_seen=1001.0,
            seen_count=3,
        )
        d = dev.to_dict()
        assert d["address"] == "AA:BB:CC:DD:EE:FF"
        assert d["name"] == "iPad"
        assert d["rssi"] == -55
        assert d["apple_data_hex"] == "0faabb"
        assert d["apple_msg_type"] == "Nearby Action"
        assert d["apple_msg_type_id"] == 0x0F
        assert d["first_seen"] == 1000.0
        assert d["last_seen"] == 1001.0
        assert d["seen_count"] == 3

    def test_to_dict_empty_data(self):
        dev = AppleDevice(
            address="00:00:00:00:00:00",
            name=None,
            rssi=-100,
            apple_data=b"",
        )
        d = dev.to_dict()
        assert d["apple_data_hex"] == ""
        assert d["apple_msg_type"] is None

    def test_default_seen_count(self):
        dev = AppleDevice(
            address="AA:BB:CC:DD:EE:FF",
            name=None,
            rssi=-50,
            apple_data=b"\x10",
        )
        assert dev.seen_count == 1

    def test_default_timestamps_are_set(self):
        before = time.time()
        dev = AppleDevice(
            address="AA:BB:CC:DD:EE:FF",
            name=None,
            rssi=-50,
            apple_data=b"\x10",
        )
        after = time.time()
        assert before <= dev.first_seen <= after
        assert before <= dev.last_seen <= after


# ── TargetScanner Initialization ──────────────────────────────────────


class TestTargetScannerInit:
    """Tests for TargetScanner initialization and properties."""

    def test_default_init(self):
        s = TargetScanner()
        assert s.scan_duration == 10.0
        assert s.min_rssi == -100
        assert s.scan_count == 0
        assert not s.is_scanning
        assert s.discovered_devices == {}

    def test_custom_init(self):
        s = TargetScanner(scan_duration=5.0, min_rssi=-70, adapter="hci1")
        assert s.scan_duration == 5.0
        assert s.min_rssi == -70

    def test_set_scan_duration(self, scanner):
        scanner.scan_duration = 15.0
        assert scanner.scan_duration == 15.0

    def test_set_scan_duration_invalid(self, scanner):
        with pytest.raises(ValueError, match="positive"):
            scanner.scan_duration = 0

    def test_set_scan_duration_negative(self, scanner):
        with pytest.raises(ValueError, match="positive"):
            scanner.scan_duration = -5.0

    def test_set_min_rssi(self, scanner):
        scanner.min_rssi = -60
        assert scanner.min_rssi == -60


# ── is_apple_device ───────────────────────────────────────────────────


class TestIsAppleDevice:
    """Tests for the static is_apple_device method."""

    def test_detects_apple_device(self, apple_adv):
        assert TargetScanner.is_apple_device(apple_adv)

    def test_rejects_non_apple_device(self, non_apple_adv):
        assert not TargetScanner.is_apple_device(non_apple_adv)

    def test_rejects_empty_manufacturer_data(self):
        adv = FakeAdvertisementData(manufacturer_data={}, rssi=-50)
        assert not TargetScanner.is_apple_device(adv)

    def test_detects_apple_among_multiple(self):
        adv = FakeAdvertisementData(
            manufacturer_data={
                0x0006: b"\x01",
                APPLE_COMPANY_ID: b"\x0f\x01",
                0x004D: b"\x02",
            },
            rssi=-50,
        )
        assert TargetScanner.is_apple_device(adv)

    def test_rejects_close_but_wrong_id(self):
        adv = FakeAdvertisementData(
            manufacturer_data={0x004D: b"\x05\x01"},  # 0x4D != 0x4C
            rssi=-50,
        )
        assert not TargetScanner.is_apple_device(adv)

    def test_apple_id_with_empty_data(self):
        adv = FakeAdvertisementData(
            manufacturer_data={APPLE_COMPANY_ID: b""},
            rssi=-50,
        )
        assert TargetScanner.is_apple_device(adv)


# ── parse_apple_msg_type ──────────────────────────────────────────────


class TestParseAppleMsgType:
    """Tests for parsing Apple continuity protocol message types."""

    def test_parse_airdrop(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x05\x01\x02")
        assert type_id == 0x05
        assert type_name == "AirDrop"

    def test_parse_handoff(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x0c\xaa")
        assert type_id == 0x0C
        assert type_name == "Handoff"

    def test_parse_nearby_action(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x0f\x01\x02\x03")
        assert type_id == 0x0F
        assert type_name == "Nearby Action"

    def test_parse_nearby_info(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x10\x05")
        assert type_id == 0x10
        assert type_name == "Nearby Info"

    def test_parse_airpods(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x07\x19")
        assert type_id == 0x07
        assert type_name == "AirPods"

    def test_parse_ibeacon(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x02\x15")
        assert type_id == 0x02
        assert type_name == "iBeacon"

    def test_parse_find_my(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x12\x19\x00")
        assert type_id == 0x12
        assert type_name == "Find My"

    def test_parse_unknown_type(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\xff\x01")
        assert type_id == 0xFF
        assert type_name == "Unknown (0xFF)"

    def test_parse_empty_data(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"")
        assert type_id is None
        assert type_name is None

    def test_parse_single_byte(self):
        type_id, type_name = TargetScanner.parse_apple_msg_type(b"\x0e")
        assert type_id == 0x0E
        assert type_name == "Magic Switch"

    def test_all_known_types_mapped(self):
        """Verify all entries in APPLE_MSG_TYPES are parseable."""
        for type_id, expected_name in APPLE_MSG_TYPES.items():
            data = bytes([type_id, 0x00])
            parsed_id, parsed_name = TargetScanner.parse_apple_msg_type(data)
            assert parsed_id == type_id
            assert parsed_name == expected_name


# ── Detection Callback ────────────────────────────────────────────────


class TestDetectionCallback:
    """Tests for the internal _detection_callback method."""

    def test_adds_apple_device(self, scanner, apple_device, apple_adv):
        scanner._detection_callback(apple_device, apple_adv)
        assert apple_device.address in scanner._discovered
        dev = scanner._discovered[apple_device.address]
        assert dev.address == "AA:BB:CC:DD:EE:FF"
        assert dev.name == "iPhone"
        assert dev.rssi == -45
        assert dev.apple_msg_type == "AirDrop"

    def test_ignores_non_apple_device(self, scanner, apple_device, non_apple_adv):
        scanner._detection_callback(apple_device, non_apple_adv)
        assert len(scanner._discovered) == 0

    def test_rssi_filter(self, scanner, apple_device, apple_adv):
        scanner.min_rssi = -40  # Device is at -45, should be filtered
        scanner._detection_callback(apple_device, apple_adv)
        assert len(scanner._discovered) == 0

    def test_rssi_filter_passes(self, scanner, apple_device, apple_adv):
        scanner.min_rssi = -50  # Device is at -45, should pass
        scanner._detection_callback(apple_device, apple_adv)
        assert len(scanner._discovered) == 1

    def test_updates_existing_device(self, scanner, apple_device, apple_adv):
        scanner._detection_callback(apple_device, apple_adv)
        assert scanner._discovered[apple_device.address].seen_count == 1

        # Second detection with updated RSSI
        apple_adv.rssi = -30
        scanner._detection_callback(apple_device, apple_adv)
        dev = scanner._discovered[apple_device.address]
        assert dev.seen_count == 2
        assert dev.rssi == -30

    def test_updates_name_on_re_detection(self, scanner, apple_adv):
        device1 = FakeBLEDevice(address="AA:BB:CC:DD:EE:FF", name=None)
        scanner._detection_callback(device1, apple_adv)
        assert scanner._discovered["AA:BB:CC:DD:EE:FF"].name is None

        device2 = FakeBLEDevice(address="AA:BB:CC:DD:EE:FF", name="John's iPhone")
        scanner._detection_callback(device2, apple_adv)
        assert scanner._discovered["AA:BB:CC:DD:EE:FF"].name == "John's iPhone"

    def test_does_not_overwrite_name_with_none(self, scanner, apple_adv):
        device1 = FakeBLEDevice(address="AA:BB:CC:DD:EE:FF", name="iPhone")
        scanner._detection_callback(device1, apple_adv)

        device2 = FakeBLEDevice(address="AA:BB:CC:DD:EE:FF", name=None)
        scanner._detection_callback(device2, apple_adv)
        assert scanner._discovered["AA:BB:CC:DD:EE:FF"].name == "iPhone"

    def test_multiple_different_devices(self, scanner, apple_adv):
        dev1 = FakeBLEDevice(address="11:11:11:11:11:11", name="iPhone 1")
        dev2 = FakeBLEDevice(address="22:22:22:22:22:22", name="iPad")
        dev3 = FakeBLEDevice(address="33:33:33:33:33:33", name="MacBook")

        scanner._detection_callback(dev1, apple_adv)
        scanner._detection_callback(dev2, apple_adv)
        scanner._detection_callback(dev3, apple_adv)
        assert len(scanner._discovered) == 3

    def test_handles_none_rssi(self, scanner, apple_device):
        adv = FakeAdvertisementData(
            manufacturer_data={APPLE_COMPANY_ID: b"\x10\x01"},
            rssi=None,
        )
        # Override rssi to None after construction
        adv.rssi = None
        scanner.min_rssi = -128  # Below the -127 default for None
        scanner._detection_callback(apple_device, adv)
        assert apple_device.address in scanner._discovered
        assert scanner._discovered[apple_device.address].rssi == -127

    def test_empty_apple_data_no_crash(self, scanner, apple_device):
        adv = FakeAdvertisementData(
            manufacturer_data={APPLE_COMPANY_ID: b""},
            rssi=-50,
        )
        scanner._detection_callback(apple_device, adv)
        dev = scanner._discovered[apple_device.address]
        assert dev.apple_msg_type is None
        assert dev.apple_msg_type_id is None


# ── Scan Method ───────────────────────────────────────────────────────


class TestScan:
    """Tests for the async scan() method."""

    @pytest.mark.asyncio
    async def test_scan_returns_list(self, scanner):
        with patch("target_scanner.BleakScanner") as MockScanner:
            mock_instance = AsyncMock()
            MockScanner.return_value = mock_instance
            with patch("target_scanner.asyncio.sleep", new_callable=AsyncMock):
                result = await scanner.scan()
        assert isinstance(result, list)

    @pytest.mark.asyncio
    async def test_scan_increments_count(self, scanner):
        assert scanner.scan_count == 0
        with patch("target_scanner.BleakScanner") as MockScanner:
            MockScanner.return_value = AsyncMock()
            with patch("target_scanner.asyncio.sleep", new_callable=AsyncMock):
                await scanner.scan()
        assert scanner.scan_count == 1

    @pytest.mark.asyncio
    async def test_scan_sets_scanning_flag(self, scanner):
        scanning_during = []

        async def capture_state(_):
            scanning_during.append(scanner.is_scanning)

        with patch("target_scanner.BleakScanner") as MockScanner:
            MockScanner.return_value = AsyncMock()
            with patch("target_scanner.asyncio.sleep", side_effect=capture_state):
                await scanner.scan()

        assert scanning_during == [True]
        assert not scanner.is_scanning

    @pytest.mark.asyncio
    async def test_scan_clears_flag_on_error(self, scanner):
        with patch("target_scanner.BleakScanner") as MockScanner:
            mock_instance = AsyncMock()
            mock_instance.start.side_effect = RuntimeError("Bluetooth off")
            MockScanner.return_value = mock_instance
            with pytest.raises(RuntimeError):
                await scanner.scan()
        assert not scanner.is_scanning
        assert scanner.scan_count == 1  # Count still incremented in finally

    @pytest.mark.asyncio
    async def test_scan_returns_sorted_by_rssi(self, scanner):
        # Pre-populate with devices at different RSSI
        scanner._discovered = {
            "11:11:11:11:11:11": AppleDevice(
                address="11:11:11:11:11:11", name="Weak", rssi=-90,
                apple_data=b"\x10", apple_msg_type="Nearby Info",
            ),
            "22:22:22:22:22:22": AppleDevice(
                address="22:22:22:22:22:22", name="Strong", rssi=-30,
                apple_data=b"\x10", apple_msg_type="Nearby Info",
            ),
            "33:33:33:33:33:33": AppleDevice(
                address="33:33:33:33:33:33", name="Medium", rssi=-60,
                apple_data=b"\x10", apple_msg_type="Nearby Info",
            ),
        }
        with patch("target_scanner.BleakScanner") as MockScanner:
            MockScanner.return_value = AsyncMock()
            with patch("target_scanner.asyncio.sleep", new_callable=AsyncMock):
                result = await scanner.scan()

        assert result[0].name == "Strong"
        assert result[1].name == "Medium"
        assert result[2].name == "Weak"

    @pytest.mark.asyncio
    async def test_scan_once_clears_first(self, scanner):
        scanner._discovered["old"] = AppleDevice(
            address="old", name="old", rssi=-80, apple_data=b"\x10"
        )
        with patch("target_scanner.BleakScanner") as MockScanner:
            MockScanner.return_value = AsyncMock()
            with patch("target_scanner.asyncio.sleep", new_callable=AsyncMock):
                result = await scanner.scan_once()
        assert "old" not in scanner._discovered

    @pytest.mark.asyncio
    async def test_scan_passes_adapter(self):
        s = TargetScanner(scan_duration=1.0, adapter="hci1")
        with patch("target_scanner.BleakScanner") as MockScanner:
            MockScanner.return_value = AsyncMock()
            with patch("target_scanner.asyncio.sleep", new_callable=AsyncMock):
                await s.scan()
            MockScanner.assert_called_once()
            call_kwargs = MockScanner.call_args.kwargs
            assert call_kwargs.get("adapter") == "hci1"


# ── Filtering Methods ─────────────────────────────────────────────────


class TestFilteringMethods:
    """Tests for device filtering methods."""

    @pytest.fixture
    def populated_scanner(self):
        s = TargetScanner()
        s._discovered = {
            "11:11:11:11:11:11": AppleDevice(
                address="11:11:11:11:11:11", name="iPhone",
                rssi=-40, apple_data=b"\x05", apple_msg_type="AirDrop",
            ),
            "22:22:22:22:22:22": AppleDevice(
                address="22:22:22:22:22:22", name="iPad",
                rssi=-70, apple_data=b"\x0c", apple_msg_type="Handoff",
            ),
            "33:33:33:33:33:33": AppleDevice(
                address="33:33:33:33:33:33", name="MacBook",
                rssi=-55, apple_data=b"\x05", apple_msg_type="AirDrop",
            ),
            "44:44:44:44:44:44": AppleDevice(
                address="44:44:44:44:44:44", name="AirPods",
                rssi=-30, apple_data=b"\x07", apple_msg_type="AirPods",
            ),
        }
        return s

    def test_get_devices_by_type_airdrop(self, populated_scanner):
        result = populated_scanner.get_devices_by_type("AirDrop")
        assert len(result) == 2
        addresses = {d.address for d in result}
        assert "11:11:11:11:11:11" in addresses
        assert "33:33:33:33:33:33" in addresses

    def test_get_devices_by_type_handoff(self, populated_scanner):
        result = populated_scanner.get_devices_by_type("Handoff")
        assert len(result) == 1
        assert result[0].name == "iPad"

    def test_get_devices_by_type_no_match(self, populated_scanner):
        result = populated_scanner.get_devices_by_type("Find My")
        assert len(result) == 0

    def test_get_devices_above_rssi(self, populated_scanner):
        result = populated_scanner.get_devices_above_rssi(-50)
        assert len(result) == 2  # -40 and -30
        addresses = {d.address for d in result}
        assert "11:11:11:11:11:11" in addresses
        assert "44:44:44:44:44:44" in addresses

    def test_get_devices_above_rssi_none(self, populated_scanner):
        result = populated_scanner.get_devices_above_rssi(-10)
        assert len(result) == 0

    def test_get_devices_above_rssi_all(self, populated_scanner):
        result = populated_scanner.get_devices_above_rssi(-100)
        assert len(result) == 4

    def test_get_strongest_device(self, populated_scanner):
        strongest = populated_scanner.get_strongest_device()
        assert strongest.address == "44:44:44:44:44:44"
        assert strongest.rssi == -30

    def test_get_strongest_device_empty(self, scanner):
        assert scanner.get_strongest_device() is None

    def test_get_target_addresses(self, populated_scanner):
        addresses = populated_scanner.get_target_addresses()
        assert len(addresses) == 4
        assert "11:11:11:11:11:11" in addresses


# ── Clear and Summary ─────────────────────────────────────────────────


class TestClearAndSummary:
    """Tests for clear() and summary() methods."""

    def test_clear(self, scanner):
        scanner._discovered["test"] = AppleDevice(
            address="test", name="t", rssi=-50, apple_data=b"\x10"
        )
        scanner.clear()
        assert len(scanner._discovered) == 0

    def test_summary_empty(self, scanner):
        s = scanner.summary()
        assert s["total_devices"] == 0
        assert s["scan_count"] == 0
        assert s["is_scanning"] is False
        assert s["type_breakdown"] == {}
        assert s["devices"] == []

    def test_summary_with_devices(self):
        sc = TargetScanner(scan_duration=5.0, min_rssi=-80)
        sc._discovered = {
            "11:11:11:11:11:11": AppleDevice(
                address="11:11:11:11:11:11", name="iPhone",
                rssi=-40, apple_data=b"\x05", apple_msg_type="AirDrop",
            ),
            "22:22:22:22:22:22": AppleDevice(
                address="22:22:22:22:22:22", name="iPad",
                rssi=-50, apple_data=b"\x05", apple_msg_type="AirDrop",
            ),
            "33:33:33:33:33:33": AppleDevice(
                address="33:33:33:33:33:33", name="Mac",
                rssi=-60, apple_data=b"\x0c", apple_msg_type="Handoff",
            ),
        }
        sc._scan_count = 3

        s = sc.summary()
        assert s["total_devices"] == 3
        assert s["scan_count"] == 3
        assert s["min_rssi"] == -80
        assert s["scan_duration"] == 5.0
        assert s["type_breakdown"]["AirDrop"] == 2
        assert s["type_breakdown"]["Handoff"] == 1
        assert len(s["devices"]) == 3

    def test_summary_unknown_type_counted(self):
        sc = TargetScanner()
        sc._discovered["a"] = AppleDevice(
            address="a", name=None, rssi=-50,
            apple_data=b"\xff", apple_msg_type=None,
        )
        s = sc.summary()
        assert s["type_breakdown"]["Unknown"] == 1

    def test_discovered_devices_returns_copy(self, scanner):
        scanner._discovered["x"] = AppleDevice(
            address="x", name=None, rssi=-50, apple_data=b"\x10"
        )
        copy = scanner.discovered_devices
        copy["y"] = "should not affect original"
        assert "y" not in scanner._discovered


# ── APPLE_MSG_TYPES Dict ──────────────────────────────────────────────


class TestAppleMsgTypes:
    """Verify the APPLE_MSG_TYPES dictionary."""

    def test_contains_airdrop(self):
        assert 0x05 in APPLE_MSG_TYPES
        assert APPLE_MSG_TYPES[0x05] == "AirDrop"

    def test_contains_handoff(self):
        assert 0x0C in APPLE_MSG_TYPES
        assert APPLE_MSG_TYPES[0x0C] == "Handoff"

    def test_contains_nearby_action(self):
        assert 0x0F in APPLE_MSG_TYPES
        assert APPLE_MSG_TYPES[0x0F] == "Nearby Action"

    def test_contains_find_my(self):
        assert 0x12 in APPLE_MSG_TYPES
        assert APPLE_MSG_TYPES[0x12] == "Find My"

    def test_contains_ibeacon(self):
        assert 0x02 in APPLE_MSG_TYPES
        assert APPLE_MSG_TYPES[0x02] == "iBeacon"

    def test_has_expected_count(self):
        assert len(APPLE_MSG_TYPES) == 10
