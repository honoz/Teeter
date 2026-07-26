package com.htc.android.teeter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.widget.FrameLayout

class GameView(context: Context) : FrameLayout(context) {

    private var mBallBitmap: Bitmap? = null
    private val mBallPoint = Point()
    private var mFadeInStart: Long = 0
    private val mPaint = Paint()
    private var mShowBall: Boolean = false

    init {
        setWillNotDraw(false)
    }

    fun showBall(show: Boolean) {
        mShowBall = show
        if (mShowBall) {
            mFadeInStart = System.currentTimeMillis()
        }
    }

    fun setBallSize(newWidth: Int, newHeight: Int) {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.ball, opts) ?: return
        val width = source.width
        val height = source.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = Matrix().apply { postScale(scaleWidth, scaleHeight) }
        val scaled = Bitmap.createBitmap(source, 0, 0, width, height, matrix, false)
        mBallBitmap?.recycle()
        mBallBitmap = scaled
        if (scaled !== source) {
            source.recycle()
        }
    }

    fun setBallPos(l: Int, t: Int) {
        if (mBallBitmap == null) return
        if (mBallPoint.x != l || mBallPoint.y != t) {
            mBallPoint.x = l
            mBallPoint.y = t
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = mBallBitmap ?: return
        if (mShowBall) {
            val timeElapse = System.currentTimeMillis() - mFadeInStart
            if (timeElapse < 500) {
                mPaint.alpha = (timeElapse / 2).toInt()
                canvas.drawBitmap(bitmap, mBallPoint.x.toFloat(), mBallPoint.y.toFloat(), mPaint)
                invalidate()
                return
            }
            canvas.drawBitmap(bitmap, mBallPoint.x.toFloat(), mBallPoint.y.toFloat(), null)
        }
    }
}
