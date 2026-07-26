package com.htc.android.teeter

import android.graphics.Point
import kotlin.math.sqrt

class Vector : Point {

    constructor() : super(0, 0)

    constructor(x: Int, y: Int) : super(x, y)

    fun setVector(px: Int, py: Int) {
        x = px
        y = py
    }

    fun length(): Int {
        return sqrt((x * x + y * y).toDouble()).toInt()
    }

    fun dot(ballPos: Vector): Int {
        return x * ballPos.x + y * ballPos.y
    }

    fun mul(target: Float): Vector {
        return Vector((x * target).toInt(), (y * target).toInt())
    }

    override fun toString(): String {
        return "Vector($x,$y)"
    }

    fun decrease(friction: Int) {
        val length = length()
        x -= (x * friction) / length
        y -= (y * friction) / length
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector) return false
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int {
        return 31 * x + y
    }
}
