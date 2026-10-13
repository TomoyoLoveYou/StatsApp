package com.stats.app.util

import android.util.Log

/**
 * 统一日志工具。
 * 所有日志使用固定 TAG "StatsApp"，消息带 [模块] 前缀，便于通过 adb 过滤：
 *   adb logcat -s StatsApp
 */
object AppLog {
    private const val TAG = "StatsApp"

    fun d(topic: String, message: String) = Log.d(TAG, "[$topic] $message")

    fun i(topic: String, message: String) = Log.i(TAG, "[$topic] $message")

    fun w(topic: String, message: String, tr: Throwable? = null) =
        Log.w(TAG, "[$topic] $message", tr)

    fun e(topic: String, message: String, tr: Throwable? = null) =
        Log.e(TAG, "[$topic] $message", tr)
}
