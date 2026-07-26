package com.htc.android.teeter

import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants
import kotlin.math.sqrt

object CL {
    private var a_k: Array<Point3D?>? = null

    var diamondZone: Array<Triangle?>? = null

    var mIsFacet: Boolean = false

    var begin: Point? = Point()

    var end: Point? = Point()

    var walls: Array<Rect> = emptyArray()

    var walls_big: Array<Rect> = emptyArray()

    var holes: Array<Point> = emptyArray()

    var HOLE_INDEX: Int = -1

    private var diamond_accleration: Array<Vector?>? = null

    val isAcclerationInitialized: Boolean
        get() = diamond_accleration != null

    fun modifyRect(source: Rect): Rect {
        return Rect(CU.s2b(source.left), CU.s2b(source.top), CU.s2b(source.right), CU.s2b(source.bottom))
    }

    fun destToPoint(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = (x1 - x2).toFloat()
        val dy = (y1 - y2).toFloat()
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    fun isAtHole(pos: Point): Boolean {
        val bigRadius = (CU.s2b(CU.HOLE_RADIUS) * 0.8).toInt()
        for (i in holes.indices) {
            val dest = destToPoint(CU.s2b(holes[i].x), CU.s2b(holes[i].y), pos.x, pos.y)
            if (dest > 0.0f && dest < bigRadius) {
                HOLE_INDEX = i
                return true
            }
        }
        return false
    }

    fun isAtEnd(pos: Point): Boolean {
        if (end != null) {
            val bigRadius = (CU.s2b(CU.END_RADIUS) * 0.8).toInt()
            val dest = destToPoint(CU.s2b(end!!.x), CU.s2b(end!!.y), pos.x, pos.y)
            if (dest > 0.0f && dest < bigRadius) {
                return true
            }
        }
        return false
    }

    fun atZone(px: Int, py: Int, vx: Int, vy: Int, depth: Int = 0): Int {
        for (i in diamondZone!!.indices) {
            if (diamondZone!![i]!!.isPointInTriangle(px, py)) {
                return i
            }
        }
        if (depth > 10) {
            LogUtils.w(TTConstants.CL_TAG, "atZone max recursion reached, defaulting to zone 0 pos($px, $py)")
            return 0
        }
        LogUtils.w(TTConstants.CL_TAG, "ball on the edge, recompure is needed pos($px, $py) V($vx, $vy)")
        var vx2 = CU.b2s(vx)
        var vy2 = CU.b2s(vy)
        if (vx2 == 0) {
            vx2 = 5
        }
        if (vy2 == 0) {
            vy2 = 5
        }
        val px2 = px + vx2
        val py2 = py + vy2
        val pxClamped = if (px2 < 0) 0 else px2
        val pyClamped = if (py2 < 0) 0 else py2
        return atZone(pxClamped, pyClamped, vx2, vy2, depth + 1)
    }

    @Throws(Resources.NotFoundException::class)
    fun initAccleration(res: Resources) {
        if (diamond_accleration == null) {
            a_k = arrayOfNulls(11)
            val arrayResIds = intArrayOf(
                R.array.a, R.array.b, R.array.c, R.array.d, R.array.e,
                R.array.f, R.array.g, R.array.h, R.array.i, R.array.j, R.array.k
            )
            for (i in 0..10) {
                val face = res.getIntArray(arrayResIds[i])
                a_k!![i] = Point3D(face[0], face[1], face[2])
            }
            diamondZone = arrayOf(
                Triangle(a_k!![3]!!, a_k!![10]!!, a_k!![5]!!),
                Triangle(a_k!![3]!!, a_k!![5]!!, a_k!![0]!!),
                Triangle(a_k!![3]!!, a_k!![0]!!, a_k!![1]!!),
                Triangle(a_k!![3]!!, a_k!![1]!!, a_k!![2]!!),
                Triangle(a_k!![3]!!, a_k!![2]!!, a_k!![4]!!),
                Triangle(a_k!![3]!!, a_k!![4]!!, a_k!![8]!!),
                Triangle(a_k!![3]!!, a_k!![8]!!, a_k!![10]!!),
                Triangle(a_k!![10]!!, a_k!![8]!!, a_k!![9]!!),
                Triangle(a_k!![10]!!, a_k!![9]!!, a_k!![7]!!),
                Triangle(a_k!![7]!!, a_k!![6]!!, a_k!![10]!!),
                Triangle(a_k!![10]!!, a_k!![6]!!, a_k!![5]!!)
            )
            diamond_accleration = arrayOfNulls(diamondZone!!.size)
            val v01 = Vector3D(0, 0, 0)
            val v12 = Vector3D(0, 0, 0)
            for (i2 in diamond_accleration!!.indices) {
                val zone = diamondZone!![i2]!!
                v01.x = zone.b.x - zone.a.x
                v01.y = zone.b.y - zone.a.y
                v01.z = zone.b.z - zone.a.z
                v12.x = zone.c.x - zone.b.x
                v12.y = zone.c.y - zone.b.y
                v12.z = zone.c.z - zone.b.z
                val normal = v01.cross(v12)
                val nx = normal.x.toFloat() / 100f
                val ny = normal.y.toFloat() / 100f
                val nz = normal.z.toFloat() / 100f
                val nLen = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                val v1DotNormal = nx * nx + ny * ny
                val v1Len = sqrt((nx * nx + ny * ny).toDouble()).toFloat()
                val cosV = if (v1Len == 0f || nLen == 0f) 0f else (v1DotNormal / v1Len) / nLen
                val x = ((nx * CU.GRAVITY_FACTOR * 9.80665f * cosV) / nLen).toInt()
                val y = ((ny * CU.GRAVITY_FACTOR * 9.80665f * cosV) / nLen).toInt()
                diamond_accleration!![i2] = Vector(x, y)
            }
        }
    }

    @Suppress("unused")
    fun getDiamondAccleration(zone: Int): Vector {
        return diamond_accleration!![zone]!!
    }

    @Suppress("unused")
    fun clear() {
        begin = null
        end = null
        walls = emptyArray()
        walls_big = emptyArray()
        holes = emptyArray()
        diamond_accleration = null
        if (diamondZone != null) {
            for (i in diamondZone!!.indices) {
                diamondZone!![i] = null
            }
        }
        diamondZone = null
        if (a_k != null) {
            for (i2 in 0..10) {
                a_k!![i2] = null
            }
            a_k = null
        }
    }
}
