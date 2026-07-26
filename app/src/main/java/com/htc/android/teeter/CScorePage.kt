package com.htc.android.teeter

import android.app.Activity
import android.content.res.Resources
import android.view.View
import android.widget.TableLayout
import android.widget.TextView

internal class CScorePage(private var mActivity: Activity?) {

    private var mLayout: TableLayout? = null
    private var mView0: TextView? = null
    private var mView1: TextView? = null
    private var mView2: TextView? = null
    private var mView3: TextView? = null
    private var mView4: TextView? = null

    fun fnCreateView(): View {
        return fnLayoutScores(CU.LEVEL)
    }

    fun fnInvalidate() {
        mLayout?.invalidate()
    }

    @Throws(Resources.NotFoundException::class)
    private fun fnLayoutScores(level: Int): View {
        val activity = mActivity ?: throw IllegalStateException("Activity is null")
        if (mLayout == null) {
            mLayout = View.inflate(activity, R.layout.layout01, null) as TableLayout
        }
        if (mView0 == null) {
            mView0 = mLayout?.findViewById(R.id.level_caption)
        }
        val str = String.format(
            activity.resources.getString(R.string.str_level_caption),
            level
        )
        mView0?.text = str

        if (mView1 == null) {
            mView1 = mLayout?.findViewById(R.id.level_time_score)
        }
        val t = CTime(CS.levelTime)
        val format = activity.resources.getString(R.string.str_time)
        val str2 = String.format(format, t.hours, t.minutes, t.seconds)
        mView1?.text = str2

        if (mView2 == null) {
            mView2 = mLayout?.findViewById(R.id.level_attempt_score)
        }
        mView2?.text = CS.levelAttempt.toString()

        if (mView3 == null) {
            mView3 = mLayout?.findViewById(R.id.total_time_score)
        }
        val t2 = CTime(CS.totalTime)
        val str4 = String.format(format, t2.hours, t2.minutes, t2.seconds)
        mView3?.text = str4

        if (mView4 == null) {
            mView4 = mLayout?.findViewById(R.id.total_attempt_score)
        }
        mView4?.text = CS.totalAttempt.toString()

        return mLayout!!
    }

    fun clearMemory() {
        mActivity = null
        mLayout = null
        mView0 = null
        mView1 = null
        mView2 = null
        mView3 = null
        mView4 = null
    }
}
