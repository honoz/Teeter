package com.htc.android.teeter

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toDrawable
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants

class CCoverActivity : AppCompatActivity() {

    companion object {
        private const val NO_SENSOR = 0
        private const val GAME_START = 1
        private const val GAME_EXIT = 2
        private const val GAME_ENTER = 3
    }

    private var hasSensor = false
    private var ivBall: ImageView? = null
    private var mDataLoaderThread: Thread? = null
    private var mEndPlayer: MediaPlayer? = null
    private var mHoleDrw: CAnimDra? = null
    private var mMainView: FrameLayout? = null
    private var onPause = false
    private var sm: SensorManager? = null
    private var startGame = false
    private var ivSplashBg: ImageView? = null

    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GAME_START -> {
                    val mainView = mMainView
                    if (mainView != null) {
                        val ballParams = calculateBallPosition()
                        mainView.addView(ivBall!!, ballParams)
                    } else {
                        LogUtils.d(TTConstants.COVER_ACTIVITY_TAG, "layout is null")
                    }
                    val holeDrw = mHoleDrw
                    if (holeDrw != null) {
                        holeDrw.start()
                    } else {
                        LogUtils.d(TTConstants.COVER_ACTIVITY_TAG, "mHoleDrw is null")
                    }
                }
                GAME_EXIT -> finish()
                GAME_ENTER -> gameStart()
            }
        }
    }

    private val sl = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent?) {}
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtils.init(this)
        hideSystemUI()
        mMainView = FrameLayout(this)
        setContentView(mMainView!!)
        setupSplashBackground()
        hasSensor = checkHasSensor()
        if (!hasSensor) {
            @Suppress("DEPRECATION")
            mHandler.sendEmptyMessageDelayed(GAME_EXIT, 5000L)
            createNoSensorDialog().show()
        } else {
            mDataLoaderThread = Thread {
                prepareAnimationData()
                if (hasSensor) {
                    mHandler.sendEmptyMessageDelayed(GAME_START, 500L)
                }
            }
            mDataLoaderThread?.start()
            onPause = false
            startGame = false
            LogUtils.toQualityBoard(TTConstants.QUALITY_BOARD_TAG, true, "onCreate")
        }
    }

    private fun prepareAnimationData() {
        val res = resources
        mHoleDrw = CAnimDra()
        val option = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val splashIds = intArrayOf(
            R.drawable.splash_0001, R.drawable.splash_0002, R.drawable.splash_0003,
            R.drawable.splash_0004, R.drawable.splash_0005, R.drawable.splash_0006,
            R.drawable.splash_0007, R.drawable.splash_0008, R.drawable.splash_0009,
            R.drawable.splash_0010, R.drawable.splash_0011, R.drawable.splash_0012,
            R.drawable.splash_0013, R.drawable.splash_0014, R.drawable.splash_0015,
            R.drawable.splash_0016, R.drawable.splash_0017, R.drawable.splash_0018,
            R.drawable.splash_0019, R.drawable.splash_0020, R.drawable.splash_0021,
            R.drawable.splash_0022, R.drawable.splash_0023, R.drawable.splash_0024,
            R.drawable.splash_0025, R.drawable.splash_0026, R.drawable.splash_0027,
            R.drawable.splash_0028, R.drawable.splash_0029, R.drawable.splash_0030,
            R.drawable.splash_0031, R.drawable.splash_0032, R.drawable.splash_0033,
            R.drawable.splash_0034, R.drawable.splash_0035, R.drawable.splash_0036,
            R.drawable.splash_0037, R.drawable.splash_0038, R.drawable.splash_0039,
            R.drawable.splash_0040, R.drawable.splash_0041, R.drawable.splash_0042,
            R.drawable.splash_0043, R.drawable.splash_0044, R.drawable.splash_0045,
            R.drawable.splash_0046, R.drawable.splash_0047, R.drawable.splash_0048,
            R.drawable.splash_0049, R.drawable.splash_0050, R.drawable.splash_0051,
            R.drawable.splash_0052, R.drawable.splash_0053, R.drawable.splash_0054,
            R.drawable.splash_0055, R.drawable.splash_0056, R.drawable.splash_0057,
            R.drawable.splash_0058, R.drawable.splash_0059, R.drawable.splash_0060
        )
        for (i in 0 until 60) {
            val aBmp = BitmapFactory.decodeResource(res, splashIds[i], option)
            if (i <= 29) {
                mHoleDrw?.addFrame(aBmp.toDrawable(res), 25)
            } else {
                mHoleDrw?.addFrame(aBmp.toDrawable(res), 50)
            }
        }
        ivBall = ImageView(this).apply {
            @Suppress("DEPRECATION")
            setBackgroundDrawable(mHoleDrw)
        }
        mEndPlayer = MediaPlayer.create(this, R.raw.level_complete)
    }

    override fun onResume() {
        super.onResume()
        onPause = false
        LogUtils.toQualityBoard(TTConstants.QUALITY_BOARD_TAG, true, "onResume")
    }

    override fun onWindowFocusChanged(focus: Boolean) {
        if (focus) {
            hideSystemUI()
            if (startGame) {
                startGame = false
                mHandler.sendEmptyMessageDelayed(GAME_ENTER, 200L)
            }
        } else {
            mHandler.removeMessages(GAME_ENTER)
        }
    }

    override fun onPause() {
        super.onPause()
        onPause = true
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        sm?.unregisterListener(sl)
        sm = null
        try {
            mDataLoaderThread?.join()
        } catch (e: InterruptedException) {
            LogUtils.e(TTConstants.COVER_ACTIVITY_TAG, "onDestroy: thread join interrupted", e)
        } finally {
            mMainView?.removeAllViews()
            mMainView = null
            mDataLoaderThread = null
            ivBall = null
            ivSplashBg = null
            mHoleDrw = null
            mEndPlayer?.release()
            mEndPlayer = null
        }
        LogUtils.toQualityBoard(TTConstants.QUALITY_BOARD_TAG, true, "onDestroy")
    }

    private fun gameStart() {
        val next = Intent(this, CTeeterActivity::class.java)
        startActivity(next)
        @Suppress("DEPRECATION")
        mMainView?.setBackgroundDrawable(null)
        @Suppress("DEPRECATION")
        ivBall?.setBackgroundDrawable(null)
        val holeDrw = mHoleDrw
        if (holeDrw != null) {
            for (i in 0 until holeDrw.numberOfFrames) {
                val frame = holeDrw.getFrame(i)
                if (frame is BitmapDrawable) {
                    frame.bitmap.recycle()
                }
            }
        }
        finish()
    }

    inner class CAnimDra : AnimationDrawable() {
        private var mPlayingCount = 0

        override fun run() {
            super.run()
            if (mPlayingCount == 30 && mEndPlayer != null) {
                mEndPlayer?.start()
            }
            if (mPlayingCount == 59) {
                fadeout()
            }
            mPlayingCount++
        }
    }

    private fun fadeout() {
        val an2 = AnimationUtils.loadAnimation(this, android.R.anim.fade_out).apply {
            duration = 1000L
            startOffset = 500L
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationEnd(animation: Animation?) {
                    if (!onPause) {
                        gameStart()
                    } else {
                        startGame = true
                    }
                }
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationStart(animation: Animation?) {}
            })
        }
        mMainView?.startAnimation(an2)
    }

    private fun checkHasSensor(): Boolean {
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensorEnable = sm?.registerListener(sl, sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST) ?: false
        LogUtils.e(TTConstants.TEETER, "registerListener return = $sensorEnable")
        return sensorEnable
    }

    private fun createNoSensorDialog(): android.app.Dialog {
        val aBuilder = AlertDialog.Builder(this).apply {
            setTitle(R.string.private_app)
            setCancelable(false)
            setMessage(R.string.str_no_sensor)
            setPositiveButton(R.string.str_btn_quit) { _, _ -> finish() }
        }
        val dialog = aBuilder.create()
        dialog.setOnShowListener {
            @Suppress("DEPRECATION")
            dialog.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
        return dialog
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCreateDialog(id: Int): android.app.Dialog? {
        if (id != NO_SENSOR) return null
        return createNoSensorDialog()
    }

    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = true

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    private fun setupSplashBackground() {
        val res = resources
        val option = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeResource(res, R.drawable.splash_bg, option) ?: return
        ivSplashBg = object : AppCompatImageView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val d = drawable
                if (d != null) {
                    val h = MeasureSpec.getSize(heightMeasureSpec)
                    val dw = d.intrinsicWidth
                    val dh = d.intrinsicHeight
                    if (dh > 0) {
                        val w = (h.toFloat() / dh * dw).toInt()
                        setMeasuredDimension(w, h)
                        return
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        mMainView?.addView(ivSplashBg, 0)
    }

    private fun calculateBallPosition(): FrameLayout.LayoutParams {
        val bgView = ivSplashBg
        if (bgView == null || bgView.width == 0 || bgView.height == 0) {
            return FrameLayout.LayoutParams(0, 0)
        }

        val res = resources
        val option = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeResource(res, R.drawable.splash_bg, option)
            ?: return FrameLayout.LayoutParams(0, 0)
        val bgW = bmp.width
        val bgH = bmp.height

        val bgViewLeft = bgView.left
        val bgViewTop = bgView.top
        val bgViewW = bgView.width
        val bgViewH = bgView.height

        val designW = res.getDimensionPixelSize(R.dimen.original_design_width).toFloat()
        val designH = res.getDimensionPixelSize(R.dimen.original_design_height).toFloat()
        val ballX = res.getDimensionPixelSize(R.dimen.loading_position_x).toFloat()
        val ballY = res.getDimensionPixelSize(R.dimen.loading_position_y).toFloat()
        val ballW = res.getDimensionPixelSize(R.dimen.loading_width).toFloat()
        val ballH = res.getDimensionPixelSize(R.dimen.loading_height).toFloat()

        val bgLeftPad = (bgW - designW) / 2.0f
        val bgTopPad = (bgH - designH) / 2.0f

        val scaleX = bgViewW.toFloat() / bgW
        val scaleY = bgViewH.toFloat() / bgH

        val ballWidth = (ballW * scaleX).toInt()
        val ballHeight = (ballH * scaleY).toInt()
        val marginX = (bgViewLeft + (bgLeftPad + ballX) * scaleX).toInt()
        val marginY = (bgViewTop + (bgTopPad + ballY) * scaleY).toInt()

        return FrameLayout.LayoutParams(ballWidth, ballHeight).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = marginX
            topMargin = marginY
        }
    }

}
