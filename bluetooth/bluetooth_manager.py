"""
Bluetooth Manager — D-Bus interface to the system BlueZ Bluetooth adapter.

Provides adapter management (power, discoverable, pairable), device discovery,
and a foundation for profile registration and pairing operations used by
downstream modules (attack_orchestrator, TargetScanner, etc.).
"""

import dbus
import dbus.mainloop.glib
import logging
import subprocess
from typing import Optional

logger = logging.getLogger(__name__)

# BlueZ D-Bus constants
BLUEZ_SERVICE = "org.bluez"
BLUEZ_ADAPTER_IFACE = "org.bluez.Adapter1"
BLUEZ_DEVICE_IFACE = "org.bluez.Device1"
BLUEZ_PROFILE_MANAGER_IFACE = "org.bluez.ProfileManager1"
BLUEZ_AGENT_MANAGER_IFACE = "org.bluez.AgentManager1"
DBUS_PROPERTIES_IFACE = "org.freedesktop.DBus.Properties"
DBUS_OBJECT_MANAGER_IFACE = "org.freedesktop.DBus.ObjectManager"

# Bluetooth Class of Device (CoD) — Peripheral/Keyboard
# 0x000540 = Major: Peripheral (0x05), Minor: Keyboard (0x40)
DEVICE_CLASS_PERIPHERAL_KEYBOARD = 0x000540
DEVICE_CLASS_MAJOR_PERIPHERAL = 0x05
DEVICE_CLASS_MINOR_KEYBOARD = 0x40

# SDP record XML for broadcasting as a Peripheral/Keyboard device.
# Declares HID service UUID 0x1124, L2CAP + HIDP protocol stack,
# and HIDDeviceSubclass 0x40 (Keyboard) matching CoD 0x000540.
KEYBOARD_SDP_RECORD_XML = """<?xml version="1.0" encoding="UTF-8" ?>
<record>
  <!-- ServiceClassIDList: HID UUID -->
  <attribute id="0x0001">
    <sequence>
      <uuid value="0x1124" />
    </sequence>
  </attribute>
  <!-- ProtocolDescriptorList: L2CAP (PSM 0x0011) + HIDP -->
  <attribute id="0x0004">
    <sequence>
      <sequence>
        <uuid value="0x0100" />
        <uint16 value="0x0011" />
      </sequence>
      <sequence>
        <uuid value="0x0011" />
      </sequence>
    </sequence>
  </attribute>
  <!-- BrowseGroupList: PublicBrowseRoot -->
  <attribute id="0x0005">
    <sequence>
      <uuid value="0x1002" />
    </sequence>
  </attribute>
  <!-- LanguageBaseAttributeIDList -->
  <attribute id="0x0006">
    <sequence>
      <uint16 value="0x656e" />
      <uint16 value="0x006a" />
      <uint16 value="0x0100" />
    </sequence>
  </attribute>
  <!-- BluetoothProfileDescriptorList: HID v1.0 -->
  <attribute id="0x0009">
    <sequence>
      <sequence>
        <uuid value="0x1124" />
        <uint16 value="0x0100" />
      </sequence>
    </sequence>
  </attribute>
  <!-- AdditionalProtocolDescriptorLists: L2CAP (PSM 0x0013) + HIDP -->
  <attribute id="0x000d">
    <sequence>
      <sequence>
        <sequence>
          <uuid value="0x0100" />
          <uint16 value="0x0013" />
        </sequence>
        <sequence>
          <uuid value="0x0011" />
        </sequence>
      </sequence>
    </sequence>
  </attribute>
  <!-- ServiceName -->
  <attribute id="0x0100">
    <text value="Bluetooth Keyboard" />
  </attribute>
  <!-- ServiceDescription -->
  <attribute id="0x0101">
    <text value="Bluetooth HID Keyboard" />
  </attribute>
  <!-- ProviderName -->
  <attribute id="0x0102">
    <text value="SMS Gateway" />
  </attribute>
  <!-- HIDParserVersion -->
  <attribute id="0x0201">
    <uint16 value="0x0111" />
  </attribute>
  <!-- HIDDeviceSubclass: 0x40 = Keyboard (matches CoD minor class) -->
  <attribute id="0x0202">
    <uint8 value="0x40" />
  </attribute>
  <!-- HIDCountryCode: 0x00 = Not localized -->
  <attribute id="0x0203">
    <uint8 value="0x00" />
  </attribute>
  <!-- HIDVirtualCable -->
  <attribute id="0x0204">
    <boolean value="true" />
  </attribute>
  <!-- HIDReconnectInitiate -->
  <attribute id="0x0205">
    <boolean value="true" />
  </attribute>
  <!-- HIDDescriptorList: standard keyboard HID descriptor -->
  <attribute id="0x0206">
    <sequence>
      <sequence>
        <uint8 value="0x22" />
        <text encoding="hex" value="05010906a101850175019508050719e029e715002501810295017508810395057501050819012905910295017503910395067508150025650507190029658100c0" />
      </sequence>
    </sequence>
  </attribute>
  <!-- HIDLANGIDBaseList -->
  <attribute id="0x0207">
    <sequence>
      <sequence>
        <uint16 value="0x0409" />
        <uint16 value="0x0100" />
      </sequence>
    </sequence>
  </attribute>
  <!-- HIDBootDevice: true (supports boot protocol) -->
  <attribute id="0x020e">
    <boolean value="true" />
  </attribute>
</record>
"""


class BluetoothManager:
    """Manages the system Bluetooth adapter via D-Bus/BlueZ."""

    def __init__(self, adapter_name: str = "hci0"):
        """
        Initialize the Bluetooth manager.

        Args:
            adapter_name: Name of the Bluetooth adapter (default: hci0).
        """
        self._adapter_name = adapter_name
        self._adapter_path = f"/org/bluez/{adapter_name}"
        self._bus: Optional[dbus.SystemBus] = None
        self._adapter: Optional[dbus.Interface] = None
        self._adapter_props: Optional[dbus.Interface] = None
        self._profile_manager: Optional[dbus.Interface] = None

        self._init_dbus()

    def _init_dbus(self) -> None:
        """Initialize D-Bus connection and adapter interfaces."""
        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
        self._bus = dbus.SystemBus()

        try:
            adapter_obj = self._bus.get_object(BLUEZ_SERVICE, self._adapter_path)
            self._adapter = dbus.Interface(adapter_obj, BLUEZ_ADAPTER_IFACE)
            self._adapter_props = dbus.Interface(adapter_obj, DBUS_PROPERTIES_IFACE)
            logger.info("Connected to BlueZ adapter at %s", self._adapter_path)
        except dbus.exceptions.DBusException as e:
            logger.error("Failed to connect to adapter %s: %s", self._adapter_name, e)
            raise

    @property
    def bus(self) -> dbus.SystemBus:
        """Return the D-Bus system bus."""
        return self._bus

    @property
    def adapter_path(self) -> str:
        """Return the D-Bus object path of the adapter."""
        return self._adapter_path

    # ── Adapter Properties ─────────────────────────────────────────────

    def get_adapter_property(self, prop: str):
        """Get a single property from the adapter."""
        return self._adapter_props.Get(BLUEZ_ADAPTER_IFACE, prop)

    def set_adapter_property(self, prop: str, value) -> None:
        """Set a single property on the adapter."""
        self._adapter_props.Set(BLUEZ_ADAPTER_IFACE, prop, value)

    @property
    def address(self) -> str:
        """Return the adapter's Bluetooth MAC address."""
        return str(self.get_adapter_property("Address"))

    @property
    def name(self) -> str:
        """Return the adapter's friendly name."""
        return str(self.get_adapter_property("Name"))

    @property
    def powered(self) -> bool:
        """Return whether the adapter is powered on."""
        return bool(self.get_adapter_property("Powered"))

    @powered.setter
    def powered(self, value: bool) -> None:
        """Power the adapter on or off."""
        self.set_adapter_property("Powered", dbus.Boolean(value))
        logger.info("Adapter %s powered %s", self._adapter_name, "on" if value else "off")

    @property
    def discoverable(self) -> bool:
        """Return whether the adapter is discoverable."""
        return bool(self.get_adapter_property("Discoverable"))

    @discoverable.setter
    def discoverable(self, value: bool) -> None:
        """Set the adapter discoverable state."""
        self.set_adapter_property("Discoverable", dbus.Boolean(value))
        logger.info("Adapter %s discoverable: %s", self._adapter_name, value)

    @property
    def pairable(self) -> bool:
        """Return whether the adapter is pairable."""
        return bool(self.get_adapter_property("Pairable"))

    @pairable.setter
    def pairable(self, value: bool) -> None:
        """Set the adapter pairable state."""
        self.set_adapter_property("Pairable", dbus.Boolean(value))
        logger.info("Adapter %s pairable: %s", self._adapter_name, value)

    @property
    def alias(self) -> str:
        """Return the adapter's advertised alias (friendly name)."""
        return str(self.get_adapter_property("Alias"))

    @alias.setter
    def alias(self, value: str) -> None:
        """Set the adapter's advertised alias."""
        self.set_adapter_property("Alias", dbus.String(value))
        logger.info("Adapter alias set to '%s'", value)

    @property
    def discoverable_timeout(self) -> int:
        """Return the discoverable timeout in seconds (0 = infinite)."""
        return int(self.get_adapter_property("DiscoverableTimeout"))

    @discoverable_timeout.setter
    def discoverable_timeout(self, seconds: int) -> None:
        """Set the discoverable timeout. 0 means stay discoverable indefinitely."""
        self.set_adapter_property("DiscoverableTimeout", dbus.UInt32(seconds))

    # ── Device Discovery ───────────────────────────────────────────────

    def start_discovery(self) -> None:
        """Start scanning for nearby Bluetooth devices."""
        self._adapter.StartDiscovery()
        logger.info("Discovery started on %s", self._adapter_name)

    def stop_discovery(self) -> None:
        """Stop scanning for nearby Bluetooth devices."""
        try:
            self._adapter.StopDiscovery()
            logger.info("Discovery stopped on %s", self._adapter_name)
        except dbus.exceptions.DBusException:
            pass  # Not discovering — safe to ignore

    def get_discovered_devices(self) -> list[dict]:
        """
        Return a list of discovered Bluetooth devices.

        Each device is a dict with keys: address, name, paired, connected, rssi.
        """
        obj_manager = dbus.Interface(
            self._bus.get_object(BLUEZ_SERVICE, "/"),
            DBUS_OBJECT_MANAGER_IFACE,
        )
        managed_objects = obj_manager.GetManagedObjects()

        devices = []
        for path, interfaces in managed_objects.items():
            if BLUEZ_DEVICE_IFACE not in interfaces:
                continue
            if not str(path).startswith(self._adapter_path):
                continue

            props = interfaces[BLUEZ_DEVICE_IFACE]
            devices.append({
                "path": str(path),
                "address": str(props.get("Address", "")),
                "name": str(props.get("Name", props.get("Alias", "Unknown"))),
                "paired": bool(props.get("Paired", False)),
                "connected": bool(props.get("Connected", False)),
                "rssi": int(props.get("RSSI", 0)),
                "uuids": [str(u) for u in props.get("UUIDs", [])],
                "manufacturer_data": dict(props.get("ManufacturerData", {})),
            })

        return devices

    def remove_device(self, device_path: str) -> None:
        """Remove a cached device from the adapter."""
        self._adapter.RemoveDevice(dbus.ObjectPath(device_path))
        logger.info("Removed device %s", device_path)

    # ── Profile Manager ────────────────────────────────────────────────

    def get_profile_manager(self) -> dbus.Interface:
        """Return the BlueZ ProfileManager1 interface for registering profiles."""
        if self._profile_manager is None:
            obj = self._bus.get_object(BLUEZ_SERVICE, "/org/bluez")
            self._profile_manager = dbus.Interface(obj, BLUEZ_PROFILE_MANAGER_IFACE)
        return self._profile_manager

    def register_profile(self, profile_path: str, uuid: str, options: dict) -> None:
        """
        Register a Bluetooth profile with BlueZ.

        Args:
            profile_path: D-Bus object path for the profile.
            uuid: Service UUID string.
            options: Profile options dict (Name, Role, Channel, etc.).
        """
        manager = self.get_profile_manager()
        manager.RegisterProfile(
            dbus.ObjectPath(profile_path),
            uuid,
            dbus.Dictionary(options, signature="sv"),
        )
        logger.info("Registered profile %s (UUID: %s)", profile_path, uuid)

    def unregister_profile(self, profile_path: str) -> None:
        """Unregister a previously registered Bluetooth profile."""
        manager = self.get_profile_manager()
        manager.UnregisterProfile(dbus.ObjectPath(profile_path))
        logger.info("Unregistered profile %s", profile_path)

    # ── Agent Manager ──────────────────────────────────────────────────

    def get_agent_manager(self) -> dbus.Interface:
        """Return the BlueZ AgentManager1 interface for pairing agents."""
        obj = self._bus.get_object(BLUEZ_SERVICE, "/org/bluez")
        return dbus.Interface(obj, BLUEZ_AGENT_MANAGER_IFACE)

    # ── Low-Level HCI Utilities ────────────────────────────────────────

    @staticmethod
    def hci_set_device_class(adapter: str, major: int, minor: int) -> None:
        """
        Set the Bluetooth device class via hcitool.

        Args:
            adapter: Adapter name (e.g., "hci0").
            major: Major device class byte.
            minor: Minor device class byte.
        """
        class_hex = f"0x{(major << 8 | minor):06x}"
        subprocess.run(
            ["hcitool", "-i", adapter, "class", class_hex],
            check=True,
            capture_output=True,
        )
        logger.info("Set device class to %s on %s", class_hex, adapter)

    @staticmethod
    def hci_reset_adapter(adapter: str = "hci0") -> None:
        """Reset the Bluetooth adapter via hciconfig."""
        subprocess.run(["hciconfig", adapter, "reset"], check=True, capture_output=True)
        logger.info("Reset adapter %s", adapter)

    # ── Adapter Info ───────────────────────────────────────────────────

    def get_adapter_info(self) -> dict:
        """Return a summary of the adapter's current state."""
        return {
            "name": self.name,
            "address": self.address,
            "powered": self.powered,
            "discoverable": self.discoverable,
            "pairable": self.pairable,
            "alias": self.alias,
            "adapter": self._adapter_name,
        }

    def ensure_ready(self) -> None:
        """Ensure the adapter is powered on, discoverable, and pairable."""
        if not self.powered:
            self.powered = True
        if not self.discoverable:
            self.discoverable_timeout = 0
            self.discoverable = True
        if not self.pairable:
            self.pairable = True
        logger.info("Adapter %s is ready: %s", self._adapter_name, self.get_adapter_info())

    # ── SDP / Device Class Configuration ──────────────────────────────

    def configure_keyboard_sdp(self, profile_path: str = "/org/bluez/sdp_keyboard") -> None:
        """
        Configure the SDP record and device class to broadcast as a
        Peripheral/Keyboard (CoD 0x000540).

        This sets the HCI device class to Peripheral/Keyboard and registers
        an SDP service record advertising HID keyboard capabilities with
        BlueZ's ProfileManager.

        Args:
            profile_path: D-Bus object path for the SDP profile registration.
        """
        # Set HCI device class to Peripheral/Keyboard (0x000540)
        self.hci_set_device_class(
            self._adapter_name,
            DEVICE_CLASS_MAJOR_PERIPHERAL,
            DEVICE_CLASS_MINOR_KEYBOARD,
        )
        logger.info(
            "Device class set to Peripheral/Keyboard (0x%06x) on %s",
            DEVICE_CLASS_PERIPHERAL_KEYBOARD,
            self._adapter_name,
        )

        # Register the keyboard SDP record with BlueZ
        sdp_options = {
            "ServiceRecord": dbus.String(KEYBOARD_SDP_RECORD_XML),
            "Role": dbus.String("server"),
            "RequireAuthentication": dbus.Boolean(False),
            "RequireAuthorization": dbus.Boolean(False),
        }
        self.register_profile(
            profile_path,
            "00001124-0000-1000-8000-00805f9b34fb",  # HID UUID
            sdp_options,
        )
        logger.info(
            "Keyboard SDP record registered at %s (CoD: 0x%06x)",
            profile_path,
            DEVICE_CLASS_PERIPHERAL_KEYBOARD,
        )

    @staticmethod
    def get_keyboard_device_class() -> int:
        """Return the Peripheral/Keyboard Class of Device value (0x000540)."""
        return DEVICE_CLASS_PERIPHERAL_KEYBOARD

    @staticmethod
    def get_keyboard_sdp_record() -> str:
        """Return the SDP record XML for a Peripheral/Keyboard device."""
        return KEYBOARD_SDP_RECORD_XML
