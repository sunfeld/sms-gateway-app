package com.sunfeld.smsgateway

data class HidPreset(
    val id: String,
    val name: String,
    val profileId: String,
    val customDeviceName: String,
    val targetAddresses: List<String>,
    val payload: String,
    val createdAt: Long
)
