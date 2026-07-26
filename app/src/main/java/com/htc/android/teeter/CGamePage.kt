package com.htc.android.teeter

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.drawable.AnimationDrawable
import android.os.Message
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable

internal class CGamePage(activity: Activity) {

    private var ballPos: Point? = Point()
    private var mActivity: Activity? = activity
    private var mBall: CBall? = null
    private var mGameView: GameView? = GameView(activity)
    private var mHoleAnimView: ImageView? = null
    private val half: Int get() = (CU.BALL_RATIO * CU.BALL_RADIUS).toInt()
    private var mEndAnimDr: CAnimDrawable? = CAnimDrawable()
    private var mHoleAnimDr: CAnimDrawable? = CAnimDrawable()
    private var mCurrentAnimView: ImageView? = null

    init {
        val option = BitmapFactory.Options().apply { inScaled = false }
        val endAnimBmp = BitmapFactory.decodeResource(activity.resources, R.drawable.end_anim, option)
        val squareCount = endAnimBmp.width / endAnimBmp.height
        val height = endAnimBmp.height
        for (i in 0 until squareCount) {
            val aBmp = Bitmap.createBitmap(endAnimBmp, i * height, 0, height, height)
            val dr = aBmp.toDrawable(activity.resources)
            mEndAnimDr?.addFrame(dr, 50)
        }
        endAnimBmp.recycle()
        val holeAnimIds = intArrayOf(
            R.drawable.hole_anim_001, R.drawable.hole_anim_002,
            R.drawable.hole_anim_003, R.drawable.hole_anim_004,
            R.drawable.hole_anim_005, R.drawable.hole_anim_006,
            R.drawable.hole_anim_007, R.drawable.hole_anim_008,
            R.drawable.hole_anim_009, R.drawable.hole_anim_010,
            R.drawable.hole_anim_011, R.drawable.hole_anim_012,
            R.drawable.hole_anim_013, R.drawable.hole_anim_014,
            R.drawable.hole_anim_015, R.drawable.hole_anim_016,
            R.drawable.hole_anim_017, R.drawable.hole_anim_018,
            R.drawable.hole_anim_019, R.drawable.hole_anim_020
        )
        for (i2 in 0 until 20) {
            val aBmp2 = BitmapFactory.decodeResource(activity.resources, holeAnimIds[i2], option)
            mHoleAnimDr?.addFrame(aBmp2.toDrawable(activity.resources), 45 - i2 * 2)
        }
    }

    fun fnCreateView(): View {
        check(CU.BG_BMP != null)
        val activity = mActivity ?: throw IllegalStateException("Activity is null")
        val gameView = mGameView ?: throw IllegalStateException("GameView is null")
        gameView.setBallSize(half * 2, half * 2)
        mHoleAnimView = ImageView(activity).also {
            val diameter = (CU.HOLE_ANIM_RATIO * CU.BALL_RADIUS).toInt() * 2
            val holeParam = FrameLayout.LayoutParams(diameter, diameter).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            gameView.addView(it, holeParam)
        }
        return gameView
    }

    fun fnClearGameView() {
        mGameView?.removeAllViews()
        mBall = null
    }

    fun fnAttachBall(ball: CBall) {
        mBall = ball
        mGameView?.showBall(true)
        fnInvalidate()
        @Suppress("DEPRECATION")
        mHoleAnimView?.setBackgroundDrawable(null)
    }

    fun fnInvalidate() {
        val ball = mBall ?: return
        val pos = ballPos ?: return
        ball.fnGetCenter(pos)
        mGameView?.setBallPos(pos.x - half, pos.y - half)
    }

    fun fnPlayEndingAnimation(playEndMsg: Message) {
        val activity = mActivity ?: return
        val gameView = mGameView ?: return
        gameView.showBall(false)
        val aImageView = ImageView(activity)
        mCurrentAnimView = aImageView
        @Suppress("DEPRECATION")
        aImageView.setBackgroundDrawable(mEndAnimDr)
        val radius = (CU.END_ANIM_RATIO * CU.BALL_RADIUS).toInt()
        val endParam = FrameLayout.LayoutParams(radius * 2, radius * 2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (CL.end?.x ?: 0) - radius
            topMargin = (CL.end?.y ?: 0) - radius
        }
        gameView.addView(aImageView, endParam)
        gameView.showBall(false)
        mEndAnimDr?.fnSetPlayEndMsg(playEndMsg)
        mEndAnimDr?.start()
    }

    fun fnPlayHoleAnimation(holePos: Point?, playEndMsg: Message) {
        val gameView = mGameView ?: return
        val holeAnimView = mHoleAnimView ?: return
        mCurrentAnimView = holeAnimView
        gameView.showBall(false)
        if (holePos != null) {
            mHoleAnimDr?.fnSetPlayEndMsg(playEndMsg)
            val radius = (CU.HOLE_ANIM_RATIO * CU.BALL_RADIUS).toInt()
            val param = FrameLayout.LayoutParams(radius * 2, radius * 2).apply {
                leftMargin = holePos.x - radius
                topMargin = holePos.y - radius
                gravity = Gravity.TOP or Gravity.START
            }
            @Suppress("DEPRECATION")
            holeAnimView.setBackgroundDrawable(mHoleAnimDr)
            gameView.updateViewLayout(holeAnimView, param)
            holeAnimView.pivotX = holeAnimView.width / 2f
            holeAnimView.pivotY = holeAnimView.width / 2f
            holeAnimView.rotation = mBall?.getInHoleDegree()?.toFloat() ?: 0f
            mHoleAnimDr?.start()
        }
    }

    fun clearMemory() {
        ballPos = null
        mActivity = null
        mBall?.clearMemory()
        mBall = null
        mEndAnimDr = null
        mGameView = null
        mHoleAnimDr = null
        mHoleAnimView = null
    }

    inner class CAnimDrawable : AnimationDrawable() {
        private var mPlayEndMsg: Message? = null
        private var mPlayingCount = 0

        fun fnSetPlayEndMsg(msg: Message) {
            mPlayEndMsg = msg
        }

        override fun run() {
            super.run()
            if (mPlayingCount >= numberOfFrames - 1) {
                mPlayingCount = 0
                @Suppress("DEPRECATION")
                mCurrentAnimView?.setBackgroundDrawable(null)
                mCurrentAnimView?.invalidate()
                mCurrentAnimView = null
                mPlayEndMsg?.sendToTarget()
                stop()
                return
            }
            mPlayingCount++
        }
    }
}
