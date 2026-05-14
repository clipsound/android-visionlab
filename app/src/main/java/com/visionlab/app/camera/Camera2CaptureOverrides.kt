package com.visionlab.app.camera

import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import kotlin.math.exp
import kotlin.math.ln

private const val TAG = "VisionLab"

@OptIn(ExperimentalCamera2Interop::class)
object Camera2CaptureOverrides {

    fun setAutoExposure(camera: Camera) {
        runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                .build()
            Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(opts)
        }.onFailure { Log.w(TAG, "AE automatica: ${it.message}") }
    }

    fun setManualShutterAndGain(
        camera: Camera,
        exposureSlider01: Float,
        gainSlider01: Float,
        exposureMinNs: Long,
        exposureMaxNs: Long,
        isoMin: Int,
        isoMax: Int,
    ) {
        if (exposureMaxNs <= exposureMinNs || isoMax <= isoMin) return
        val t = exposureSlider01.coerceIn(0f, 1f)
        val g = gainSlider01.coerceIn(0f, 1f)
        val expNs = lerpLogNs(exposureMinNs, exposureMaxNs, t)
        val iso = (isoMin + (isoMax - isoMin) * g).toInt().coerceIn(isoMin, isoMax)
        runCatching {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, expNs)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                .build()
            Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(opts)
        }.onFailure { Log.w(TAG, "AE manuale (shutter/ISO): ${it.message}") }
    }

    private fun lerpLogNs(minNs: Long, maxNs: Long, t: Float): Long {
        val a = minNs.toDouble().coerceAtLeast(1.0)
        val b = maxNs.toDouble().coerceAtLeast(a + 1.0)
        val u = t.toDouble().coerceIn(0.0, 1.0)
        val v = exp(ln(a) + (ln(b) - ln(a)) * u)
        return v.toLong().coerceIn(minNs, maxNs)
    }

    /** Valore UI (etichetta) allineato allo shutter effettivo in modalità manuale. */
    fun previewExposureNanoseconds(
        exposureSlider01: Float,
        exposureMinNs: Long,
        exposureMaxNs: Long,
    ): Long {
        if (exposureMaxNs <= exposureMinNs) return 0L
        return lerpLogNs(exposureMinNs, exposureMaxNs, exposureSlider01)
    }

    fun previewIsoValue(
        gainSlider01: Float,
        isoMin: Int,
        isoMax: Int,
    ): Int {
        if (isoMax <= isoMin) return 0
        val g = gainSlider01.coerceIn(0f, 1f)
        return (isoMin + (isoMax - isoMin) * g).toInt().coerceIn(isoMin, isoMax)
    }
}
