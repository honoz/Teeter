package com.htc.android.teeter

internal class CTime(milliseconds: Long) {

    val hours: Int
    val minutes: Int
    val seconds: Int

    init {
        val sec = milliseconds / 1000
        hours = (sec / 3600).toInt()
        minutes = ((sec - hours * 3600) / 60).toInt()
        seconds = (sec - hours * 3600 - minutes * 60).toInt()
    }
}