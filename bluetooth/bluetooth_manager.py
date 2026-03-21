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
