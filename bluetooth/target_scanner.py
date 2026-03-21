"""
TargetScanner — BLE scanner to identify nearby iOS devices.

Uses the `bleak` library to perform BLE (Bluetooth Low Energy) scanning and
identifies Apple/iOS devices by detecting Apple-specific manufacturer data
with company ID 0x004C (76 decimal) in advertisement packets.

Apple devices broadcast various BLE advertisement types including:
- AirDrop (0x05)
- Handoff/Continuity (0x0C)
- Nearby Action (0x0F)
- AirPods proximity (0x07)
- iBeacon (0x02, 0x15 length)
"""

import asyncio
import logging
import time
from dataclasses import dataclass, field
from typing import Optional

from bleak import BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.scanner import AdvertisementData

logger = logging.getLogger(__name__)

# Apple's Bluetooth SIG company identifier
APPLE_COMPANY_ID = 0x004C

# Known Apple continuity protocol message types (first byte of manufacturer data)
APPLE_MSG_TYPES = {
    0x02: "iBeacon",
    0x05: "AirDrop",
    0x07: "AirPods",
    0x09: "AirPlay Target",
    0x0A: "AirPlay Source",
    0x0C: "Handoff",
    0x0E: "Magic Switch",
    0x0F: "Nearby Action",
    0x10: "Nearby Info",
    0x12: "Find My",
}


@dataclass
class AppleDevice:
    """Represents a detected Apple/iOS device."""

    address: str
    name: Optional[str]
    rssi: int
    apple_data: bytes
    apple_msg_type: Optional[str] = None
    apple_msg_type_id: Optional[int] = None
    first_seen: float = field(default_factory=time.time)
    last_seen: float = field(default_factory=time.time)
    seen_count: int = 1

    def to_dict(self) -> dict:
        """Serialize to dictionary."""
        return {
            "address": self.address,
            "name": self.name,
            "rssi": self.rssi,
            "apple_data_hex": self.apple_data.hex(),
            "apple_msg_type": self.apple_msg_type,
            "apple_msg_type_id": self.apple_msg_type_id,
            "first_seen": self.first_seen,
            "last_seen": self.last_seen,
            "seen_count": self.seen_count,
        }


class TargetScanner:
    """
    BLE scanner that identifies nearby iOS/Apple devices by detecting
    Apple-specific manufacturer data (company ID 0x004C) in BLE advertisements.

    Usage:
        scanner = TargetScanner(scan_duration=10.0)
        devices = await scanner.scan()
        for dev in devices:
            print(f"{dev.address} — {dev.apple_msg_type} (RSSI: {dev.rssi})")
    """

    def __init__(
        self,
        scan_duration: float = 10.0,
        min_rssi: int = -100,
        adapter: Optional[str] = None,
    ):
        """
        Initialize the TargetScanner.

        Args:
            scan_duration: How long to scan in seconds (default 10s).
            min_rssi: Minimum RSSI threshold; devices weaker than this
                      are ignored (default -100, effectively no filter).
            adapter: Bluetooth adapter to use (e.g., "hci0"). None for default.
        """
        self._scan_duration = scan_duration
        self._min_rssi = min_rssi
        self._adapter = adapter
        self._discovered: dict[str, AppleDevice] = {}
        self._scan_count = 0
        self._is_scanning = False

    @property
    def scan_duration(self) -> float:
        """Return the configured scan duration in seconds."""
        return self._scan_duration

    @scan_duration.setter
    def scan_duration(self, value: float) -> None:
        """Set the scan duration. Must be positive."""
        if value <= 0:
            raise ValueError("scan_duration must be positive")
        self._scan_duration = value

    @property
    def min_rssi(self) -> int:
        """Return the minimum RSSI threshold."""
        return self._min_rssi

    @min_rssi.setter
    def min_rssi(self, value: int) -> None:
        """Set the minimum RSSI threshold."""
        self._min_rssi = value

    @property
    def discovered_devices(self) -> dict[str, AppleDevice]:
        """Return the dict of discovered Apple devices keyed by address."""
        return dict(self._discovered)

    @property
    def scan_count(self) -> int:
        """Return the number of completed scans."""
        return self._scan_count

    @property
    def is_scanning(self) -> bool:
        """Return whether a scan is currently in progress."""
        return self._is_scanning

    @staticmethod
    def is_apple_device(advertisement_data: AdvertisementData) -> bool:
        """
        Check if an advertisement contains Apple manufacturer data.

        Args:
            advertisement_data: BLE advertisement data from bleak.

        Returns:
            True if Apple company ID (0x004C) is present in manufacturer data.
        """
        return APPLE_COMPANY_ID in advertisement_data.manufacturer_data

    @staticmethod
    def parse_apple_msg_type(data: bytes) -> tuple[Optional[int], Optional[str]]:
        """
        Parse the Apple continuity protocol message type from manufacturer data.

        The first byte of Apple's manufacturer data typically indicates the
        message type (AirDrop, Handoff, Nearby, etc.).

        Args:
            data: Raw manufacturer data bytes (after company ID).

        Returns:
            Tuple of (type_id, type_name). Both None if data is empty.
        """
        if not data:
            return None, None
        type_id = data[0]
        type_name = APPLE_MSG_TYPES.get(type_id, f"Unknown (0x{type_id:02X})")
        return type_id, type_name

    def _detection_callback(
        self, device: BLEDevice, advertisement_data: AdvertisementData
    ) -> None:
        """
        Callback invoked by BleakScanner for each detected BLE advertisement.

        Filters for Apple devices and updates the discovered devices dict.
        """
        if not self.is_apple_device(advertisement_data):
            return

        rssi = advertisement_data.rssi if advertisement_data.rssi is not None else -127
        if rssi < self._min_rssi:
            return

        apple_data = advertisement_data.manufacturer_data[APPLE_COMPANY_ID]
        msg_type_id, msg_type_name = self.parse_apple_msg_type(apple_data)

        now = time.time()

        if device.address in self._discovered:
            existing = self._discovered[device.address]
            existing.rssi = rssi
            existing.apple_data = apple_data
            existing.apple_msg_type = msg_type_name
            existing.apple_msg_type_id = msg_type_id
            existing.last_seen = now
            existing.seen_count += 1
            if device.name:
                existing.name = device.name
        else:
            self._discovered[device.address] = AppleDevice(
                address=device.address,
                name=device.name,
                rssi=rssi,
                apple_data=apple_data,
                apple_msg_type=msg_type_name,
                apple_msg_type_id=msg_type_id,
                first_seen=now,
                last_seen=now,
            )
            logger.info(
                "New Apple device: %s (name=%s, type=%s, RSSI=%d)",
                device.address,
                device.name,
                msg_type_name,
                rssi,
            )

    async def scan(self) -> list[AppleDevice]:
        """
        Perform a BLE scan and return detected Apple/iOS devices.

        Each scan updates the internal discovered devices dict, merging
        new detections with previously seen devices.

        Returns:
            List of AppleDevice instances detected during this scan,
            sorted by RSSI (strongest first).
        """
        self._is_scanning = True
        scan_start_count = len(self._discovered)

        try:
            scanner_kwargs = {
                "detection_callback": self._detection_callback,
            }
            if self._adapter:
                scanner_kwargs["adapter"] = self._adapter

            scanner = BleakScanner(**scanner_kwargs)
            await scanner.start()
            await asyncio.sleep(self._scan_duration)
            await scanner.stop()
        finally:
            self._is_scanning = False
            self._scan_count += 1

        new_count = len(self._discovered) - scan_start_count
        logger.info(
            "Scan #%d complete: %d Apple device(s) found (%d new)",
            self._scan_count,
            len(self._discovered),
            new_count,
        )

        return sorted(
            self._discovered.values(),
            key=lambda d: d.rssi,
            reverse=True,
        )

    async def scan_once(self) -> list[AppleDevice]:
        """
        Perform a single scan with a clean state.

        Clears previously discovered devices before scanning.

        Returns:
            List of AppleDevice instances detected, sorted by RSSI.
        """
        self.clear()
        return await self.scan()

    def get_devices_by_type(self, msg_type: str) -> list[AppleDevice]:
        """
        Filter discovered devices by Apple message type.

        Args:
            msg_type: Apple message type name (e.g., "AirDrop", "Handoff").

        Returns:
            List of matching AppleDevice instances.
        """
        return [
            d for d in self._discovered.values()
            if d.apple_msg_type == msg_type
        ]

    def get_devices_above_rssi(self, min_rssi: int) -> list[AppleDevice]:
        """
        Filter discovered devices by minimum RSSI.

        Args:
            min_rssi: Minimum signal strength threshold.

        Returns:
            List of AppleDevice instances with RSSI >= min_rssi.
        """
        return [
            d for d in self._discovered.values()
            if d.rssi >= min_rssi
        ]

    def get_strongest_device(self) -> Optional[AppleDevice]:
        """
        Return the device with the strongest RSSI signal.

        Returns:
            The AppleDevice with highest RSSI, or None if no devices found.
        """
        if not self._discovered:
            return None
        return max(self._discovered.values(), key=lambda d: d.rssi)

    def get_target_addresses(self) -> list[str]:
        """
        Return a list of all discovered Apple device MAC addresses.

        Useful for passing to downstream modules like the attack_orchestrator.

        Returns:
            List of MAC address strings.
        """
        return list(self._discovered.keys())

    def clear(self) -> None:
        """Clear all discovered devices."""
        self._discovered.clear()
        logger.info("Cleared discovered devices")

    def summary(self) -> dict:
        """
        Return a summary of the current scan state.

        Returns:
            Dict with scan statistics and device breakdown.
        """
        type_counts: dict[str, int] = {}
        for device in self._discovered.values():
            t = device.apple_msg_type or "Unknown"
            type_counts[t] = type_counts.get(t, 0) + 1

        return {
            "total_devices": len(self._discovered),
            "scan_count": self._scan_count,
            "is_scanning": self._is_scanning,
            "min_rssi": self._min_rssi,
            "scan_duration": self._scan_duration,
            "type_breakdown": type_counts,
            "devices": [d.to_dict() for d in self._discovered.values()],
        }
