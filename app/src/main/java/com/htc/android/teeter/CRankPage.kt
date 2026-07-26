package com.htc.android.teeter

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.TableLayout
import android.widget.TextView
import com.htc.android.teeter.utils.LogUtils
import com.htc.android.teeter.utils.TTConstants
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.StreamCorruptedException

internal class CRankPage(private var mActivity: Activity?) {

    @Throws(Throwable::class)
    fun fnCreateView(): View {
        val layout = View.inflate(mActivity, R.layout.layout02, null) as TableLayout
        val rankArray = fnGetStoredRanks()
        val newRankArray: LongArray
        val length: Int
        if (rankArray != null && rankArray.isNotEmpty()) {
            length = rankArray.size + 1
            newRankArray = LongArray(length)
            rankArray.copyInto(newRankArray, 0, 0, length - 1)
        } else {
            length = 1
            newRankArray = LongArray(1)
        }
        val newRank = CS.totalTime
        newRankArray[length - 1] = newRank
        newRankArray.sort()
        var nHighlight = -1
        val len = if (RANK_MAX_COUNT <= length) RANK_MAX_COUNT else length
        for (i in 0 until len) {
            val t = CTime(newRankArray[i])
            val format = mActivity!!.resources.getString(R.string.str_time)
            val str = String.format(format, t.hours, t.minutes, t.seconds)
            (layout.findViewById<View>(R.id.rank_scores1 + i) as TextView).text = str
            if (newRank == newRankArray[i]) {
                nHighlight = i
            }
        }
        if (nHighlight != -1) {
            val rankView = layout.findViewById<TextView>(R.id.rank_scores1 + nHighlight)
            rankView.setTextColor(ColorStateList.valueOf(-0x1000000))
            rankView.setBackgroundColor(-0x1)
        }
        val storedRankArray: LongArray
        if (len != length) {
            storedRankArray = LongArray(len)
            newRankArray.copyInto(storedRankArray, 0, 0, len)
        } else {
            storedRankArray = newRankArray
        }
        fnStoreRanks(storedRankArray)
        val newGameBtn = layout.findViewById<Button>(R.id.btn_restart)
        newGameBtn.setText(R.string.str_btn_restart)
        newGameBtn.setOnClickListener { _ ->
            val activity = mActivity
            if (activity is CTeeterActivity) {
                activity.fnExternalGameFlow(2)
            }
        }
        val leaveBtn = layout.findViewById<Button>(R.id.btn_quit)
        leaveBtn.setOnClickListener { _ ->
            val activity = mActivity
            if (activity is CTeeterActivity) {
                activity.fnExternalGameFlow(3)
            }
        }
        return layout
    }

    private fun fnStoreRanks(rankArray: LongArray): Boolean {
        val length = rankArray.size
        if (length <= 0) {
            return false
        }
        var success: Boolean
        var fos: FileOutputStream? = null
        var dos: DataOutputStream? = null
        try {
            fos = mActivity!!.openFileOutput(RANK_FILENAME, Context.MODE_PRIVATE)
            dos = DataOutputStream(fos)
            dos.writeInt(length)
            for (j in rankArray) {
                dos.writeLong(j)
            }
            success = true
        } catch (e: FileNotFoundException) {
            LogUtils.e(TTConstants.RANK_PAGE_TAG, "fnStoreRanks: file not found", e)
            success = false
        } catch (e: IOException) {
            LogUtils.e(TTConstants.RANK_PAGE_TAG, "fnStoreRanks: IO error", e)
            success = false
        } finally {
            closeResource(dos)
            closeResource(fos)
        }
        return success
    }

    private fun fnGetStoredRanks(): LongArray? {
        var fis: FileInputStream? = null
        var dis: DataInputStream? = null
        var rankArray: LongArray? = null
        try {
            fis = mActivity!!.openFileInput(RANK_FILENAME)
            dis = DataInputStream(fis)
            val length = dis.readInt()
            if (length > 0) {
                rankArray = LongArray(length)
                for (i in 0 until length) {
                    rankArray[i] = dis.readLong()
                }
            }
        } catch (e: FileNotFoundException) {
            LogUtils.e(TTConstants.RANK_PAGE_TAG, "fnGetStoredRanks: file not found", e)
        } catch (e: StreamCorruptedException) {
            LogUtils.e(TTConstants.RANK_PAGE_TAG, "fnGetStoredRanks: stream corrupted", e)
        } catch (e: IOException) {
            LogUtils.e(TTConstants.RANK_PAGE_TAG, "fnGetStoredRanks: IO error", e)
        } finally {
            closeResource(dis)
            closeResource(fis)
        }
        return rankArray
    }

    private fun closeResource(c: Closeable?) {
        try {
            c?.close()
        } catch (e: IOException) {
            LogUtils.e(TTConstants.RANK_PAGE_TAG, "closeResource: failed to close", e)
        }
    }

    fun clearMemory() {
        mActivity = null
    }

    companion object {
        private const val RANK_FILENAME = "rank.list"
        private const val RANK_MAX_COUNT = 5
    }
}
