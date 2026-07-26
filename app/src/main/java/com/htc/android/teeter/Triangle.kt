package com.htc.android.teeter

class Triangle(
    val a: Point3D,
    val b: Point3D,
    val c: Point3D,
) {
    private var dot00: Double
    private var dot01: Double
    private var dot02: Double = 0.0
    private var dot11: Double
    private var dot12: Double = 0.0
    private var invDenom: Double
    private val vAB = Vector(b.x - a.x, b.y - a.y)
    private val vAC = Vector(c.x - a.x, c.y - a.y)
    private val vAP = Vector()

    init {
        dot00 = vAC.dot(vAC).toDouble()
        dot01 = vAC.dot(vAB).toDouble()
        dot11 = vAB.dot(vAB).toDouble()
        val denom = dot00 * dot11 - dot01 * dot01
        invDenom = if (denom == 0.0) 0.0 else 1.0 / denom
    }

    fun isPointInTriangle(px: Int, py: Int): Boolean {
        vAP.set(px - a.x, py - a.y)
        dot02 = vAC.dot(vAP).toDouble()
        dot12 = vAB.dot(vAP).toDouble()
        val u = (dot11 * dot02 - dot01 * dot12) * invDenom
        val v = (dot00 * dot12 - dot01 * dot02) * invDenom
        return u > 0.0 && v > 0.0 && u + v < 1.0
    }
}
