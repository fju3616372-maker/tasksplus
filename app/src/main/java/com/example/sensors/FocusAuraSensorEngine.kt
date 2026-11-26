package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.screentime.ScreenTimeEnforcementEngine
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FocusAuraSensorState(
  val isArmed: Boolean = false,
  val isFaceDown: Boolean = false,
  val isProximityCovered: Boolean = false,
  val isFocusSessionActive: Boolean = false,
  val accelX: Float = 0f,
  val accelY: Float = 0f,
  val accelZ: Float = 9.8f,
  val hasAccelerometer: Boolean = true,
  val hasProximitySensor: Boolean = false,
  val focusElapsedSeconds: Int = 0,
  val pickupInterruptions: Int = 0,
  val statusMessage: String = "Arm Focus Aura, then place your phone face-down to enter deep focus."
)

/**
 * Real Android hardware sensor engine for Phone-Flip "Focus Aura".
 * Uses Sensor.TYPE_ACCELEROMETER (+ Sensor.TYPE_PROXIMITY when present) to detect when the device is
 * placed flat face-down on a desk, automatically engaging ScreenTimeEnforcementEngine focus shielding
 * and starting/resuming the Task Timer with gentle haptic & audio feedback.
 */
class FocusAuraSensorEngine(
  context: Context,
  private val onFaceDownActivated: () -> Unit = {},
  private val onEarlyPickupPaused: () -> Unit = {},
  private val onMeaningfulFocusSessionEnded: (elapsedSeconds: Int) -> Unit = {}
) : SensorEventListener {

  companion object {
    /**
     * Pure deterministic orientation check (unit-testable):
     * Device is considered face-down when Z gravity is strongly negative (<= -7.5 m/s^2)
     * and X/Y tilt are reasonably flat (<= 4.5 m/s^2). If a proximity sensor is available,
     * either proximity is near OR Z is strongly inverted (<= -8.5 m/s^2).
     */
    fun isFaceDownOrientation(
      accelX: Float,
      accelY: Float,
      accelZ: Float,
      hasProximitySensor: Boolean,
      isProximityCovered: Boolean
    ): Boolean {
      val flatEnough = abs(accelX) <= 4.5f && abs(accelY) <= 4.5f
      if (!flatEnough) return false
      return if (hasProximitySensor) {
        (accelZ <= -7.2f && isProximityCovered) || accelZ <= -8.6f
      } else {
        accelZ <= -7.5f
      }
    }
  }

  private val appContext = context.applicationContext
  private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
  private val proximityMaxRange: Float = proximitySensor?.maximumRange ?: 5f

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var tickJob: Job? = null
  private var faceDownSinceMillis: Long = 0L
  private var lastAccelX = 0f
  private var lastAccelY = 0f
  private var lastAccelZ = 9.8f
  private var lastProximityCovered = false
  private var sessionCountedForStreak = false

  private val _state = MutableStateFlow(
    FocusAuraSensorState(
      hasAccelerometer = accelerometer != null,
      hasProximitySensor = proximitySensor != null,
      statusMessage = if (accelerometer != null) {
        "Arm Focus Aura, then place your phone face-down to enter deep focus."
      } else {
        "No hardware accelerometer detected on this device."
      }
    )
  )
  val state: StateFlow<FocusAuraSensorState> = _state.asStateFlow()

  fun toggleArm() {
    if (_state.value.isArmed) disarm() else arm()
  }

  fun arm() {
    if (accelerometer == null || sensorManager == null) {
      _state.update {
        it.copy(
          isArmed = false,
          hasAccelerometer = false,
          statusMessage = "Hardware accelerometer unavailable on this device."
        )
      }
      return
    }
    if (_state.value.isArmed) return
    faceDownSinceMillis = 0L
    sessionCountedForStreak = false
    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    if (proximitySensor != null) {
      sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
    }
    _state.update {
      it.copy(
        isArmed = true,
        isFocusSessionActive = false,
        focusElapsedSeconds = 0,
        pickupInterruptions = 0,
        statusMessage = "Armed! Flip your phone face-down on the desk to ignite Focus Aura."
      )
    }
    startTickerLoop()
  }

  fun disarm() {
    val current = _state.value
    val elapsed = current.focusElapsedSeconds
    if (elapsed >= 30 && !sessionCountedForStreak) {
      sessionCountedForStreak = true
      onMeaningfulFocusSessionEnded(elapsed)
    }
    sensorManager?.unregisterListener(this)
    tickJob?.cancel()
    tickJob = null
    faceDownSinceMillis = 0L
    ScreenTimeEnforcementEngine.isFocusAuraShieldActive = false
    _state.update {
      it.copy(
        isArmed = false,
        isFaceDown = false,
        isFocusSessionActive = false,
        statusMessage = if (elapsed >= 30) {
          "Focus Aura complete — ${formatDuration(elapsed)} of deep uninterrupted focus!"
        } else {
          "Focus Aura disarmed. Flip phone face-down anytime after arming."
        }
      )
    }
  }

  fun release() {
    disarm()
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event == null || !_state.value.isArmed) return
    when (event.sensor.type) {
      Sensor.TYPE_ACCELEROMETER -> {
        lastAccelX = event.values.getOrNull(0) ?: 0f
        lastAccelY = event.values.getOrNull(1) ?: 0f
        lastAccelZ = event.values.getOrNull(2) ?: 9.8f
      }
      Sensor.TYPE_PROXIMITY -> {
        val distance = event.values.getOrNull(0) ?: proximityMaxRange
        lastProximityCovered = distance < minOf(proximityMaxRange, 3f)
      }
    }
    evaluateOrientationState()
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }

  private fun evaluateOrientationState() {
    val faceDownNow = isFaceDownOrientation(
      accelX = lastAccelX,
      accelY = lastAccelY,
      accelZ = lastAccelZ,
      hasProximitySensor = proximitySensor != null,
      isProximityCovered = lastProximityCovered
    )
    val now = System.currentTimeMillis()
    val current = _state.value

    if (faceDownNow) {
      if (faceDownSinceMillis == 0L) {
        faceDownSinceMillis = now
      }
      val heldMs = now - faceDownSinceMillis
      if (!current.isFocusSessionActive && heldMs >= 1_200L) {
        ScreenTimeEnforcementEngine.isFocusAuraShieldActive = true
        vibrateSoft(80L)
        onFaceDownActivated()
        _state.update {
          it.copy(
            isFaceDown = true,
            isProximityCovered = lastProximityCovered,
            isFocusSessionActive = true,
            accelX = lastAccelX,
            accelY = lastAccelY,
            accelZ = lastAccelZ,
            statusMessage = "Focus Aura ACTIVE — Timer running & distracting apps shielded."
          )
        }
        return
      }
    } else {
      faceDownSinceMillis = 0L
      if (current.isFocusSessionActive) {
        ScreenTimeEnforcementEngine.isFocusAuraShieldActive = false
        vibrateSoft(140L)
        playGentlePickupChime()
        onEarlyPickupPaused()
        _state.update {
          it.copy(
            isFaceDown = false,
            isProximityCovered = lastProximityCovered,
            isFocusSessionActive = false,
            accelX = lastAccelX,
            accelY = lastAccelY,
            accelZ = lastAccelZ,
            pickupInterruptions = it.pickupInterruptions + 1,
            statusMessage = "Phone lifted! Focus Aura paused (${formatDuration(it.focusElapsedSeconds)} banked). Flip back down to resume."
          )
        }
        return
      }
    }

    _state.update {
      it.copy(
        isFaceDown = faceDownNow,
        isProximityCovered = lastProximityCovered,
        accelX = lastAccelX,
        accelY = lastAccelY,
        accelZ = lastAccelZ
      )
    }
  }

  private fun startTickerLoop() {
    tickJob?.cancel()
    tickJob = scope.launch {
      while (isActive && _state.value.isArmed) {
        delay(1_000L)
        // Re-evaluate debounce in case sensor events slow down on a flat table
        evaluateOrientationState()
        if (_state.value.isFocusSessionActive) {
          _state.update { it.copy(focusElapsedSeconds = it.focusElapsedSeconds + 1) }
        }
      }
    }
  }

  private fun vibrateSoft(durationMs: Long) {
    try {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
      if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(durationMs)
        }
      }
    } catch (_: Exception) {
    }
  }

  private fun playGentlePickupChime() {
    try {
      val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
      tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
      scope.launch {
        delay(260L)
        runCatching { tone.release() }
      }
    } catch (_: Exception) {
    }
  }

  private fun formatDuration(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
  }
}
