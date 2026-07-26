package com.htc.android.teeter

import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
import androidx.core.graphics.withSave
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.Locale

class CBGLoadingThread(private var mActivity: Activity?, private val mLevel: Int) : Thread() {

    init {
        val activity = mActivity ?: throw IllegalStateException("Activity must not be null")
        val option = BitmapFactory.Options().apply { inScaled = false }
        @Suppress("DiscouragedApi")
        val newShadowId = activity.resources.getIdentifier("teeter_bar_shadow", "drawable", activity.packageName)
        if (mBmpEnd == null) {
            mBmpEnd = BitmapFactory.decodeResource(activity.resources, R.drawable.end, option)
        }
        if (mBmpHole == null) {
            mBmpHole = BitmapFactory.decodeResource(activity.resources, R.drawable.hole, option)
        }
        if (newShadowId != 0) {
            if (mNinePatchShadowDrawable == null) {
                mNinePatchShadowDrawable = androidx.core.content.res.ResourcesCompat.getDrawable(
                    activity.resources, newShadowId, activity.theme
                ) as? NinePatchDrawable
            }
        }
        if (mBmpEnd != null && mBmpEnd!!.isRecycled) {
            mBmpEnd = BitmapFactory.decodeResource(activity.resources, R.drawable.end, option)
            LogUtils.w(TTConstants.CB_LOADING_TAG, "BmpEnd is recycled.")
        }
        if (mBmpHole != null && mBmpHole!!.isRecycled) {
            mBmpHole = BitmapFactory.decodeResource(activity.resources, R.drawable.hole, option)
            LogUtils.w(TTConstants.CB_LOADING_TAG, "BmpHole is recycled.")
        }
    }

    override fun run() {
        fnResetLevel()
        try {
            fnParseLevelFile(mLevel)
        } catch (e: IOException) {
            LogUtils.e(TTConstants.CB_LOADING_TAG, "fnParseLevelFile IOException: ${e.message}")
        } catch (e: XmlPullParserException) {
            LogUtils.e(TTConstants.CB_LOADING_TAG, "fnParseLevelFile XmlPullParserException: ${e.message}")
        }
        val oldBitmap = CU.BG_BMP
        CU.BG_BMP = fnCreateBG()
        oldBitmap?.recycle()
        mActivity = null
    }

    private fun fnResetLevel() {
        if (CL.begin != null) {
            CL.begin!!.set(0, 0)
        } else {
            CL.begin = Point(0, 0)
        }
        CL.end = null
        CL.holes = emptyArray()
        CL.walls = emptyArray()
        CL.walls_big = emptyArray()
        CL.mIsFacet = false
    }

    @Throws(XmlPullParserException::class, Resources.NotFoundException::class, IOException::class)
    private fun fnParseLevelFile(nLevel: Int) {
        val levelNum = nLevel.coerceIn(1, 32)
        val resId = LEVEL_XML_IDS[levelNum - 1]
        if (resId == 0) {
            LogUtils.e(TTConstants.CB_LOADING_TAG, "fnParseLevelFile: level resource not found: level${String.format(Locale.US, "%03d", levelNum)}")
            return
        }
        val xrp = mActivity!!.resources.getXml(resId)
        while (xrp.next() != 2) { /* advance to first START_TAG */ }
        xrp.next()
        while (xrp.eventType != 3) {
            while (xrp.eventType != 2) {
                if (xrp.eventType != 1) {
                    xrp.next()
                } else {
                    xrp.close()
                    return
                }
            }
            when (xrp.name) {
                "begin" -> CL.begin!!.set(
                    xrp.getAttributeIntValue(null, "x", -1),
                    xrp.getAttributeIntValue(null, "y", -1)
                )
                "end" -> CL.end = Point(
                    xrp.getAttributeIntValue(null, "x", -1),
                    xrp.getAttributeIntValue(null, "y", -1)
                )
                "walls" -> {
                    val count = xrp.getAttributeIntValue(null, "count", 0)
                    CL.walls = Array(count) { Rect() }
                    CL.walls_big = Array(count) { Rect() }
                    xrp.next()
                    for (i in 0 until count) {
                        if (xrp.name == "wall") {
                            CL.walls[i] = Rect(
                                xrp.getAttributeIntValue(null, "left", -1),
                                xrp.getAttributeIntValue(null, "top", -1),
                                xrp.getAttributeIntValue(null, "right", -1),
                                xrp.getAttributeIntValue(null, "bottom", -1)
                            )
                            CL.walls_big[i] = CL.modifyRect(CL.walls[i])
                            xrp.next()
                            xrp.next()
                        }
                    }
                }
                "holes" -> {
                    val count = xrp.getAttributeIntValue(null, "count", 0)
                    CL.holes = Array(count) { Point() }
                    xrp.next()
                    for (i in 0 until count) {
                        if (xrp.name == "hole") {
                            CL.holes[i] = Point(
                                xrp.getAttributeIntValue(null, "x", -1),
                                xrp.getAttributeIntValue(null, "y", -1)
                            )
                            xrp.next()
                            xrp.next()
                        }
                    }
                }
                "background" -> CL.mIsFacet = xrp.getAttributeIntValue(null, "no", -1) == 0
            }
            while (xrp.eventType != 3) {
                xrp.next()
            }
            xrp.next()
        }
        xrp.close()
        CBall.updateWallInfo()
    }

    @Throws(Resources.NotFoundException::class)
    private fun fnCreateBG(): Bitmap {
        val activity = mActivity ?: throw IllegalStateException("Activity is null in fnCreateBG")
        val option = BitmapFactory.Options().apply {
            inScaled = false
            inMutable = true
        }
        val viewBmp = if (CL.mIsFacet) {
            BitmapFactory.decodeResource(activity.resources, R.drawable.facet, option)
        } else {
            BitmapFactory.decodeResource(activity.resources, R.drawable.maze, option)
        } ?: throw IllegalStateException("Failed to decode background bitmap")

        val offsetX = (viewBmp.width - CU.SCREEN_WIDTH) / 2f
        val offsetY = (viewBmp.height - CU.SCREEN_HEIGHT) / 2f

        val canvas = Canvas(viewBmp)
        if (CL.mIsFacet && !CL.isAcclerationInitialized) {
            CL.initAccleration(activity.resources)
        }

        val end = CL.end
        if (end != null) {
            val endX = end.x + offsetX.toInt()
            val endY = end.y + offsetY.toInt()
            val adjustedEnd = Point(endX, endY)
            fnDrawHoleOnBG(canvas, adjustedEnd, CU.END_RATIO * CU.END_RADIUS * 2.0f, mBmpEnd!!)
        } else {
            LogUtils.w(TTConstants.CB_LOADING_TAG, "fnCreateBG: CL.end is null, skipping end drawing")
        }

        val w = mBmpHole!!.width
        val h = mBmpHole!!.height
        val matrix = Matrix()
        val targetWidth = CU.HOLE_RATIO * CU.HOLE_RADIUS * 2.0f
        matrix.setScale(targetWidth / w, targetWidth / h)
        val scaledBmp = Bitmap.createBitmap(mBmpHole!!, 0, 0, w, h, matrix, true)
        for (i in CL.holes.indices) {
            val holeX = CL.holes[i].x + offsetX
            val holeY = CL.holes[i].y + offsetY
            canvas.drawBitmap(scaledBmp, holeX - targetWidth / 2.0f, holeY - targetWidth / 2.0f, null)
        }
        if (mNinePatchShadowDrawable != null) {
            val wallsRaw = Array(CL.walls.size) {
                Rect(
                    CL.walls[it].left + offsetX.toInt(),
                    CL.walls[it].top + offsetY.toInt(),
                    CL.walls[it].right + offsetX.toInt(),
                    CL.walls[it].bottom + offsetY.toInt()
                )
            }
            val clipExtraPx = 2
            val clipWalls = Array(wallsRaw.size) { idx ->
                Rect(
                    wallsRaw[idx].left - clipExtraPx,
                    wallsRaw[idx].top - clipExtraPx,
                    wallsRaw[idx].right + clipExtraPx,
                    wallsRaw[idx].bottom + clipExtraPx
                )
            }

            for (k in wallsRaw.indices) {
                val expandedLeft = wallsRaw[k].left - CU.WALL_PADDING_LEFT
                val expandedTop = wallsRaw[k].top - CU.WALL_PADDING_TOP
                val expandedRight = wallsRaw[k].right + CU.WALL_PADDING_RIGHT
                val expandedBottom = wallsRaw[k].bottom + CU.WALL_PADDING_BOTTOM
                val width = expandedRight - expandedLeft
                val height = expandedBottom - expandedTop

                if (width > 0 && height > 0) {
                    canvas.withSave {
                        for (j in wallsRaw.indices) {
                            if (j != k) {
                                @Suppress("DEPRECATION")
                                canvas.clipRect(clipWalls[j], android.graphics.Region.Op.DIFFERENCE)
                            }
                        }
                        mNinePatchShadowDrawable!!.bounds = Rect(expandedLeft, expandedTop, expandedRight, expandedBottom)
                        mNinePatchShadowDrawable!!.draw(canvas)
                    }
                } else {
                    LogUtils.w(TTConstants.CB_LOADING_TAG, "fnCreateBG: invalid wall[$k] ${wallsRaw[k]}, expanded=$expandedLeft,$expandedTop,$expandedRight,$expandedBottom")
                }
            }

            val displayMetrics = activity.resources.displayMetrics
            val isWideScreen =
                (displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels) > (16f / 9f)
            if (isWideScreen) {
                val edgeWallWidth = CU.WALL_PADDING_LEFT + CU.WALL_PADDING_RIGHT + 11
                val halfWidth = edgeWallWidth / 2
                val shift = 7
                val leftEdgeRect = Rect(
                    offsetX.toInt() - halfWidth - shift,
                    offsetY.toInt(),
                    offsetX.toInt() + halfWidth - shift,
                    offsetY.toInt() + CU.SCREEN_HEIGHT
                )
                val rightEdgeRect = Rect(
                    offsetX.toInt() + CU.SCREEN_WIDTH - halfWidth + shift,
                    offsetY.toInt(),
                    offsetX.toInt() + CU.SCREEN_WIDTH + halfWidth + shift,
                    offsetY.toInt() + CU.SCREEN_HEIGHT
                )
                mNinePatchShadowDrawable!!.bounds = leftEdgeRect
                mNinePatchShadowDrawable!!.draw(canvas)
                mNinePatchShadowDrawable!!.bounds = rightEdgeRect
                mNinePatchShadowDrawable!!.draw(canvas)
            }
        }

        if (CU.DEBUG) {
            val p = Paint()
            val dx = offsetX.toInt()
            val dy = offsetY.toInt()
            p.color = Color.argb(70, 0, 255, 0)
            canvas.drawRect(Rect(200 + dx, 225 + dy, 400 + dx, 375 + dy), p)
            p.color = Color.argb(130, 255, 0, 0)
            canvas.drawRect(Rect(880 + dx, 225 + dy, 1080 + dx, 375 + dy), p)
            p.color = Color.argb(70, 0, 0, 255)
            p.textSize = 40f
            p.textAlign = Paint.Align.CENTER
            val fm = p.fontMetrics
            val holeCenterY = 300f + dy - (fm.descent - fm.ascent) / 2f - fm.ascent
            canvas.drawText("HOLE", 300f + dx, holeCenterY, p)
            canvas.drawText("END", 980f + dx, holeCenterY, p)
            canvas.drawText("Lv:${CU.LEVEL}", 640f + dx, 50f + dy - (fm.descent - fm.ascent) / 2f - fm.ascent, p)
        }
        return viewBmp
    }

    companion object {
        private val LEVEL_XML_IDS = intArrayOf(
            R.xml.level001, R.xml.level002, R.xml.level003, R.xml.level004,
            R.xml.level005, R.xml.level006, R.xml.level007, R.xml.level008,
            R.xml.level009, R.xml.level010, R.xml.level011, R.xml.level012,
            R.xml.level013, R.xml.level014, R.xml.level015, R.xml.level016,
            R.xml.level017, R.xml.level018, R.xml.level019, R.xml.level020,
            R.xml.level021, R.xml.level022, R.xml.level023, R.xml.level024,
            R.xml.level025, R.xml.level026, R.xml.level027, R.xml.level028,
            R.xml.level029, R.xml.level030, R.xml.level031, R.xml.level032
        )

        private var mBmpEnd: Bitmap? = null
        private var mBmpHole: Bitmap? = null
        private var mNinePatchShadowDrawable: NinePatchDrawable? = null


        fun clearMemory() {
            mBmpHole?.recycle()
            mBmpHole = null
            mBmpEnd?.recycle()
            mBmpEnd = null
        }


        fun fnDrawHoleOnBG(canvas: Canvas, dstP: Point, dstD: Float, srcBmp: Bitmap) {
            val w = srcBmp.width
            val h = srcBmp.height
            val matrix = Matrix()
            matrix.setScale(dstD / w, dstD / h)
            val scaledBmp = Bitmap.createBitmap(srcBmp, 0, 0, w, h, matrix, true)
            canvas.drawBitmap(scaledBmp, dstP.x - dstD / 2.0f, dstP.y - dstD / 2.0f, null)
            if (scaledBmp !== srcBmp) {
                scaledBmp.recycle()
            }
        }
    }
}