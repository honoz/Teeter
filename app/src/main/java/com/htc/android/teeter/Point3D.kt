package com.htc.android.teeter

data class Point3D(
    var x: Int,
    var y: Int,
    var z: Int
) {
    override fun toString(): String {
        return "Point3D ($x,$y,$z)"
    }
}
