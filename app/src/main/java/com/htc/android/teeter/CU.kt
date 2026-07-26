package com.htc.android.teeter

import android.graphics.Bitmap

object CU {
    const val ACC_PER_MINISEC = 0.066

    var BALL_RADIUS = 0
    var BALL_RADIUS_BIG = 0
    var BG_BMP: Bitmap? = null
    var BOUNCE_RATE = 0.0f

    var END_RADIUS = 0
    var FRICTION_FACTOR = 0

    var GRAVITY_FACTOR = 0
    var HOLE_RADIUS = 0

    var LEVEL_COUNT = 0
    var MAX_SPEED = 0

    const val MSG_ANIM_END = 2
    const val MSG_ANIM_HOLE = 4
    const val MSG_REDRAW = 1
    const val REDRAW_DURATION = 20
    const val RESTART_DELAY = 1000

    var SCREEN_HEIGHT = 720
    var SCREEN_WIDTH = 1280

    const val STATE_AT_END = 2
    const val STATE_AT_HOLE = 1
    const val STATE_NORMAL = 0

    var VIBRATION_ACTIVE_SPEED = 0
    var VIBRATION_ACTIVE_SPEED_GROUND = 0
    var VIBRATION_DURATION = 0
    var VIBRATION_END: LongArray = longArrayOf()
    var VIBRATION_HOLE: LongArray = longArrayOf()

    var BALL_RATIO = 1.53f
    var HOLE_RATIO = 1.1f
    var END_RATIO = 1.3f
    var END_ANIM_RATIO = 2.13f
    var HOLE_ANIM_RATIO = 1.333f
    var DEBUG = false
    var GAME_OVER = false
    var TOUCHABLE = true
    var TIMER_GO = false
    var HOLE_ON = true
    var END_ON = true
    var LEVEL = 1

    var SHADOW_WIDTH = 0
    var SHADOW_HEIGHT = 0
    var WALL_PADDING_TOP = 0
    var WALL_PADDING_BOTTOM = 0
    var WALL_PADDING_LEFT = 0
    var WALL_PADDING_RIGHT = 0

    var GAME_SCALE = 1.0f
    var GAME_OFFSET_X = 0.0f
    var GAME_OFFSET_Y = 0.0f

    fun s2b(num: Int): Int = num shl 10

    fun b2s(num: Int): Int = num shr 10
}
