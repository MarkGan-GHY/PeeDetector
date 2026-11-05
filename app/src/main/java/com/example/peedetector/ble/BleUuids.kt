package com.example.peedetector.ble

import java.util.UUID

/**
 * E104-BT5010A 测试固件接口 UUID（依据硬件方规范）。
 */
object BleUuids {
    /** 服务 FFF0 */
    val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    /** 接收特征 FFF1（Read / Notify） */
    val CHARACTERISTIC_RX: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    /** CCCD 客户端特征配置描述符 */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
