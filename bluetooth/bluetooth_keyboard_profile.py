"""
Bluetooth Keyboard Profile — BlueZ Profile1 implementation for HID Keyboard.

Implements the org.bluez.Profile1 D-Bus interface to register as a Bluetooth
HID (Human Interface Device) keyboard. Uses the standard HID service UUID
0x1124 and exposes the required Profile1 methods: Release, NewConnection,
and RequestDisconnection.
"""

import dbus
import dbus.service
import logging
import os
from typing import Optional

logger = logging.getLogger(__name__)

# HID Keyboard Service UUID (Bluetooth SIG assigned)
HID_KEYBOARD_UUID = "00001124-0000-1000-8000-00805f9b34fb"
HID_KEYBOARD_UUID_SHORT = "0x1124"

# BlueZ Profile1 D-Bus interface
BLUEZ_PROFILE1_IFACE = "org.bluez.Profile1"

# Default D-Bus object path for the keyboard profile
DEFAULT_PROFILE_PATH = "/org/bluez/hid_keyboard_profile"

# HID keyboard SDP record — defines the device as a keyboard peripheral
# with boot protocol support and standard HID descriptor.
SDP_RECORD_XML = """<?xml version="1.0" encoding="UTF-8" ?>
<record>
  <attribute id="0x0001">
    <sequence>
      <uuid value="0x1124" />
    </sequence>
  </attribute>
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
  <attribute id="0x0005">
    <sequence>
      <uuid value="0x1002" />
    </sequence>
  </attribute>
  <attribute id="0x0006">
    <sequence>
      <uint16 value="0x656e" />
      <uint16 value="0x006a" />
      <uint16 value="0x0100" />
    </sequence>
  </attribute>
  <attribute id="0x0009">
    <sequence>
      <sequence>
        <uuid value="0x1124" />
        <uint16 value="0x0100" />
      </sequence>
    </sequence>
  </attribute>
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
  <attribute id="0x0100">
    <text value="Bluetooth HID Keyboard" />
  </attribute>
  <attribute id="0x0101">
    <text value="Keyboard" />
  </attribute>
  <attribute id="0x0102">
    <text value="SMS Gateway HID Keyboard Profile" />
  </attribute>
  <attribute id="0x0200">
    <uint16 value="0x0100" />
  </attribute>
  <attribute id="0x0201">
    <uint16 value="0x0111" />
  </attribute>
  <attribute id="0x0202">
    <uint8 value="0x40" />
  </attribute>
  <attribute id="0x0203">
    <uint8 value="0x00" />
  </attribute>
  <attribute id="0x0204">
    <boolean value="true" />
  </attribute>
  <attribute id="0x0205">
    <boolean value="true" />
  </attribute>
  <attribute id="0x0206">
    <sequence>
      <sequence>
        <uint8 value="0x22" />
        <text encoding="hex" value="05010906a101850175019508050719e029e715002501810295017508810395057501050819012905910295017503910395067508150025650507190029658100c0050c0901a1018503750895021501268c0219012a8c028100c0" />
      </sequence>
    </sequence>
  </attribute>
  <attribute id="0x0207">
    <sequence>
      <sequence>
        <uint16 value="0x0409" />
        <uint16 value="0x0100" />
      </sequence>
    </sequence>
  </attribute>
  <attribute id="0x020b">
    <uint16 value="0x0100" />
  </attribute>
  <attribute id="0x020c">
    <uint16 value="0x0c80" />
  </attribute>
  <attribute id="0x020d">
    <boolean value="false" />
  </attribute>
  <attribute id="0x020e">
    <boolean value="true" />
  </attribute>
</record>
"""


class BluetoothKeyboardProfile(dbus.service.Object):
    """
    BlueZ Profile1 implementation for a Bluetooth HID keyboard.

    Registers with BlueZ's ProfileManager1 as a HID device using the
    standard keyboard service UUID (0x1124). Handles incoming connections
    and disconnection requests from paired devices.

    Usage:
        manager = BluetoothManager()
        profile = BluetoothKeyboardProfile(manager.bus)
        profile.register(manager)
    """

    def __init__(self, bus: dbus.SystemBus, object_path: str = DEFAULT_PROFILE_PATH):
        """
        Initialize the keyboard profile on the D-Bus.

        Args:
            bus: The D-Bus system bus instance.
            object_path: D-Bus object path for this profile.
        """
        self._object_path = object_path
        self._bus = bus
        self._connections: dict[str, int] = {}  # device_path -> fd
        self._registered = False
        super().__init__(bus, object_path)
        logger.info("BluetoothKeyboardProfile created at %s", object_path)

    @property
    def uuid(self) -> str:
        """Return the HID keyboard service UUID."""
        return HID_KEYBOARD_UUID

    @property
    def uuid_short(self) -> str:
        """Return the short-form HID keyboard UUID."""
        return HID_KEYBOARD_UUID_SHORT

    @property
    def object_path(self) -> str:
        """Return the D-Bus object path for this profile."""
        return self._object_path

    @property
    def is_registered(self) -> bool:
        """Return whether this profile is currently registered with BlueZ."""
        return self._registered

    @property
    def active_connections(self) -> dict[str, int]:
        """Return a dict of active connections (device_path -> fd)."""
        return dict(self._connections)

    def get_profile_options(self) -> dict:
        """
        Return the profile registration options for BlueZ ProfileManager1.

        These options define the profile as a HID keyboard with the
        appropriate SDP service record and role.
        """
        return {
            "Name": dbus.String("HID Keyboard"),
            "Role": dbus.String("server"),
            "RequireAuthentication": dbus.Boolean(False),
            "RequireAuthorization": dbus.Boolean(False),
            "AutoConnect": dbus.Boolean(True),
            "ServiceRecord": dbus.String(SDP_RECORD_XML),
        }

    def register(self, bluetooth_manager) -> None:
        """
        Register this profile with BlueZ via the BluetoothManager.

        Args:
            bluetooth_manager: A BluetoothManager instance with an active
                               D-Bus connection and profile manager.
        """
        if self._registered:
            logger.warning("Profile already registered at %s", self._object_path)
            return

        bluetooth_manager.register_profile(
            self._object_path,
            HID_KEYBOARD_UUID,
            self.get_profile_options(),
        )
        self._registered = True
        logger.info(
            "HID Keyboard profile registered (UUID: %s, path: %s)",
            HID_KEYBOARD_UUID_SHORT,
            self._object_path,
        )

    def unregister(self, bluetooth_manager) -> None:
        """
        Unregister this profile from BlueZ.

        Args:
            bluetooth_manager: A BluetoothManager instance.
        """
        if not self._registered:
            logger.warning("Profile not registered, nothing to unregister")
            return

        bluetooth_manager.unregister_profile(self._object_path)
        self._registered = False
        logger.info("HID Keyboard profile unregistered from %s", self._object_path)

    # ── Profile1 D-Bus Interface Methods ──────────────────────────────

    @dbus.service.method(BLUEZ_PROFILE1_IFACE, in_signature="", out_signature="")
    def Release(self):
        """Called by BlueZ when the profile is unregistered."""
        logger.info("Profile1.Release called — profile unregistered by BlueZ")
        self._registered = False
        self._connections.clear()

    @dbus.service.method(BLUEZ_PROFILE1_IFACE, in_signature="oha{sv}", out_signature="")
    def NewConnection(self, device, fd, fd_properties):
        """
        Called by BlueZ when a new HID connection is established.

        Args:
            device: D-Bus object path of the connected device.
            fd: File descriptor for the L2CAP connection.
            fd_properties: Connection properties dict.
        """
        device_path = str(device)
        fd_num = fd.take()
        self._connections[device_path] = fd_num
        logger.info(
            "NewConnection from %s (fd=%d, properties=%s)",
            device_path,
            fd_num,
            dict(fd_properties) if fd_properties else {},
        )

    @dbus.service.method(BLUEZ_PROFILE1_IFACE, in_signature="o", out_signature="")
    def RequestDisconnection(self, device):
        """
        Called by BlueZ when a device requests disconnection.

        Args:
            device: D-Bus object path of the disconnecting device.
        """
        device_path = str(device)
        fd = self._connections.pop(device_path, None)
        if fd is not None:
            try:
                os.close(fd)
            except OSError:
                pass
        logger.info("RequestDisconnection from %s (fd=%s)", device_path, fd)
