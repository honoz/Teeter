package com.htc.android.teeter

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference

class CGameModel(activity: Activity) {

    class CGameHandler(gameModel: CGameModel) : Handler(Looper.getMainLooper()) {
        private val mModelRef = WeakReference(gameModel)
        private var mLastTime: Long = 0

        fun resetTimeElapse() {
            mLastTime = 0L
        }

        override fun handleMessage(msg: Message) {
            val model = mModelRef.get() ?: return
            when (msg.what) {
                CU.MSG_REDRAW -> {
                    if (model.mBall != null) {
                        val state = model.mBall!!.fnCheckStatus()
                        when (state) {
                            CU.STATE_NORMAL -> {
                                val currentTime = System.currentTimeMillis()
                                if (mLastTime == 0L) {
                                    model.mBall!!.updateAttribute(CU.REDRAW_DURATION)
                                } else {
                                    model.mBall!!.updateAttribute((currentTime - mLastTime).toInt())
                                }
                                model.mDispMgr!!.fnInvalidate()
                                mLastTime = currentTime
                                val redrawMsg = Message.obtain(this, CU.MSG_REDRAW)
                                sendMessageDelayed(redrawMsg, CU.REDRAW_DURATION.toLong())
                            }
                            CU.STATE_AT_HOLE -> {
                                model.lockTimer("CGameModel-STATE_AT_HOLE")
                                model.fnStop()
                                val holeAnimEndMsg = Message.obtain(this, CU.MSG_ANIM_HOLE)
                                model.mDispMgr!!.fnPlayHoleEffect(holeAnimEndMsg)
                                CS.sfnFallInHole()
                                mLastTime = 0L
                            }
                            CU.STATE_AT_END -> {
                                CU.TOUCHABLE = false
                                model.mGameState = STATE_FINISH_LEVEL
                                model.lockTimer("CGameModel-STATE_AT_END")
                                model.fnStop()
                                val playEndMsg = Message.obtain(this, CU.MSG_ANIM_END)
                                model.mDispMgr!!.fnPlayEndingEffect(playEndMsg)
                                mLastTime = 0L
                            }
                        }
                    }
                }
                CU.MSG_ANIM_END -> {
                    if (model.mBall != null) {
                        model.mBall!!.fnReset(CL.begin, Vector(0, 0), Vector(0, 0))
                        model.mDispMgr!!.fnShowScorePage()
                    }
                    mLastTime = 0L
                }
                CU.MSG_ANIM_HOLE -> {
                    model.fnResetBallInfo(BALL_INFO_RELOAD)
                    model.mDispMgr!!.fnShowGamePage()
                    model.fnStart(START_CONTINUE)
                    mLastTime = 0L
                }
            }
        }
    }

    private class CLoadingHandler(gameModel: CGameModel, var mThread: Thread?, private val newBall: Boolean) : Handler(Looper.getMainLooper()) {
        private val mModelRef = WeakReference(gameModel)

        override fun handleMessage(msg: Message) {
            val model = mModelRef.get() ?: return
            if (mThread != null) {
                try {
                    mThread!!.join()
                } catch (e: InterruptedException) {
                    LogUtils.e(TTConstants.GAME_MODEL_TAG, "CLoadingHandler join interrupted", e)
                }
                model.mDispMgr!!.fnShowGamePage()
                if (newBall && model.mBall == null) {
                    model.mBall = CBall(model.mActivity!!)
                }
                if (!newBall) {
                    model.fnResetBallInfo(BALL_INFO_RELOAD)
                }
                model.unlockTimer("CGameModel-CLoadingHandler")
                if (CU.TIMER_GO) {
                    CU.TIMER_GO = false
                    model.fnStartInternal()
                }
            }
        }
    }

    private var mActivity: CTeeterActivity? = activity as? CTeeterActivity
    private var mBall: CBall? = null
    private var mDispMgr: CDispMgr? = if (activity is CTeeterActivity) CDispMgr(activity) else null
    private var mLHandler: CLoadingHandler? = null
    private var mThread: CBGLoadingThread? = null
    private var locker: Int = 3
    private var mGameState: Int = STATE_NOT_INITIALIZED
    private var mIsFileSaved = false
    private var mBallPos: Point? = Point(0, 0)
    private var mVelocity: Vector? = Vector(0, 0)
    private var mAccelerate: Vector? = Vector(0, 0)
    private val mHandler: CGameHandler = CGameHandler(this)

    fun fnInitialize() {
        mGameState = STATE_INITIALIZED
        var isFileExisted = fnExtractSavedFile(STORAGE_FILENAME)
        if (isFileExisted) {
            lockTimer("CGameModel-fnInitialize()-fnIsSavedFileExisted")
            Thread {
                fnSaveGameState(STORAGE_FILENAME_BAK)
            }.start()
            mIsFileSaved = false
        } else {
            isFileExisted = fnExtractSavedFile(STORAGE_FILENAME_BAK)
            if (isFileExisted) {
                lockTimer("CGameModel-fnInitialize()-fnIsSavedFileExisted")
                Thread {
                    fnSaveGameState(STORAGE_FILENAME)
                }.start()
                mIsFileSaved = false
            }
        }
        mThread = CBGLoadingThread(mActivity, CU.LEVEL)
        mThread!!.start()
        mLHandler = CLoadingHandler(this, mThread, true)
        unlockTimer("CGameModel-fnInitialize()")
        CU.TIMER_GO = true
        val msg = Message.obtain(mLHandler, 0)
        mLHandler!!.sendMessageAtFrontOfQueue(msg)
        try {
            mLHandler!!.mThread!!.join()
        } catch (e: InterruptedException) {
            LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnInitialize join interrupted", e)
        }
    }

    fun clearMemory() {
        if (mGameState == STATE_FINISH_GAME) {
            fnResetBallInfo(BALL_INFO_CLEAR)
            val exists = fnIsSavedFileExisted()
            if (exists) {
                mActivity!!.deleteFile(STORAGE_FILENAME)
            }
        } else {
            mGameState = STATE_UNINITIALIZED
            if (!mIsFileSaved) {
                mIsFileSaved = true
                fnSaveGameState(STORAGE_FILENAME)
            }
        }
        mHandler.let {
            it.removeMessages(CU.MSG_REDRAW)
            it.resetTimeElapse()
        }
        mLHandler?.removeMessages(0)
        mBall?.clearMemory()
        mBall = null
        mDispMgr?.clearMemory()
        mDispMgr = null
        mActivity = null
        mBallPos = null
        mVelocity = null
        mAccelerate = null
        mThread = null
    }

    fun fnStart(nStartCode: Int): Boolean {
        when (nStartCode) {
            START_NEWGAME -> {
                fnResetBallInfo(BALL_INFO_RELOAD)
                CS.sfnReset()
            }
            START_NEWGAME_NEED_INIT -> {
                CS.sfnReset()
                mDispMgr!!.fnClearGamePage()
                mThread = CBGLoadingThread(mActivity, CU.LEVEL)
                mThread!!.start()
                mLHandler = CLoadingHandler(this, mThread, false)
                val msg = Message.obtain(mLHandler, 0)
                mLHandler!!.sendMessageAtFrontOfQueue(msg)
            }
            START_CONTINUE -> {
                unlockTimer("CGameModel-START_CONTINUE()")
                fnStartInternal()
            }
            START_NEXTLEVEL -> {
                mDispMgr!!.fnClearGamePage()
                fnResetBallInfo(BALL_INFO_RELOAD)
                mDispMgr!!.fnShowGamePage()
                unlockTimer("CGameModel-START_NEXTLEVEL()")
                fnStartInternal()
            }
            else -> {
                throw AssertionError()
            }
        }
        CU.TOUCHABLE = true
        return true
    }

    fun lockTimer(name: String) {
        locker++
        LogUtils.i(TTConstants.GAME_MODEL_TAG, "Locker = $locker lock by $name")
    }

    fun unlockTimer(name: String) {
        locker--
        LogUtils.i(TTConstants.GAME_MODEL_TAG, "Locker = $locker unlock by $name")
    }

    private fun fnStartInternal(): Boolean {
        if (locker <= 0) {
            locker = 0
            mGameState = STATE_NORMAL
            mHandler.removeMessages(CU.MSG_REDRAW)
            val msg = Message.obtain(mHandler, CU.MSG_REDRAW, CU.RESTART_DELAY)
            mHandler.sendMessageDelayed(msg, CU.RESTART_DELAY.toLong())
            mBall!!.fnStart(mBallPos!!, mVelocity!!, mAccelerate!!)
            mDispMgr!!.fnAttachBall(mBall!!)
            CS.sfnBeginLevel()
        }
        return true
    }

    fun fnStop() {
        mHandler.let {
            it.removeMessages(CU.MSG_REDRAW)
            it.resetTimeElapse()
        }
        mLHandler?.removeMessages(0)
        if (mBall != null && mGameState != STATE_UNINITIALIZED) {
            mBall!!.fnGetCenter(mBallPos!!)
            mBall!!.fnGetVelocity(mVelocity!!)
            mBall!!.fnGetAccelerate(mAccelerate!!)
            mBall!!.fnStop()
        }
        if (mGameState != STATE_FINISH_GAME && mGameState != STATE_FINISH_LEVEL && mGameState != STATE_PAUSED) {
            mGameState = STATE_STOPPED
        }
        if (mGameState != STATE_FINISH_GAME && mGameState != STATE_FINISH_LEVEL) {
            CS.sfnPauseRecord()
        } else {
            CS.sfnEndLevel()
        }
    }

    fun stopSensor() {
        mBall?.stopSensor()
    }

    fun fnGetGameState(): Int {
        return mGameState
    }

    fun fnResetBallInfo(mode: Int) {
        if (mBallPos == null) {
            mBallPos = Point()
        }
        when (mode) {
            BALL_INFO_CLEAR -> mBallPos!!.set(0, 0)
            BALL_INFO_RELOAD -> mBallPos!!.set(CL.begin!!.x, CL.begin!!.y)
        }
        if (mVelocity == null) {
            mVelocity = Vector()
        }
        mVelocity!!.set(0, 0)
        if (mAccelerate == null) {
            mAccelerate = Vector()
        }
        mAccelerate!!.set(0, 0)
    }

    fun fnSaveGameState(name: String) {
        var fos: FileOutputStream? = null
        var dos: DataOutputStream? = null
        try {
            fos = mActivity!!.openFileOutput(name, Context.MODE_PRIVATE)
            dos = DataOutputStream(fos)
            dos.writeInt(CU.LEVEL)
            dos.writeBoolean(CS.pausedState)
            dos.writeLong(CS.levelTime)
            dos.writeLong(CS.totalTime)
            dos.writeInt(CS.levelAttempt)
            dos.writeInt(CS.totalAttempt)
            dos.writeInt(mBallPos!!.x)
            dos.writeInt(mBallPos!!.y)
            dos.writeInt(mVelocity!!.x)
            dos.writeInt(mVelocity!!.y)
            dos.writeInt(mAccelerate!!.x)
            dos.writeInt(mAccelerate!!.y)
            dos.writeInt(mGameState)
            dos.flush()
        } catch (e: Exception) {
            LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnSaveGameState error", e)
        } finally {
            try {
                dos?.close()
            } catch (e: IOException) {
                LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnSaveGameState close dos error", e)
            }
            try {
                fos?.close()
            } catch (e: IOException) {
                LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnSaveGameState close fos error", e)
            }
        }
    }

    fun fnExtractSavedFile(name: String): Boolean {
        var fis: FileInputStream? = null
        var dis: DataInputStream? = null
        return try {
            if (!mActivity!!.fileList().contains(name)) {
                return false
            }
            fis = mActivity!!.openFileInput(name)
            dis = DataInputStream(fis)
            CU.LEVEL = dis.readInt()
            CS.pausedState = dis.readBoolean()
            CS.levelTime = dis.readLong()
            CS.totalTime = dis.readLong()
            CS.levelAttempt = dis.readInt()
            CS.totalAttempt = dis.readInt()
            mBallPos!!.set(dis.readInt(), dis.readInt())
            mVelocity!!.set(dis.readInt(), dis.readInt())
            mAccelerate!!.set(dis.readInt(), dis.readInt())
            mGameState = dis.readInt()
            true
        } catch (e: Exception) {
            LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnExtractSavedFile error", e)
            false
        } finally {
            try {
                dis?.close()
            } catch (e: IOException) {
                LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnExtractSavedFile close dis error", e)
            }
            try {
                fis?.close()
            } catch (e: IOException) {
                LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnExtractSavedFile close fis error", e)
            }
        }
    }

    fun fnIsSavedFileExisted(): Boolean {
        var fis: FileInputStream? = null
        return try {
            fis = mActivity!!.openFileInput(STORAGE_FILENAME)
            true
        } catch (e: FileNotFoundException) {
            LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnIsSavedFileExisted: file not found", e)
            false
        } catch (e: IOException) {
            LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnIsSavedFileExisted: IO error", e)
            false
        } finally {
            try {
                fis?.close()
            } catch (e: IOException) {
                LogUtils.e(TTConstants.GAME_MODEL_TAG, "fnIsSavedFileExisted: close error", e)
            }
        }
    }

    fun gameFinish() {
        mGameState = STATE_FINISH_GAME
    }

    fun gamePause() {
        mGameState = STATE_PAUSED
    }

    companion object {

        const val BALL_INFO_CLEAR = 0
        const val BALL_INFO_RELOAD = 1
        const val START_CONTINUE = 3
        const val START_NEWGAME = 1
        const val START_NEWGAME_NEED_INIT = 2
        const val START_NEXTLEVEL = 4
        const val STATE_FINISH_GAME = 7
        const val STATE_FINISH_LEVEL = 6
        const val STATE_INITIALIZED = 2
        const val STATE_NORMAL = 5
        const val STATE_NOT_INITIALIZED = 1
        const val STATE_PAUSED = 8
        const val STATE_STOPPED = 4
        const val STATE_UNINITIALIZED = 3
        private const val STORAGE_FILENAME = "current.state"
        private const val STORAGE_FILENAME_BAK = "current.statebak"
    }
}
