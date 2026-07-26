package com.htc.android.teeter

import android.os.SystemClock

internal object CS {

    private var sIsPaused = false
    private var sLevelStartTime = 0L
    private var sLevelTime = 0L
    private var sTotalTime = 0L
    private var sLevelAttempt = 0
    private var sTotalAttempt = 1

    var pausedState: Boolean
        get() = sIsPaused
        set(b) { sIsPaused = b }

    var levelTime: Long
        get() = sLevelTime
        set(value) { sLevelTime = value }

    var levelAttempt: Int
        get() = sLevelAttempt
        set(value) { sLevelAttempt = value }

    var totalTime: Long
        get() = sTotalTime
        set(value) { sTotalTime = value }

    var totalAttempt: Int
        get() = sTotalAttempt
        set(value) { sTotalAttempt = value }

    fun sfnBeginLevel() {
        sLevelStartTime = SystemClock.uptimeMillis()
        if (!sIsPaused) {
            sLevelAttempt = 0
            sLevelTime = 0L
        } else {
            sIsPaused = false
        }
    }

    fun sfnFallInHole() {
        sLevelAttempt++
    }

    fun sfnEndLevel() {
        sLevelTime += SystemClock.uptimeMillis() - sLevelStartTime
        sTotalTime += sLevelTime
        sLevelAttempt++
        sTotalAttempt += sLevelAttempt - 1
        sIsPaused = false
    }

    fun sfnPauseRecord() {
        sIsPaused = true
        sLevelTime += SystemClock.uptimeMillis() - sLevelStartTime
        sLevelStartTime = SystemClock.uptimeMillis()
    }

    fun sfnReset() {
        sIsPaused = false
        sLevelStartTime = 0L
        sLevelTime = 0L
        sTotalTime = 0L
        sLevelAttempt = 0
        sTotalAttempt = 1
    }
}
