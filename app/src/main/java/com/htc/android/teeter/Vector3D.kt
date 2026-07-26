package com.htc.android.teeter

import kotlin.math.sqrt

class Vector3D(
    var x: Int,
    var y: Int,
    var z: Int
) {

    fun cross(v: Vector3D): Vector3D {
        return Vector3D(
            (y * v.z) - (z * v.y),
            (z * v.x) - (x * v.z),
            (x * v.y) - (y * v.x)
        )
    }

    override fun toString(): String {
        return "Vector3D ($x,$y,$z)"
    }

    @Suppress("unused")
    fun dot(v: Vector3D): Int {
        return (x * v.x) + (y * v.y) + (z * v.z)
    }

    @Suppress("unused")
    fun length(): Int {
        return sqrt(((x * x) + (y * y) + (z * z)).toDouble()).toInt()
    }
}
