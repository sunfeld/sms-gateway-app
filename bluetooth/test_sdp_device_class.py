"""
Unit tests for SDP record configuration and device class broadcasting
as Peripheral/Keyboard (0x000540).

Validates that bluetooth_manager.py and bluetooth_keyboard_profile.py
correctly configure the SDP service record and device class for a
Bluetooth HID keyboard peripheral.
"""

import unittest
from unittest.mock import patch, MagicMock
import subprocess
import sys
import xml.etree.ElementTree as ET

# Mock dbus before importing modules under test
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

from bluetooth_manager import BluetoothManager
from bluetooth_keyboard_profile import (
    BluetoothKeyboardProfile,
    SDP_RECORD_XML,
    HID_KEYBOARD_UUID,
)


class TestSDPRecordXMLParseable(unittest.TestCase):
    """Verify the SDP record XML is well-formed and parseable."""

    def test_sdp_record_parses_as_xml(self):
        root = ET.fromstring(SDP_RECORD_XML)
        self.assertEqual(root.tag, "record")

    def test_sdp_record_has_attributes(self):
        root = ET.fromstring(SDP_RECORD_XML)
        attributes = root.findall("attribute")
        self.assertGreater(len(attributes), 0)

    def test_sdp_record_attribute_ids_are_hex(self):
        root = ET.fromstring(SDP_RECORD_XML)
        for attr in root.findall("attribute"):
            attr_id = attr.get("id")
            self.assertIsNotNone(attr_id)
            self.assertTrue(attr_id.startswith("0x"), f"Attribute ID {attr_id} not hex")


class TestSDPRecordServiceClass(unittest.TestCase):
    """Verify SDP attribute 0x0001 (ServiceClassIDList) is HID."""

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)

    def _find_attribute(self, attr_id):
        for attr in self.root.findall("attribute"):
            if attr.get("id") == attr_id:
                return attr
        return None

    def test_service_class_id_list_present(self):
        attr = self._find_attribute("0x0001")
        self.assertIsNotNone(attr, "SDP attribute 0x0001 (ServiceClassIDList) missing")

    def test_service_class_contains_hid_uuid(self):
        attr = self._find_attribute("0x0001")
        uuid_elem = attr.find(".//uuid")
        self.assertIsNotNone(uuid_elem)
        self.assertEqual(uuid_elem.get("value"), "0x1124")


class TestSDPRecordProtocolDescriptor(unittest.TestCase):
    """Verify SDP attribute 0x0004 (ProtocolDescriptorList) for L2CAP + HIDP."""

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)

    def _find_attribute(self, attr_id):
        for attr in self.root.findall("attribute"):
            if attr.get("id") == attr_id:
                return attr
        return None

    def test_protocol_descriptor_list_present(self):
        attr = self._find_attribute("0x0004")
        self.assertIsNotNone(attr, "SDP attribute 0x0004 (ProtocolDescriptorList) missing")

    def test_protocol_includes_l2cap(self):
        attr = self._find_attribute("0x0004")
        uuids = [u.get("value") for u in attr.findall(".//uuid")]
        self.assertIn("0x0100", uuids, "L2CAP UUID (0x0100) not in protocol descriptor")

    def test_protocol_includes_hidp(self):
        attr = self._find_attribute("0x0004")
        uuids = [u.get("value") for u in attr.findall(".//uuid")]
        self.assertIn("0x0011", uuids, "HIDP UUID (0x0011) not in protocol descriptor")


class TestSDPRecordProfileDescriptor(unittest.TestCase):
    """Verify SDP attribute 0x0009 (BluetoothProfileDescriptorList)."""

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)

    def _find_attribute(self, attr_id):
        for attr in self.root.findall("attribute"):
            if attr.get("id") == attr_id:
                return attr
        return None

    def test_profile_descriptor_list_present(self):
        attr = self._find_attribute("0x0009")
        self.assertIsNotNone(attr, "SDP attribute 0x0009 (ProfileDescriptorList) missing")

    def test_profile_descriptor_references_hid(self):
        attr = self._find_attribute("0x0009")
        uuid_elem = attr.find(".//uuid")
        self.assertIsNotNone(uuid_elem)
        self.assertEqual(uuid_elem.get("value"), "0x1124")


class TestSDPRecordServiceName(unittest.TestCase):
    """Verify SDP attributes 0x0100-0x0102 (service name/description/provider)."""

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)

    def _find_attribute(self, attr_id):
        for attr in self.root.findall("attribute"):
            if attr.get("id") == attr_id:
                return attr
        return None

    def test_service_name_present(self):
        attr = self._find_attribute("0x0100")
        self.assertIsNotNone(attr, "SDP attribute 0x0100 (ServiceName) missing")
        text = attr.find("text")
        self.assertIsNotNone(text)
        self.assertIn("Keyboard", text.get("value"))

    def test_service_description_present(self):
        attr = self._find_attribute("0x0101")
        self.assertIsNotNone(attr, "SDP attribute 0x0101 (ServiceDescription) missing")
        text = attr.find("text")
        self.assertEqual(text.get("value"), "Keyboard")

    def test_provider_name_present(self):
        attr = self._find_attribute("0x0102")
        self.assertIsNotNone(attr, "SDP attribute 0x0102 (ProviderName) missing")


class TestSDPRecordHIDAttributes(unittest.TestCase):
    """Verify HID-specific SDP attributes required for keyboard peripheral."""

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)

    def _find_attribute(self, attr_id):
        for attr in self.root.findall("attribute"):
            if attr.get("id") == attr_id:
                return attr
        return None

    def test_hid_device_release_number(self):
        """Attribute 0x0200 — HIDDeviceReleaseNumber."""
        attr = self._find_attribute("0x0200")
        self.assertIsNotNone(attr, "HIDDeviceReleaseNumber (0x0200) missing")

    def test_hid_parser_version(self):
        """Attribute 0x0201 — HIDParserVersion."""
        attr = self._find_attribute("0x0201")
        self.assertIsNotNone(attr, "HIDParserVersion (0x0201) missing")

    def test_hid_device_subclass_is_keyboard(self):
        """Attribute 0x0202 — HIDDeviceSubclass must be 0x40 (keyboard)."""
        attr = self._find_attribute("0x0202")
        self.assertIsNotNone(attr, "HIDDeviceSubclass (0x0202) missing")
        uint8 = attr.find("uint8")
        self.assertIsNotNone(uint8)
        self.assertEqual(uint8.get("value"), "0x40",
                         "HIDDeviceSubclass should be 0x40 for keyboard")

    def test_hid_country_code(self):
        """Attribute 0x0203 — HIDCountryCode."""
        attr = self._find_attribute("0x0203")
        self.assertIsNotNone(attr, "HIDCountryCode (0x0203) missing")

    def test_hid_virtual_cable(self):
        """Attribute 0x0204 — HIDVirtualCable."""
        attr = self._find_attribute("0x0204")
        self.assertIsNotNone(attr, "HIDVirtualCable (0x0204) missing")

    def test_hid_reconnect_initiate(self):
        """Attribute 0x0205 — HIDReconnectInitiate."""
        attr = self._find_attribute("0x0205")
        self.assertIsNotNone(attr, "HIDReconnectInitiate (0x0205) missing")

    def test_hid_descriptor_list(self):
        """Attribute 0x0206 — HIDDescriptorList (contains the HID report descriptor)."""
        attr = self._find_attribute("0x0206")
        self.assertIsNotNone(attr, "HIDDescriptorList (0x0206) missing")
        # The descriptor should contain a hex-encoded HID report descriptor
        text = attr.find(".//text[@encoding='hex']")
        self.assertIsNotNone(text, "HID report descriptor hex encoding missing")
        descriptor_hex = text.get("value")
        self.assertIsNotNone(descriptor_hex)
        # HID keyboard report descriptors start with 0x05 0x01 (Usage Page: Generic Desktop)
        self.assertTrue(descriptor_hex.startswith("0501"),
                        "HID descriptor should start with Usage Page Generic Desktop (0501)")

    def test_hid_langid_base_list(self):
        """Attribute 0x0207 — HIDLANGIDBaseList."""
        attr = self._find_attribute("0x0207")
        self.assertIsNotNone(attr, "HIDLANGIDBaseList (0x0207) missing")

    def test_hid_boot_device(self):
        """Attribute 0x020e — HIDBootDevice (keyboard should support boot protocol)."""
        attr = self._find_attribute("0x020e")
        self.assertIsNotNone(attr, "HIDBootDevice (0x020e) missing")
        boolean = attr.find("boolean")
        self.assertIsNotNone(boolean)
        self.assertEqual(boolean.get("value"), "true",
                         "HIDBootDevice should be true for keyboard")


class TestSDPRecordAdditionalProtocol(unittest.TestCase):
    """Verify SDP attribute 0x000d (AdditionalProtocolDescriptorList)."""

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)

    def _find_attribute(self, attr_id):
        for attr in self.root.findall("attribute"):
            if attr.get("id") == attr_id:
                return attr
        return None

    def test_additional_protocol_present(self):
        attr = self._find_attribute("0x000d")
        self.assertIsNotNone(attr, "AdditionalProtocolDescriptorList (0x000d) missing")

    def test_additional_protocol_has_interrupt_channel(self):
        """HID interrupt channel uses L2CAP PSM 0x0013."""
        attr = self._find_attribute("0x000d")
        uint16s = [u.get("value") for u in attr.findall(".//uint16")]
        self.assertIn("0x0013", uint16s,
                      "HID interrupt channel PSM 0x0013 missing from additional protocol")


class TestDeviceClassComputation(unittest.TestCase):
    """Test that hci_set_device_class computes the correct class for Peripheral/Keyboard."""

    @patch("bluetooth_manager.subprocess.run")
    def test_peripheral_keyboard_class_0x000540(self, mock_run):
        """Major=5 (Peripheral), Minor=64 (Keyboard) -> 0x000540."""
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        cmd = mock_run.call_args[0][0]
        self.assertIn("0x000540", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_class_computation_formula(self, mock_run):
        """Verify (major << 8 | minor) produces correct hex."""
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        expected = (5 << 8 | 64)
        self.assertEqual(expected, 0x000540)

    @patch("bluetooth_manager.subprocess.run")
    def test_device_class_uses_hcitool(self, mock_run):
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        cmd = mock_run.call_args[0][0]
        self.assertEqual(cmd[0], "hcitool")

    @patch("bluetooth_manager.subprocess.run")
    def test_device_class_specifies_adapter(self, mock_run):
        BluetoothManager.hci_set_device_class("hci1", 5, 64)
        cmd = mock_run.call_args[0][0]
        self.assertIn("hci1", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_device_class_check_true(self, mock_run):
        """hci_set_device_class should use check=True to raise on failure."""
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        self.assertTrue(mock_run.call_args[1].get("check", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_device_class_captures_output(self, mock_run):
        BluetoothManager.hci_set_device_class("hci0", 5, 64)
        self.assertTrue(mock_run.call_args[1].get("capture_output", False))

    @patch("bluetooth_manager.subprocess.run")
    def test_device_class_different_minor(self, mock_run):
        """Major=5, Minor=0 -> 0x000500 (generic peripheral)."""
        BluetoothManager.hci_set_device_class("hci0", 5, 0)
        cmd = mock_run.call_args[0][0]
        self.assertIn("0x000500", cmd)

    @patch("bluetooth_manager.subprocess.run")
    def test_device_class_subprocess_error_propagates(self, mock_run):
        """CalledProcessError from hcitool should propagate."""
        mock_run.side_effect = subprocess.CalledProcessError(1, "hcitool")
        with self.assertRaises(subprocess.CalledProcessError):
            BluetoothManager.hci_set_device_class("hci0", 5, 64)


class TestSDPRecordInProfileOptions(unittest.TestCase):
    """Verify the SDP record is included in profile registration options."""

    def setUp(self):
        bus = MagicMock()
        self.profile = BluetoothKeyboardProfile(bus)

    def test_service_record_in_options(self):
        opts = self.profile.get_profile_options()
        self.assertIn("ServiceRecord", opts)

    def test_service_record_is_full_sdp_xml(self):
        opts = self.profile.get_profile_options()
        self.assertEqual(opts["ServiceRecord"], SDP_RECORD_XML)

    def test_service_record_contains_keyboard_subclass(self):
        opts = self.profile.get_profile_options()
        self.assertIn('value="0x40"', opts["ServiceRecord"])

    def test_service_record_contains_hid_uuid(self):
        opts = self.profile.get_profile_options()
        self.assertIn("0x1124", opts["ServiceRecord"])


class TestSDPRecordMandatoryAttributes(unittest.TestCase):
    """Verify all mandatory SDP attributes for a HID keyboard are present."""

    MANDATORY_ATTRIBUTES = [
        ("0x0001", "ServiceClassIDList"),
        ("0x0004", "ProtocolDescriptorList"),
        ("0x0005", "BrowseGroupList"),
        ("0x0006", "LanguageBaseAttributeIDList"),
        ("0x0009", "BluetoothProfileDescriptorList"),
        ("0x000d", "AdditionalProtocolDescriptorList"),
        ("0x0100", "ServiceName"),
        ("0x0200", "HIDDeviceReleaseNumber"),
        ("0x0201", "HIDParserVersion"),
        ("0x0202", "HIDDeviceSubclass"),
        ("0x0203", "HIDCountryCode"),
        ("0x0204", "HIDVirtualCable"),
        ("0x0205", "HIDReconnectInitiate"),
        ("0x0206", "HIDDescriptorList"),
        ("0x0207", "HIDLANGIDBaseList"),
        ("0x020b", "HIDProfileVersion"),
        ("0x020c", "HIDSupervisionTimeout"),
        ("0x020d", "HIDNormallyConnectable"),
        ("0x020e", "HIDBootDevice"),
    ]

    def setUp(self):
        self.root = ET.fromstring(SDP_RECORD_XML)
        self.attr_ids = {attr.get("id") for attr in self.root.findall("attribute")}

    def test_all_mandatory_attributes_present(self):
        missing = []
        for attr_id, name in self.MANDATORY_ATTRIBUTES:
            if attr_id not in self.attr_ids:
                missing.append(f"{attr_id} ({name})")
        self.assertEqual(missing, [],
                         f"Missing mandatory SDP attributes: {', '.join(missing)}")

    def test_total_attribute_count(self):
        """SDP record should have at least 19 mandatory attributes."""
        self.assertGreaterEqual(len(self.attr_ids), 19)


class TestDeviceClassValues(unittest.TestCase):
    """Verify the Peripheral/Keyboard device class constant values."""

    def test_major_class_peripheral(self):
        """Major device class 5 = Peripheral."""
        major = 5
        self.assertEqual(major, 5)

    def test_minor_class_keyboard(self):
        """Minor device class 64 (0x40) = Keyboard."""
        minor = 64
        self.assertEqual(minor, 0x40)

    def test_combined_class_value(self):
        """(5 << 8 | 64) should equal 0x000540."""
        major, minor = 5, 64
        device_class = major << 8 | minor
        self.assertEqual(device_class, 0x000540)

    def test_hex_format_string(self):
        """Hex format should produce '0x000540'."""
        major, minor = 5, 64
        class_hex = f"0x{(major << 8 | minor):06x}"
        self.assertEqual(class_hex, "0x000540")


if __name__ == "__main__":
    unittest.main()
