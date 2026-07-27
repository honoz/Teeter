package com.htc.android.teeter

import android.app.Dialog
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.ref.WeakReference

class CTeeterActivity : AppCompatActivity() {

    private val dialogPauseDefaultOption = 291
    private val dialogRestartDefaultOption = 1110
    private val dialogRestartOption = 1929
    private val dialogPause = 1
    private val dialogContinue = 3
    private var mAudioManager: AudioManager? = null
    private var mGame: CGameModel? = null
    private var mHandler: LightHandler? = null
    private var mPowerManager: PowerManager? = null
    private var wlON: PowerManager.WakeLock? = null
    private var mToast: Toast? = null

    private val mDialogHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                dialogPauseDefaultOption -> {
                    mGame!!.fnStart(CGameModel.START_CONTINUE)
                    LogUtils.e(TTConstants.TEETER_ACTIVITY_TAG, "DIALOG_PAUSE_DEFAULT_OPTION")
                }

                dialogRestartDefaultOption -> {
                    mGame!!.fnStart(CGameModel.START_CONTINUE)
                    LogUtils.e(TTConstants.TEETER_ACTIVITY_TAG, "DIALOG_RESTART_DEFAULT_OPTION")
                }

                dialogRestartOption -> {
                    CU.LEVEL = 1
                    CU.GAME_OVER = false
                    CU.TIMER_GO = true
                    mGame!!.fnStart(CGameModel.START_NEWGAME_NEED_INIT)
                }
            }
        }
    }

    @Throws(Resources.NotFoundException::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        volumeControlStream = 3
        hideSystemUI()
        LogUtils.d(TTConstants.TEETER_ACTIVITY_TAG, "onCreate")
        CU.GAME_OVER =
            PreferenceManager.getDefaultSharedPreferences(this).getBoolean("GAME_OVER", false)
        CU.WALL_PADDING_TOP = resources.getDimensionPixelSize(R.dimen.wall_padding_top)
        CU.WALL_PADDING_LEFT = resources.getDimensionPixelSize(R.dimen.wall_padding_left)
        CU.WALL_PADDING_BOTTOM = resources.getDimensionPixelSize(R.dimen.wall_padding_bottom)
        CU.WALL_PADDING_RIGHT = resources.getDimensionPixelSize(R.dimen.wall_padding_right)
        @Suppress("DiscouragedApi")
        val shadowBottomId = resources.getIdentifier("shadow_top", "drawable", packageName)
        @Suppress("DiscouragedApi")
        val shadowRightId = resources.getIdentifier("shadow_right", "drawable", packageName)
        if (shadowBottomId != 0) {
            CU.SHADOW_WIDTH = getImageWidth(shadowBottomId)
        }
        if (shadowRightId != 0) {
            CU.SHADOW_HEIGHT = getImageHeight(shadowRightId)
        }
        try {
            fnLoadConfig()
        } catch (e: IOException) {
            LogUtils.e(TTConstants.TEETER_ACTIVITY_TAG, "fnLoadConfig IOException", e)
        } catch (e: XmlPullParserException) {
            LogUtils.e(TTConstants.TEETER_ACTIVITY_TAG, "fnLoadConfig XmlPullParserException", e)
        }
        mGame = CGameModel(this)
        check(CU.LEVEL_COUNT != 0) { "LEVEL_COUNT must not be 0" }
        mAudioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        mGame!!.fnInitialize()
        mHandler = LightHandler(this)
        mPowerManager = getSystemService(POWER_SERVICE) as PowerManager
        wlON = mPowerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.htc.android.teeter:${TTConstants.TEETER_ACTIVITY_TAG}")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun getImageHeight(id: Int): Int {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, id, opts)
        return opts.outHeight
    }

    private fun getImageWidth(id: Int): Int {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, id, opts)
        return opts.outWidth
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean("GAME_OVER", CU.GAME_OVER)
        }
        mGame!!.clearMemory()
        mGame = null
        mAudioManager = null
        mPowerManager = null
        wlON = null
        if (mHandler != null) {
            mHandler!!.removeMessages(MSG_OFF)
            mHandler!!.removeMessages(MSG_ON)
        }
        mHandler = null
        val bgBmp = CU.BG_BMP
        bgBmp?.recycle()
        CU.BG_BMP = null
        CBGLoadingThread.clearMemory()
        super.onDestroy()
        LogUtils.d(TTConstants.TEETER_ACTIVITY_TAG, "onDestroy")
    }

    override fun onResume() {
        super.onResume()
        mGame!!.unlockTimer("Activity-onResume")
        LogUtils.d(TTConstants.TEETER_ACTIVITY_TAG, "onresume state=${mGame!!.fnGetGameState()}")
        window.addFlags(128)
        if (wlON != null && !wlON!!.isHeld) {
            wlON!!.acquire(600000)
        }
        when (mGame!!.fnGetGameState()) {
            CGameModel.STATE_INITIALIZED -> mGame!!.fnStart(CGameModel.START_NEWGAME)
            CGameModel.STATE_FINISH_LEVEL -> mGame!!.fnStart(CGameModel.START_NEXTLEVEL)
            CGameModel.STATE_STOPPED, CGameModel.STATE_UNINITIALIZED, CGameModel.STATE_PAUSED -> {
                @Suppress("DEPRECATION")
                showDialog(dialogContinue)
            }
            else -> {}
        }
        LogUtils.d(TTConstants.TEETER_ACTIVITY_TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        mGame!!.lockTimer("Activity-onPause")
        mGame!!.stopSensor()
        mGame!!.fnStop()
        if (wlON != null && wlON!!.isHeld) {
            wlON!!.release()
        }
        LogUtils.d(TTConstants.TEETER_ACTIVITY_TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
    }

    @Deprecated("Deprecated")
    @Suppress("DEPRECATION")
    override fun onCreateDialog(id: Int): Dialog? {
        return when (id) {
            dialogPause -> {
                val aBuilder = AlertDialog.Builder(this)
                aBuilder.setTitle(R.string.private_app)
                aBuilder.setMessage(R.string.str_msg_quit)
                aBuilder.setPositiveButton(R.string.str_btn_quit) { _, _ ->
                    finish()
                }
                if (CU.DEBUG) {
                    aBuilder.setNeutralButton("JUMP") { _, _ ->
                        CU.TIMER_GO = true
                        val newLevel = if (CU.LEVEL >= 32) 1 else CU.LEVEL + 1
                        CU.LEVEL = newLevel
                        mGame!!.fnStart(CGameModel.START_NEWGAME_NEED_INIT)
                    }
                }
                aBuilder.setNegativeButton(android.R.string.cancel) { _, _ ->
                    mGame!!.fnStart(CGameModel.START_CONTINUE)
                }
                val dialog = aBuilder.create()
                setupDialogImmersive(dialog)
                dialog.setOnCancelListener {
                    mDialogHandler.obtainMessage(dialogPauseDefaultOption).sendToTarget()
                }
                dialog
            }

            dialogContinue -> {
                val aBuilder2 = AlertDialog.Builder(this)
                aBuilder2.setTitle(R.string.private_app)
                aBuilder2.setMessage(R.string.str_msg_continue)
                if (!CU.GAME_OVER) {
                    aBuilder2.setPositiveButton(R.string.str_btn_resume) { _, _ ->
                        mGame!!.fnStart(CGameModel.START_CONTINUE)
                    }
                }
                aBuilder2.setNegativeButton(R.string.str_btn_restart) { _, _ ->
                    CU.LEVEL = 1
                    CU.GAME_OVER = false
                    CU.TIMER_GO = true
                    mGame!!.fnStart(CGameModel.START_NEWGAME_NEED_INIT)
                }
                val dialog2 = aBuilder2.create()
                setupDialogImmersive(dialog2)
                dialog2.setOnCancelListener {
                    val cancelMsgWhat = if (CU.GAME_OVER) dialogRestartOption else dialogRestartDefaultOption
                    mDialogHandler.obtainMessage(cancelMsgWhat).sendToTarget()
                }
                dialog2
            }

            else -> null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (CU.TOUCHABLE) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val gameX = (event.x - CU.GAME_OFFSET_X) / CU.GAME_SCALE
                    val gameY = (event.y - CU.GAME_OFFSET_Y) / CU.GAME_SCALE

                    if (CU.DEBUG && gameX in 200f..400f && gameY in 225f..375f) {
                        CU.HOLE_ON = !CU.HOLE_ON
                        mToast?.cancel()
                        mToast = if (CU.HOLE_ON) {
                            Toast.makeText(this, "Hole ON", Toast.LENGTH_SHORT).apply { show() }
                        } else {
                            Toast.makeText(this, "Hole OFF", Toast.LENGTH_SHORT).apply { show() }
                        }
                    } else if (CU.DEBUG && gameX in 880f..1080f && gameY in 225f..375f) {
                        CU.END_ON = !CU.END_ON
                        mToast?.cancel()
                        mToast = if (CU.END_ON) {
                            Toast.makeText(this, "End ON", Toast.LENGTH_SHORT).apply { show() }
                        } else {
                            Toast.makeText(this, "End OFF", Toast.LENGTH_SHORT).apply { show() }
                        }
                    } else {
                        mGame!!.fnStop()
                        mGame!!.lockTimer("Activity-onTouchEvent")
                        mGame!!.gamePause()
                        @Suppress("DEPRECATION")
                        showDialog(dialogPause)
                    }
                }
            }
        }
        return CU.TOUCHABLE
    }

    private class LightHandler(activity: CTeeterActivity) : Handler(Looper.getMainLooper()) {
        private val activityRef = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            val activity = activityRef.get() ?: return
            when (msg.what) {
                MSG_ON -> activity.window.addFlags(128)
                MSG_OFF -> activity.window.clearFlags(128)
            }
        }
    }

    fun turnLight(on: Boolean) {
        val msg = Message.obtain(mHandler)
        msg.what = if (on) MSG_ON else MSG_OFF
        msg.sendToTarget()
    }

    fun fnExternalGameFlow(nCase: Int) {
        when (nCase) {
            2 -> {
                CU.LEVEL = 1
                CU.GAME_OVER = false
                CU.TIMER_GO = true
                mGame!!.fnStart(CGameModel.START_NEWGAME_NEED_INIT)
            }

            3 -> {
                CU.LEVEL = 1
                CU.GAME_OVER = false
                mGame!!.gameFinish()
                CS.sfnReset()
                finish()
            }

            4 -> {
                mGame!!.fnStart(CGameModel.START_NEXTLEVEL)
            }

            else -> {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            hideSystemUI()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    @Throws(XmlPullParserException::class, Resources.NotFoundException::class, IOException::class)
    private fun fnLoadConfig() {
        val config = when {
            CU.SCREEN_WIDTH >= 1920 && CU.SCREEN_HEIGHT >= 1080 -> R.xml.config_fullhd
            else -> R.xml.config_hd
        }
        val xrp = resources.getXml(config)
        while (xrp.next() != 2) { /* advance to first START_TAG */ }
        xrp.next()
        while (xrp.eventType != 3) {
            while (xrp.eventType != 2) {
                if (xrp.eventType != 1) {
                    xrp.next()
                } else {
                    return
                }
            }
            when (xrp.name) {
                "level" -> CU.LEVEL_COUNT = xrp.getAttributeIntValue(null, "count", 0)
                "ball" -> {
                    CU.BALL_RADIUS = xrp.getAttributeIntValue(null, "radius", 0)
                    CU.BALL_RADIUS_BIG = CU.s2b(CU.BALL_RADIUS)
                }

                "hole" -> CU.HOLE_RADIUS = xrp.getAttributeIntValue(null, "radius", 0)
                "ending" -> CU.END_RADIUS = xrp.getAttributeIntValue(null, "radius", 0)
                "gravity" -> CU.GRAVITY_FACTOR = xrp.getAttributeIntValue(null, "value", 0)
                "friction" -> CU.FRICTION_FACTOR = xrp.getAttributeIntValue(null, "value", 0)
                "bounce_rate" -> CU.BOUNCE_RATE = xrp.getAttributeFloatValue(null, "value", 0.0f)
                "speed_limit" -> CU.MAX_SPEED = xrp.getAttributeIntValue(null, "value", 0)
                "vibrate_speed" -> {
                    CU.VIBRATION_ACTIVE_SPEED = xrp.getAttributeIntValue(null, "value", 0)
                    CU.VIBRATION_ACTIVE_SPEED_GROUND = CU.VIBRATION_ACTIVE_SPEED / 2
                }

                "v_hit" -> CU.VIBRATION_DURATION = xrp.getAttributeIntValue(null, "value", 0)
                "v_hole" -> {
                    val count = xrp.getAttributeIntValue(null, "length", 0)
                    CU.VIBRATION_HOLE = LongArray(count)
                    xrp.next()
                    for (i in 0 until count) {
                        if (xrp.name == "pattern") {
                            CU.VIBRATION_HOLE[i] = xrp.getAttributeIntValue(null, "value", 0).toLong()
                            xrp.next()
                            xrp.next()
                        }
                    }
                }

                "v_ending" -> {
                    val count2 = xrp.getAttributeIntValue(null, "length", 0)
                    CU.VIBRATION_END = LongArray(count2)
                    xrp.next()
                    for (i2 in 0 until count2) {
                        if (xrp.name == "pattern") {
                            CU.VIBRATION_END[i2] = xrp.getAttributeIntValue(null, "value", 0).toLong()
                            xrp.next()
                            xrp.next()
                        }
                    }
                }

                "end_ratio" -> CU.END_RATIO = xrp.getAttributeFloatValue(null, "value", 1.0f)
                "hole_ratio" -> CU.HOLE_RATIO = xrp.getAttributeFloatValue(null, "value", 1.0f)
                "ball_ratio" -> CU.BALL_RATIO = xrp.getAttributeFloatValue(null, "value", 1.53f)
                "end_anim_ratio" -> CU.END_ANIM_RATIO = xrp.getAttributeFloatValue(null, "value", 2.13f)
                "hole_anim_ratio" -> CU.HOLE_ANIM_RATIO = xrp.getAttributeFloatValue(null, "value", 1.333f)
            }
            while (xrp.eventType != 3) {
                xrp.next()
            }
            xrp.next()
        }
        xrp.close()
    }

    override fun onSearchRequested(): Boolean {
        return false
    }

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

    @Suppress("DEPRECATION")
    private fun setupDialogImmersive(dialog: Dialog) {
        dialog.setOnShowListener {
            dialog.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    companion object {
        private const val MSG_OFF = 1
        private const val MSG_ON = 0
    }
}
