package com.example.peedetector.ble

/**
 * 连接状态机。
 */
enum class ConnectionState {
    /** 未连接（初始） */
    DISCONNECTED,

    /** 扫描中 */
    SCANNING,

    /** 连接中 */
    CONNECTING,

    /** 发现服务中 */
    DISCOVERING,

    /** 订阅中 */
    SUBSCRIBING,

    /** 已连接，等待数据 */
    CONNECTED_WAITING,

    /** 接收中 */
    RECEIVING,
}
