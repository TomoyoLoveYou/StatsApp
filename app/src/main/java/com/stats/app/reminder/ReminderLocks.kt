package com.stats.app.reminder

import kotlinx.coroutines.sync.Mutex

/**
 * 串行化"由系统事件触发的打卡写入"。
 *
 * 闹钟触发（[ReminderHandler]）与漏触发补账（[AutoCompleteCatchUp]）可能并发：
 * 例如进程刚被闹钟广播唤醒时，Application 启动时发起的补账与广播处理几乎同时进行。
 * 用同一把锁保证同一触发时刻不会被记两次。
 */
internal object ReminderLocks {
    val checkIn = Mutex()
}
