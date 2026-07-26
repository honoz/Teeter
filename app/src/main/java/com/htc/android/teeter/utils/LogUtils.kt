package com.htc.android.teeter.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.htc.android.teeter.CU
import java.io.File

object LogUtils {
    private const val FILE_NAME_LOG_CRITICAL = "LogCritical"
    private const val FILE_NAME_LOG_NORMAL = "LogNormal"

    var sLogFlagCritical: Boolean = false
        private set

    var sLogFlagNormal: Boolean = false
        private set

    fun init(context: Context) {
        val fileCritical = File(context.filesDir, FILE_NAME_LOG_CRITICAL)
        val fileNormal = File(context.filesDir, FILE_NAME_LOG_NORMAL)
        when {
            fileCritical.exists() -> {
                sLogFlagNormal = true
                sLogFlagCritical = true
            }
            fileNormal.exists() -> {
                sLogFlagNormal = true
                sLogFlagCritical = false
            }
            else -> {
                sLogFlagNormal = CU.DEBUG
                sLogFlagCritical = CU.DEBUG
            }
        }
    }

    @Suppress("unused")
    fun critical(tag: String, msg: String) {
        if (sLogFlagCritical) {
            Log.wtf(TTConstants.TEETER, "${getBracketTag(tag)}$msg")
        }
    }

    @Suppress("unused")
    fun critical(tag: String, msg: String, tr: Throwable) {
        if (sLogFlagCritical) {
            try {
                Log.wtf(TTConstants.TEETER, "${getBracketTag(tag)}$msg", tr)
            } catch (e: Exception) {
                Log.wtf(TTConstants.TEETER, "${getBracketTag(tag)}$msg", e)
            }
        }
    }

    
    fun d(tag: String, msg: String) {
        if (sLogFlagNormal) {
            Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$msg")
        }
    }

    
    fun d(tag: String, prefix: String, msg: String) {
        if (sLogFlagNormal) {
            Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$prefix$msg")
        }
    }

    
    fun d(tag: String, prefix: String, msg: Int) {
        if (sLogFlagNormal) {
            Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$prefix$msg")
        }
    }

    
    fun d(tag: String, prefix: String, msg: Long) {
        if (sLogFlagNormal) {
            Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$prefix$msg")
        }
    }

    
    fun d(tag: String, prefix: String, msg: Boolean) {
        if (sLogFlagNormal) {
            Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$prefix$msg")
        }
    }

    
    fun d(tag: String, prefix: String, uri: Uri) {
        if (sLogFlagNormal) {
            Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$prefix${uri}")
        }
    }

    
    fun d(tag: String, msg: String, tr: Throwable) {
        if (sLogFlagNormal) {
            try {
                Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$msg", tr)
            } catch (e: Exception) {
                Log.d(TTConstants.TEETER, "${getBracketTag(tag)}$msg", e)
            }
        }
    }

    
    fun i(tag: String, msg: String) {
        if (sLogFlagNormal) {
            Log.i(TTConstants.TEETER, "${getBracketTag(tag)}$msg")
        }
    }

    
    fun i(tag: String, msg: String, tr: Throwable) {
        if (sLogFlagNormal) {
            try {
                Log.i(TTConstants.TEETER, "${getBracketTag(tag)}$msg", tr)
            } catch (e: Exception) {
                Log.i(TTConstants.TEETER, "${getBracketTag(tag)}$msg", e)
            }
        }
    }

    
    fun w(tag: String, msg: String) {
        if (sLogFlagNormal) {
            Log.w(TTConstants.TEETER, "${getBracketTag(tag)}$msg")
        }
    }

    @Suppress("unused")
    fun w(tag: String, msg: String, tr: Throwable) {
        if (sLogFlagNormal) {
            try {
                Log.w(TTConstants.TEETER, "${getBracketTag(tag)}$msg", tr)
            } catch (e: Exception) {
                Log.w(TTConstants.TEETER, "${getBracketTag(tag)}$msg", e)
            }
        }
    }

    
    fun e(tag: String, msg: String) {
        Log.e(TTConstants.TEETER, "${getBracketTag(tag)}$msg")
    }

    
    fun e(tag: String, msg: String, tr: Throwable) {
        try {
            Log.e(TTConstants.TEETER, "${getBracketTag(tag)}$msg", tr)
        } catch (e: Exception) {
            Log.e(TTConstants.TEETER, "${getBracketTag(tag)}$msg", e)
        }
    }

    @Suppress("unused")
    fun analytic(tag: String, msg: String) {
        Log.i(TTConstants.ANALYTIC_TAG, "[$tag]$msg")
    }

    
    fun toQualityBoard(tag: String, isSuccess: Boolean, vararg msgs: String) {
        if (sLogFlagNormal && msgs.isNotEmpty()) {
            val sb = StringBuilder(getBracketTag(tag))
            msgs.forEach { sb.append(it) }
            val type = if (isSuccess) "[S]" else "[E]"
            Log.d("${TTConstants.QUALITY_BOARD_TAG}$type", sb.toString())
        }
    }

    private fun getBracketTag(tag: String): String {
        return "<$tag> "
    }
}
