package com.htc.android.teeter

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import java.lang.Math.toDegrees
import kotlin.math.abs
import kotlin.math.acos

class CBall(private var mParent: Activity?) : SensorEventListener {

    private var currentDiamond: Vector? = null
    private var mFriction = 0
    private var mSensorManager: SensorManager? = null
    private var mVibrator: Vibrator? = null
    private var nextZone = 0
    private var wall: Rect? = null

    private var firsttime = true
    private var timerLock = false
    private var ballZone = -1
    private var idleTimer = 0L
    private var idleStemp = 0L
    private var keepAwake = true

    private val powerIdle = 0
    private val powerAwake = 1
    private var mPowerState = powerAwake

    private var sensorValue = FloatArray(3)
    private var ballPos: Point = CL.begin?.let { translateCL(it) } ?: Point(0, 0)
    private var nextPos: Point = Point(this.ballPos)
    private var mVelocity: Vector = Vector(0, 0)
    private var mAccleration: Vector = Vector(0, 0)
    private var mTempVector: Vector = Vector(0, 0)
    private var mState = 0
    private var mSensorValue = floatArrayOf(0.0f, 0.0f, 0.0f)
    private var mSensorValueOld = floatArrayOf(0.0f, 0.0f, 0.0f)

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = mParent?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            this.mVibrator = vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            this.mVibrator = mParent?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        this.mSensorManager = mParent?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    fun fnStart(begin: Point, v: Vector, a: Vector) {
        if (!this.timerLock) {
            this.timerLock = true
            fnReset(begin, v, a)
            if (this.firsttime) {
                this.firsttime = false
                this.mSensorManager?.registerListener(this, this.mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST)
            }
            this.mPowerState = 1
        }
    }

    fun fnStop() {
        this.mState = 3
        this.timerLock = false
    }

    fun stopSensor() {
        this.firsttime = true
        this.mSensorManager?.unregisterListener(this)
    }

    fun fnReset(begin: Point?, v: Vector?, a: Vector?) {
        if (begin != null) {
            this.ballPos = translateCL(begin)
            this.nextPos = Point(this.ballPos)
        }
        if (v != null) {
            this.mVelocity.set(v.x, v.y)
        }
        if (a != null) {
            this.mAccleration.set(a.x, a.y)
        }
        this.mState = 0
    }

    fun updateAttribute(elapseMinisec: Int) {
        this.mState = updateState()
        if (this.mState == 0) {
            updatemAccleration()
            updatemVelocity(elapseMinisec)
            this.nextPos.x += this.mVelocity.x
            this.nextPos.y += this.mVelocity.y
            wallContactFix()
            screenContactFix()
            wallContactControl()
            screenContactControl()
            val velocity = this.mVelocity.length()
            if (velocity > CU.MAX_SPEED) {
                val ratio = CU.MAX_SPEED.toFloat() / velocity
                this.mVelocity.x = (this.mVelocity.x * ratio).toInt()
                this.mVelocity.y = (this.mVelocity.y * ratio).toInt()
            }
            if (this.mVelocity.length() > this.mFriction) {
                this.mVelocity.decrease(this.mFriction)
            } else {
                this.mVelocity.set(0, 0)
            }
            this.ballPos.x = this.nextPos.x
            this.ballPos.y = this.nextPos.y
        }
    }

    private fun screenContactFix() {
        if (this.nextPos.x < left) {
            if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                hits()
            }
            this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
            this.nextPos.x += left - this.nextPos.x
        }
        if (this.nextPos.x > right) {
            if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                hits()
            }
            this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
            this.nextPos.x -= this.nextPos.x - right
        }
        if (this.nextPos.y < top) {
            if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                hits()
            }
            this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
            this.nextPos.y += top - this.nextPos.y
        }
        if (this.nextPos.y > bottom) {
            if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                hits()
            }
            this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
            this.nextPos.y -= this.nextPos.y - bottom
        }
    }

    private fun wallContactFix() {
        var baseline = 0
        var baseline2 = 0
        var baseline3 = 0
        var baseline4 = 0
        for (i in walls!!.indices) {
            this.wall = walls!![i]
            if (this.wall == null) continue
            if (this.nextPos.y >= this.wall!!.top && this.nextPos.y <= this.wall!!.bottom) {
                val baseline5 = this.wall!!.left - CU.BALL_RADIUS_BIG
                if (abs(baseline5 - this.nextPos.x) < CU.BALL_RADIUS_BIG && this.nextPos.x > baseline5) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += baseline5 - this.nextPos.x
                }
                if (this.ballPos.x < baseline5 && this.nextPos.x > baseline5) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += baseline5 - this.nextPos.x
                }
                val baseline6 = this.wall!!.right + CU.BALL_RADIUS_BIG
                if (abs(baseline6 - this.nextPos.x) < CU.BALL_RADIUS_BIG && this.nextPos.x < baseline6) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += baseline6 - this.nextPos.x
                }
                if (this.ballPos.x > baseline6 && this.nextPos.x < baseline6) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += baseline6 - this.nextPos.x
                }
            }
            if (this.nextPos.x >= this.wall!!.left && this.nextPos.x <= this.wall!!.right) {
                val baseline7 = this.wall!!.top - CU.BALL_RADIUS_BIG
                if (abs(baseline7 - this.nextPos.y) < CU.BALL_RADIUS_BIG && this.nextPos.y > baseline7) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += baseline7 - this.nextPos.y
                }
                if (this.ballPos.y < baseline7 && this.nextPos.y > baseline7) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += baseline7 - this.nextPos.y
                }
                val baseline8 = this.wall!!.bottom + CU.BALL_RADIUS_BIG
                if (abs(baseline8 - this.nextPos.y) < CU.BALL_RADIUS_BIG && this.nextPos.y < baseline8) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += baseline8 - this.nextPos.y
                }
                if (this.ballPos.y > baseline8 && this.nextPos.y < baseline8) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += baseline8 - this.nextPos.y
                }
            }
            if (this.nextPos.x <= this.wall!!.left && this.nextPos.x >= this.wall!!.left - CU.BALL_RADIUS_BIG
                && this.nextPos.y <= this.wall!!.top && this.nextPos.y >= this.wall!!.top - CU.BALL_RADIUS_BIG
                && run {
                    baseline4 = CL.destToPoint(this.wall!!.left, this.wall!!.top, this.nextPos.x, this.nextPos.y).toInt()
                    baseline4 < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.left - this.nextPos.x, this.wall!!.top - this.nextPos.y)
                val dot = this.mTempVector.dot(this.mVelocity)
                if (dot > 0.0f) {
                    val force = this.mTempVector.mul(dot.toFloat() / this.mTempVector.dot(this.mTempVector))
                    this.mVelocity.x = (this.mVelocity.x - force.x * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mVelocity.y = (this.mVelocity.y - force.y * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline4) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                    if (abs(force.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                }
            }
            if (this.ballPos.x <= this.wall!!.left && this.ballPos.y <= this.wall!!.top
                && this.nextPos.x >= this.wall!!.left && this.nextPos.y >= this.wall!!.top) {
                val cosV = (this.mVelocity.x * CU.RESTART_DELAY) / this.mVelocity.length()
                this.mTempVector.setVector(this.wall!!.left - this.ballPos.x, this.wall!!.top - this.ballPos.y)
                val cosCenter = (this.mTempVector.x * CU.RESTART_DELAY) / this.mTempVector.length()
                if (cosV > cosCenter) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += (this.wall!!.top - CU.BALL_RADIUS_BIG) - this.nextPos.y
                } else if (cosV < cosCenter) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.left - CU.BALL_RADIUS_BIG) - this.nextPos.x
                } else {
                    if (abs(this.mVelocity.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.left - CU.BALL_RADIUS_BIG) - this.nextPos.x
                    this.nextPos.y += (this.wall!!.top - CU.BALL_RADIUS_BIG) - this.nextPos.y
                }
            }
            if (this.nextPos.x <= this.wall!!.left && this.nextPos.x >= this.wall!!.left - CU.BALL_RADIUS_BIG
                && this.nextPos.y >= this.wall!!.bottom && this.nextPos.y <= this.wall!!.bottom + CU.BALL_RADIUS_BIG
                && run {
                    baseline3 = CL.destToPoint(this.wall!!.left, this.wall!!.bottom, this.nextPos.x, this.nextPos.y).toInt()
                    baseline3 < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.left - this.nextPos.x, this.wall!!.bottom - this.nextPos.y)
                val dot2 = this.mTempVector.dot(this.mVelocity)
                if (dot2 > 0.0f) {
                    val force2 = this.mTempVector.mul(dot2.toFloat() / this.mTempVector.dot(this.mTempVector))
                    this.mVelocity.x = (this.mVelocity.x - force2.x * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mVelocity.y = (this.mVelocity.y - force2.y * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline3) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                    if (abs(force2.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                }
            }
            if (this.ballPos.x <= this.wall!!.left && this.ballPos.y >= this.wall!!.bottom
                && this.nextPos.x >= this.wall!!.left && this.nextPos.y <= this.wall!!.bottom) {
                val cosV2 = (this.mVelocity.x * CU.RESTART_DELAY) / this.mVelocity.length()
                this.mTempVector.setVector(this.wall!!.left - this.ballPos.x, this.wall!!.bottom - this.ballPos.y)
                val cosCenter2 = (this.mTempVector.x * CU.RESTART_DELAY) / this.mTempVector.length()
                if (cosV2 > cosCenter2) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += (this.wall!!.bottom + CU.BALL_RADIUS_BIG) - this.nextPos.y
                } else if (cosV2 < cosCenter2) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.left - CU.BALL_RADIUS_BIG) - this.nextPos.x
                } else {
                    if (abs(this.mVelocity.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.left - CU.BALL_RADIUS_BIG) - this.nextPos.x
                    this.nextPos.y += (this.wall!!.bottom + CU.BALL_RADIUS_BIG) - this.nextPos.y
                }
            }
            if (this.nextPos.x >= this.wall!!.right && this.nextPos.x <= this.wall!!.right + CU.BALL_RADIUS_BIG
                && this.nextPos.y <= this.wall!!.top && this.nextPos.y >= this.wall!!.top - CU.BALL_RADIUS_BIG
                && run {
                    baseline2 = CL.destToPoint(this.wall!!.right, this.wall!!.top, this.nextPos.x, this.nextPos.y).toInt()
                    baseline2 < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.right - this.nextPos.x, this.wall!!.top - this.nextPos.y)
                val dot3 = this.mTempVector.dot(this.mVelocity)
                if (dot3 > 0.0f) {
                    val force3 = this.mTempVector.mul(dot3.toFloat() / this.mTempVector.dot(this.mTempVector))
                    this.mVelocity.x = (this.mVelocity.x - force3.x * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mVelocity.y = (this.mVelocity.y - force3.y * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline2) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                    if (abs(force3.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                }
            }
            if (this.ballPos.x >= this.wall!!.right && this.ballPos.y <= this.wall!!.top
                && this.nextPos.x <= this.wall!!.right && this.nextPos.y >= this.wall!!.top) {
                val cosV3 = (-this.mVelocity.x * CU.RESTART_DELAY) / this.mVelocity.length()
                this.mTempVector.setVector(this.wall!!.right - this.ballPos.x, this.wall!!.top - this.ballPos.y)
                val cosCenter3 = (-this.mTempVector.x * CU.RESTART_DELAY) / this.mTempVector.length()
                if (cosV3 > cosCenter3) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += (this.wall!!.top - CU.BALL_RADIUS_BIG) - this.nextPos.y
                } else if (cosV3 < cosCenter3) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.right + CU.BALL_RADIUS_BIG) - this.nextPos.x
                } else {
                    if (abs(this.mVelocity.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.right + CU.BALL_RADIUS_BIG) - this.nextPos.x
                    this.nextPos.y += (this.wall!!.top - CU.BALL_RADIUS_BIG) - this.nextPos.y
                }
            }
            if (this.nextPos.x >= this.wall!!.right && this.nextPos.x <= this.wall!!.right + CU.BALL_RADIUS_BIG
                && this.nextPos.y >= this.wall!!.bottom && this.nextPos.y <= this.wall!!.bottom + CU.BALL_RADIUS_BIG
                && run {
                    baseline = CL.destToPoint(this.wall!!.right, this.wall!!.bottom, this.nextPos.x, this.nextPos.y).toInt()
                    baseline < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.right - this.nextPos.x, this.wall!!.bottom - this.nextPos.y)
                val dot4 = this.mTempVector.dot(this.mVelocity)
                if (dot4 > 0.0f) {
                    val force4 = this.mTempVector.mul(dot4.toFloat() / this.mTempVector.dot(this.mTempVector))
                    this.mVelocity.x = (this.mVelocity.x - force4.x * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mVelocity.y = (this.mVelocity.y - force4.y * (CU.BOUNCE_RATE + 1.0f)).toInt()
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                    if (abs(force4.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                }
            }
            if (this.ballPos.x >= this.wall!!.right && this.ballPos.y >= this.wall!!.bottom
                && this.nextPos.x <= this.wall!!.right && this.nextPos.y <= this.wall!!.bottom) {
                val cosV4 = (-this.mVelocity.x * CU.RESTART_DELAY) / this.mVelocity.length()
                this.mTempVector.setVector(this.wall!!.right - this.ballPos.x, this.wall!!.bottom - this.ballPos.y)
                val cosCenter4 = (-this.mTempVector.x * CU.RESTART_DELAY) / this.mTempVector.length()
                if (cosV4 > cosCenter4) {
                    if (abs(this.mVelocity.y) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.y += (this.wall!!.bottom + CU.BALL_RADIUS_BIG) - this.nextPos.y
                } else if (cosV4 < cosCenter4) {
                    if (abs(this.mVelocity.x) > CU.VIBRATION_ACTIVE_SPEED) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.right + CU.BALL_RADIUS_BIG) - this.nextPos.x
                } else {
                    if (abs(this.mVelocity.length()) > CU.VIBRATION_ACTIVE_SPEED / 3) {
                        hits()
                    }
                    this.mVelocity.x = (this.mVelocity.x * (-CU.BOUNCE_RATE)).toInt()
                    this.mVelocity.y = (this.mVelocity.y * (-CU.BOUNCE_RATE)).toInt()
                    this.nextPos.x += (this.wall!!.right + CU.BALL_RADIUS_BIG) - this.nextPos.x
                    this.nextPos.y += (this.wall!!.bottom + CU.BALL_RADIUS_BIG) - this.nextPos.y
                }
            }
        }
    }

    private fun screenContactControl() {
        if (this.nextPos.x < left) {
            this.mVelocity.x *= 0
            this.nextPos.x += left - this.nextPos.x
        }
        if (this.nextPos.x > right) {
            this.mVelocity.x *= 0
            this.nextPos.x -= this.nextPos.x - right
        }
        if (this.nextPos.y < top) {
            this.mVelocity.y *= 0
            this.nextPos.y += top - this.nextPos.y
        }
        if (this.nextPos.y > bottom) {
            this.mVelocity.y *= 0
            this.nextPos.y -= this.nextPos.y - bottom
        }
    }

    private fun wallContactControl() {
        var baseline = 0
        var baseline2 = 0
        var baseline3 = 0
        var baseline4 = 0
        for (i in walls!!.indices) {
            this.wall = walls!![i]
            if (this.wall == null) continue
            if (this.nextPos.y >= this.wall!!.top && this.nextPos.y <= this.wall!!.bottom) {
                val baseline5 = this.wall!!.left - CU.BALL_RADIUS_BIG
                if (abs(baseline5 - this.nextPos.x) < CU.BALL_RADIUS_BIG && this.nextPos.x > baseline5) {
                    this.mVelocity.x *= 0
                    this.nextPos.x += baseline5 - this.nextPos.x
                }
                if (this.ballPos.x < baseline5 && this.nextPos.x > baseline5) {
                    this.mVelocity.x *= 0
                    this.nextPos.x += baseline5 - this.nextPos.x
                }
                val baseline6 = this.wall!!.right + CU.BALL_RADIUS_BIG
                if (abs(baseline6 - this.nextPos.x) < CU.BALL_RADIUS_BIG && this.nextPos.x < baseline6) {
                    this.mVelocity.x *= 0
                    this.nextPos.x += baseline6 - this.nextPos.x
                }
                if (this.ballPos.x > baseline6 && this.nextPos.x < baseline6) {
                    this.mVelocity.x *= 0
                    this.nextPos.x += baseline6 - this.nextPos.x
                }
            }
            if (this.nextPos.x >= this.wall!!.left && this.nextPos.x <= this.wall!!.right) {
                val baseline7 = this.wall!!.top - CU.BALL_RADIUS_BIG
                if (abs(baseline7 - this.nextPos.y) < CU.BALL_RADIUS_BIG && this.nextPos.y > baseline7) {
                    this.mVelocity.y *= 0
                    this.nextPos.y += baseline7 - this.nextPos.y
                }
                if (this.ballPos.y < baseline7 && this.nextPos.y > baseline7) {
                    this.mVelocity.y *= 0
                    this.nextPos.y += baseline7 - this.nextPos.y
                }
                val baseline8 = this.wall!!.bottom + CU.BALL_RADIUS_BIG
                if (abs(baseline8 - this.nextPos.y) < CU.BALL_RADIUS_BIG && this.nextPos.y < baseline8) {
                    this.mVelocity.y *= 0
                    this.nextPos.y += baseline8 - this.nextPos.y
                }
                if (this.ballPos.y > baseline8 && this.nextPos.y < baseline8) {
                    this.mVelocity.y *= 0
                    this.nextPos.y += baseline8 - this.nextPos.y
                }
            }
            if (this.nextPos.x <= this.wall!!.left && this.nextPos.x >= this.wall!!.left - CU.BALL_RADIUS_BIG
                && this.nextPos.y <= this.wall!!.top && this.nextPos.y >= this.wall!!.top - CU.BALL_RADIUS_BIG
                && run {
                    baseline4 = CL.destToPoint(this.wall!!.left, this.wall!!.top, this.nextPos.x, this.nextPos.y).toInt()
                    baseline4 < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.left - this.nextPos.x, this.wall!!.top - this.nextPos.y)
                val dot = this.mTempVector.dot(this.mVelocity)
                if (dot > 0.0f) {
                    this.mVelocity.x = 0
                    this.mVelocity.y = 0
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline4) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                }
            }
            if (this.nextPos.x <= this.wall!!.left && this.nextPos.x >= this.wall!!.left - CU.BALL_RADIUS_BIG
                && this.nextPos.y >= this.wall!!.bottom && this.nextPos.y <= this.wall!!.bottom + CU.BALL_RADIUS_BIG
                && run {
                    baseline3 = CL.destToPoint(this.wall!!.left, this.wall!!.bottom, this.nextPos.x, this.nextPos.y).toInt()
                    baseline3 < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.left - this.nextPos.x, this.wall!!.bottom - this.nextPos.y)
                val dot2 = this.mTempVector.dot(this.mVelocity)
                if (dot2 > 0.0f) {
                    this.mVelocity.x = 0
                    this.mVelocity.y = 0
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline3) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                }
            }
            if (this.nextPos.x >= this.wall!!.right && this.nextPos.x <= this.wall!!.right + CU.BALL_RADIUS_BIG
                && this.nextPos.y <= this.wall!!.top && this.nextPos.y >= this.wall!!.top - CU.BALL_RADIUS_BIG
                && run {
                    baseline2 = CL.destToPoint(this.wall!!.right, this.wall!!.top, this.nextPos.x, this.nextPos.y).toInt()
                    baseline2 < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.right - this.nextPos.x, this.wall!!.top - this.nextPos.y)
                val dot3 = this.mTempVector.dot(this.mVelocity)
                if (dot3 > 0.0f) {
                    this.mVelocity.x = 0
                    this.mVelocity.y = 0
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline2) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                }
            }
            if (this.nextPos.x >= this.wall!!.right && this.nextPos.x <= this.wall!!.right + CU.BALL_RADIUS_BIG
                && this.nextPos.y >= this.wall!!.bottom && this.nextPos.y <= this.wall!!.bottom + CU.BALL_RADIUS_BIG
                && run {
                    baseline = CL.destToPoint(this.wall!!.right, this.wall!!.bottom, this.nextPos.x, this.nextPos.y).toInt()
                    baseline < CU.BALL_RADIUS_BIG
                }) {
                this.mTempVector.setVector(this.wall!!.right - this.nextPos.x, this.wall!!.bottom - this.nextPos.y)
                val dot4 = this.mTempVector.dot(this.mVelocity)
                if (dot4 > 0.0f) {
                    this.mVelocity.x = 0
                    this.mVelocity.y = 0
                    this.mTempVector = this.mTempVector.mul((CU.BALL_RADIUS_BIG.toFloat() / baseline) - 1.0f)
                    this.nextPos.x -= this.mTempVector.x
                    this.nextPos.y -= this.mTempVector.y
                }
            }
        }
    }

    private fun updatemAccleration() {
        this.mAccleration.x = (this.mSensorValue[1] * CU.GRAVITY_FACTOR).toInt()
        this.mAccleration.y = (this.mSensorValue[0] * CU.GRAVITY_FACTOR).toInt()
        if (CL.mIsFacet) {
            this.nextZone = CL.atZone(CU.b2s(this.nextPos.x), CU.b2s(this.nextPos.y), this.mVelocity.x, this.mVelocity.y)
            if (this.nextZone != this.ballZone) {
                if (this.currentDiamond == null) {
                    this.currentDiamond = CL.getDiamondAccleration(this.nextZone)
                }
                val diamondLength = this.currentDiamond!!.length()
                if (diamondLength != 0) {
                    val speed = this.mVelocity.dot(this.currentDiamond!!) / diamondLength
                    if (speed >= CU.VIBRATION_ACTIVE_SPEED_GROUND || (-speed) >= CU.VIBRATION_ACTIVE_SPEED) {
                        across()
                    }
                }
                this.currentDiamond = CL.getDiamondAccleration(this.nextZone)
            }
            this.ballZone = this.nextZone
            this.mAccleration.x += this.currentDiamond!!.x
            this.mAccleration.y += this.currentDiamond!!.y
        }
        this.mFriction = abs((this.mSensorValue[2] * CU.FRICTION_FACTOR).toDouble()).toInt()
    }

    private fun updatemVelocity(elapseMinisec: Int) {
        this.mVelocity.x = (this.mVelocity.x + this.mAccleration.x * elapseMinisec * CU.ACC_PER_MINISEC).toInt()
        this.mVelocity.y = (this.mVelocity.y + this.mAccleration.y * elapseMinisec * CU.ACC_PER_MINISEC).toInt()
    }

    private fun vibrateDevice(duration: Long) {
        val vibrator = this.mVibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun hits() {
        vibrateDevice(CU.VIBRATION_DURATION.toLong())
    }

    private fun across() {
        vibrateDevice(CU.VIBRATION_DURATION.toLong())
    }

    fun updateState(): Int {
        val now = System.currentTimeMillis()
        if (abs(this.mSensorValue[0] - this.mSensorValueOld[0]) > 0.05f
            || abs(this.mSensorValue[1] - this.mSensorValueOld[1]) > 0.05f) {
            this.idleTimer = 0L
            this.idleStemp = now
            this.keepAwake = true
        } else {
            this.idleTimer = now - this.idleStemp
        }
        if (this.idleTimer > 3000) {
            this.keepAwake = false
        }
        if (this.mPowerState == powerIdle && this.keepAwake) {
            this.mPowerState = powerAwake
            (this.mParent as? CTeeterActivity)?.turnLight(true)
        }
        if (this.mPowerState == powerAwake && !this.keepAwake) {
            this.mPowerState = powerIdle
            (this.mParent as? CTeeterActivity)?.turnLight(false)
        }
        this.mSensorValueOld[0] = this.mSensorValue[0]
        this.mSensorValueOld[1] = this.mSensorValue[1]
        if (this.mState == 3) {
            return 3
        }
        if (CU.END_ON && CL.isAtEnd(this.ballPos)) {
            return 2
        }
        return if (CU.HOLE_ON && CL.isAtHole(this.ballPos)) 1 else 0
    }

    fun getInHoleDegree(): Int {
        val len = this.mAccleration.length()
        if (len == 0) return 0
        var degree = toDegrees(acos((this.mAccleration.y / len).toDouble())).toInt()
        if (this.mAccleration.x > 0) {
            degree = 360 - degree
        }
        return degree + 30
    }

    fun clearMemory() {
        this.mVelocity = Vector(0, 0)
        this.mAccleration = Vector(0, 0)
        this.ballPos = Point(0, 0)
        this.nextPos = Point(0, 0)
        this.mParent = null
        this.mSensorManager?.unregisterListener(this)
        this.mSensorManager = null
        this.mVibrator = null
        this.mSensorValue = floatArrayOf(0.0f, 0.0f, 0.0f)
        this.mSensorValueOld = floatArrayOf(0.0f, 0.0f, 0.0f)
        this.currentDiamond = null
        this.wall = null
        walls = null
    }

    fun fnCheckStatus(): Int {
        return this.mState
    }

    fun fnGetCenter(point: Point) {
        point.x = CU.b2s(this.ballPos.x)
        point.y = CU.b2s(this.ballPos.y)
    }

    fun fnGetVelocity(vector: Vector) {
        vector.x = this.mVelocity.x
        vector.y = this.mVelocity.y
    }

    fun fnGetAccelerate(vector: Vector) {
        vector.x = this.mAccleration.x
        vector.y = this.mAccleration.y
    }

    private fun translateCL(begin: Point): Point {
        return Point(CU.s2b(begin.x), CU.s2b(begin.y))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            event.values.copyInto(this.sensorValue, 0, 0, 3)
            if (abs(this.sensorValue[0]) <= 0.2f && abs(this.sensorValue[1]) <= 0.2f) {
                this.mSensorValue[0] = 0.0f
                this.mSensorValue[1] = 0.0f
                this.mSensorValue[2] = this.sensorValue[2]
                return
            }
            this.sensorValue.copyInto(this.mSensorValue)
        }
    }

    companion object {
        private var walls: Array<Rect>? = null

        private val top get() = CU.BALL_RADIUS_BIG
        private val left get() = CU.BALL_RADIUS_BIG
        private val right get() = CU.s2b(CU.SCREEN_WIDTH - CU.BALL_RADIUS)
        private val bottom get() = CU.s2b(CU.SCREEN_HEIGHT - CU.BALL_RADIUS)


        fun updateWallInfo() {
            walls = CL.walls_big
        }
    }
}
