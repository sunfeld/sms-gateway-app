package com.sunfeld.smsgateway

data class DeviceProfile(
    val id: String,
    val displayName: String,
    val sdpName: String,
    val sdpDescription: String,
    val sdpProvider: String,
    val ouiPrefix: String
)

object DeviceProfiles {
    val ALL: List<DeviceProfile> = listOf(
        DeviceProfile(
            id = "apple_magic_keyboard",
            displayName = "Apple Magic Keyboard",
            sdpName = "Magic Keyboard",
            sdpDescription = "Bluetooth Wireless Keyboard",
            sdpProvider = "Apple Inc.",
            ouiPrefix = "00:1A:7D"
        ),
        DeviceProfile(
            id = "logitech_k380",
            displayName = "Logitech K380",
            sdpName = "Keyboard K380",
            sdpDescription = "Bluetooth Multi-Device Keyboard",
            sdpProvider = "Logitech",
            ouiPrefix = "00:1F:20"
        ),
        DeviceProfile(
            id = "logitech_k480",
            displayName = "Logitech K480",
            sdpName = "Keyboard K480",
            sdpDescription = "Bluetooth Multi-Device Keyboard",
            sdpProvider = "Logitech",
            ouiPrefix = "00:04:F3"
        ),
        DeviceProfile(
            id = "logitech_mx_keys",
            displayName = "Logitech MX Keys",
            sdpName = "MX Keys",
            sdpDescription = "Advanced Wireless Illuminated Keyboard",
            sdpProvider = "Logitech",
            ouiPrefix = "6C:B7:49"
        ),
        DeviceProfile(
            id = "microsoft_bt_keyboard",
            displayName = "Microsoft Bluetooth Keyboard",
            sdpName = "Microsoft Keyboard",
            sdpDescription = "Bluetooth Desktop Keyboard",
            sdpProvider = "Microsoft Corporation",
            ouiPrefix = "00:1B:DC"
        ),
        DeviceProfile(
            id = "samsung_smart_keyboard",
            displayName = "Samsung Smart Keyboard",
            sdpName = "Samsung Keyboard",
            sdpDescription = "Smart Bluetooth Keyboard",
            sdpProvider = "Samsung Electronics",
            ouiPrefix = "34:88:5D"
        ),
        DeviceProfile(
            id = "anker_ultra_compact",
            displayName = "Anker Ultra Compact Keyboard",
            sdpName = "Anker Keyboard",
            sdpDescription = "Ultra Compact Bluetooth Keyboard",
            sdpProvider = "Anker",
            ouiPrefix = "DC:A6:32"
        ),
        DeviceProfile(
            id = "razer_blackwidow_v3",
            displayName = "Razer BlackWidow V3",
            sdpName = "Razer BW V3",
            sdpDescription = "Wireless Mechanical Gaming Keyboard",
            sdpProvider = "Razer Inc.",
            ouiPrefix = "7C:ED:8D"
        ),
        DeviceProfile(
            id = "corsair_k63_wireless",
            displayName = "Corsair K63 Wireless",
            sdpName = "Corsair K63",
            sdpDescription = "Wireless Mechanical Gaming Keyboard",
            sdpProvider = "Corsair",
            ouiPrefix = "DC:A6:32"
        ),
        DeviceProfile(
            id = "apple_keyboard",
            displayName = "Apple Keyboard",
            sdpName = "Apple Keyboard",
            sdpDescription = "Wireless Keyboard",
            sdpProvider = "Apple Inc.",
            ouiPrefix = "28:6C:07"
        ),
        DeviceProfile(
            id = "logitech_k780",
            displayName = "Logitech K780",
            sdpName = "Keyboard K780",
            sdpDescription = "Multi-Device Wireless Keyboard",
            sdpProvider = "Logitech",
            ouiPrefix = "00:1F:20"
        ),
        DeviceProfile(
            id = "hp_bt_keyboard",
            displayName = "HP Bluetooth Keyboard",
            sdpName = "HP Keyboard",
            sdpDescription = "HP Wireless Keyboard",
            sdpProvider = "HP Inc.",
            ouiPrefix = "F4:F5:DB"
        ),
        DeviceProfile(
            id = "dell_kb500",
            displayName = "Dell KB500 Wireless",
            sdpName = "Dell KB500",
            sdpDescription = "Dell Wireless Keyboard",
            sdpProvider = "Dell Technologies",
            ouiPrefix = "DC:A6:32"
        ),
        DeviceProfile(
            id = "brydge_bt_keyboard",
            displayName = "Brydge BT Keyboard",
            sdpName = "Brydge Keyboard",
            sdpDescription = "Bluetooth Keyboard for Tablets",
            sdpProvider = "Brydge",
            ouiPrefix = "DC:A6:32"
        ),
        DeviceProfile(
            id = "keychron_k2",
            displayName = "Keychron K2",
            sdpName = "Keychron K2",
            sdpDescription = "Wireless Mechanical Keyboard",
            sdpProvider = "Keychron",
            ouiPrefix = "DC:A6:32"
        )
    )

    val DEFAULT: DeviceProfile = ALL.first()

    fun findById(id: String): DeviceProfile? = ALL.find { it.id == id }
}
