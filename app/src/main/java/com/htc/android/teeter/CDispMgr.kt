package com.htc.android.teeter

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants
import java.lang.ref.WeakReference

class CDispMgr(activity: Activity) {

    private var mActivity: Activity? = activity
    private var mCSHandler: CScoreHandler? = null
    private var mEffHandler: EffectHandler? = EffectHandler(activity)
    private var mGamePage: CGamePage? = CGamePage(activity)
    private var mRankPage: CRankPage? = CRankPage(activity)
    private var mScorePage: CScorePage? = CScorePage(activity)

    private var mRootLayout: FrameLayout? = null
    private var mBackgroundView: ImageView? = null

    companion object {
        private const val EFF_AT_END = 201
        private const val EFF_AT_HOLE = 200
        private const val MSG_EFFECT = 100
        private const val GAME_DESIGN_WIDTH = 1280
        private const val GAME_DESIGN_HEIGHT = 720
    }

    fun fnShowGamePage() {
        val act = mActivity ?: return
        val gameView = mGamePage!!.fnCreateView()

        if (mRootLayout == null) {
            mRootLayout = FrameLayout(act)

            mBackgroundView = ImageView(act).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            }
            mRootLayout!!.addView(mBackgroundView)
        }

        fnUpdateBackground()

        val gameViewParent = gameView.parent as? ViewGroup
        gameViewParent?.removeView(gameView)
        val gameViewParams = FrameLayout.LayoutParams(GAME_DESIGN_WIDTH, GAME_DESIGN_HEIGHT).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        mRootLayout!!.addView(gameView, gameViewParams)

        gameView.viewTreeObserver.addOnGlobalLayoutListener {
            val rootWidth = mRootLayout?.width ?: 0
            val rootHeight = mRootLayout?.height ?: 0
            if (rootWidth > 0 && rootHeight > 0) {
                val scale = minOf(
                    rootWidth.toFloat() / GAME_DESIGN_WIDTH,
                    rootHeight.toFloat() / GAME_DESIGN_HEIGHT
                )
                gameView.pivotX = 0f
                gameView.pivotY = 0f
                gameView.scaleX = scale
                gameView.scaleY = scale
                val tx = (rootWidth - GAME_DESIGN_WIDTH * scale) / 2f
                val ty = (rootHeight - GAME_DESIGN_HEIGHT * scale) / 2f
                gameView.translationX = tx
                gameView.translationY = ty
                CU.GAME_SCALE = scale
                CU.GAME_OFFSET_X = tx
                CU.GAME_OFFSET_Y = ty
            }
        }

        act.setContentView(mRootLayout)
    }

    private fun fnUpdateBackground() {
        val bgView = mBackgroundView ?: return
        val bgBmp = CU.BG_BMP
        if (bgBmp != null && !bgBmp.isRecycled) {
            bgView.setImageBitmap(bgBmp)
            bgView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    fun fnClearGamePage() {
        mGamePage!!.fnClearGameView()
        mBackgroundView?.setImageBitmap(null)
        mRootLayout = null
        mBackgroundView = null
    }

    fun fnInvalidate() {
        mGamePage!!.fnInvalidate()
    }

    fun fnShowScorePage() {
        fnAddViewIntoSwitcher(mScorePage!!.fnCreateView())
        Handler(Looper.getMainLooper()).postDelayed({
            mScorePage!!.fnInvalidate()
        }, 100L)
        var mBGLoading: Thread? = null
        if (CU.LEVEL < CU.LEVEL_COUNT) {
            val act = mActivity!!
            val i = CU.LEVEL + 1
            CU.LEVEL = i
            mBGLoading = CBGLoadingThread(act, i)
            mBGLoading.start()
        }
        mCSHandler = CScoreHandler(mBGLoading, this)
        val msg = Message.obtain(mCSHandler)
        mCSHandler!!.sendMessageDelayed(msg, 3000L)
    }

    fun fnPlayEndingEffect(endAnimEndMsg: Message) {
        mGamePage!!.fnPlayEndingAnimation(endAnimEndMsg)
        val endMsg = Message.obtain(mEffHandler, MSG_EFFECT)
        endMsg.arg1 = EFF_AT_END
        endMsg.sendToTarget()
    }

    fun fnAttachBall(ball: CBall) {
        mGamePage!!.fnAttachBall(ball)
    }

    fun fnPlayHoleEffect(holeAnimEndMsg: Message) {
        val holePos: Point? = if (CL.HOLE_INDEX >= 0) CL.holes[CL.HOLE_INDEX] else null
        mGamePage!!.fnPlayHoleAnimation(holePos, holeAnimEndMsg)
        val holeMsg = Message.obtain(mEffHandler, MSG_EFFECT)
        holeMsg.arg1 = EFF_AT_HOLE
        holeMsg.sendToTarget()
    }

    private fun fnAddViewIntoSwitcher(aView: View) {
        mActivity!!.setContentView(aView)
    }

    private class EffectHandler(act: Activity) : Handler(Looper.getMainLooper()) {
        private var mEndPlayer: MediaPlayer? = MediaPlayer.create(act, R.raw.level_complete)
        private var mHolePlayer: MediaPlayer? = MediaPlayer.create(act, R.raw.hole)
        private var mFinishPlayer: MediaPlayer? = MediaPlayer.create(act, R.raw.game_complete)
        private var mVibrator: Vibrator?

        init {
            mEndPlayer?.isLooping = false
            mHolePlayer?.isLooping = false
            mFinishPlayer?.isLooping = false
            mVibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = act.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                act.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_EFFECT -> {
                    when (msg.arg1) {
                        EFF_AT_HOLE -> {
                            vibratePattern(CU.VIBRATION_HOLE)
                            mHolePlayer?.seekTo(0)
                            mHolePlayer?.start()
                        }
                        EFF_AT_END -> {
                            vibratePattern(CU.VIBRATION_END)
                            if (CU.LEVEL >= CU.LEVEL_COUNT) {
                                mFinishPlayer?.seekTo(0)
                                mFinishPlayer?.start()
                                CU.GAME_OVER = true
                            } else {
                                mEndPlayer?.seekTo(0)
                                mEndPlayer?.start()
                            }
                        }
                    }
                }
            }
        }

        private fun vibratePattern(pattern: LongArray) {
            val vibrator = mVibrator ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }

        fun clearMemory() {
            mEndPlayer?.release()
            mEndPlayer = null
            mHolePlayer?.release()
            mHolePlayer = null
            mFinishPlayer?.release()
            mFinishPlayer = null
            mVibrator = null
        }
    }

    private class CScoreHandler(
        private var mThread: Thread?,
        dispMgr: CDispMgr
    ) : Handler(Looper.getMainLooper()) {
        private val mDispMgrRef = WeakReference(dispMgr)

        override fun handleMessage(msg: Message) {
            val dispMgr = mDispMgrRef.get()
            if (mThread == null) {
                try {
                    dispMgr?.fnShowRankPage()
                } catch (e: Throwable) {
                    LogUtils.e(TTConstants.DISP_MGR_TAG, "fnCreateView error", e)
                }
                return
            }
            try {
                mThread!!.join()
            } catch (e: InterruptedException) {
                LogUtils.e(TTConstants.DISP_MGR_TAG, "CScoreHandler join interrupted", e)
            }
            (dispMgr?.mActivity as? CTeeterActivity)?.fnExternalGameFlow(4)
        }
    }

    fun fnShowRankPage() {
        fnAddViewIntoSwitcher(mRankPage!!.fnCreateView())
    }

    fun clearMemory() {
        if (mEffHandler != null) {
            mEffHandler!!.removeMessages(MSG_EFFECT)
            mEffHandler!!.clearMemory()
        }
        if (mCSHandler != null) {
            mCSHandler!!.removeMessages(0)
        }
        mGamePage?.clearMemory()
        mScorePage?.clearMemory()
        mRankPage?.clearMemory()
        mActivity = null
        mGamePage = null
        mScorePage = null
        mRankPage = null
        mEffHandler = null
        mCSHandler = null
    }
}
