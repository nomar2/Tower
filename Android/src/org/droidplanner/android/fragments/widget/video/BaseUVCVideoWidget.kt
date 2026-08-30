package org.droidplanner.android.fragments.widget.video

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.TextView
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import org.droidplanner.android.R
import org.droidplanner.android.fragments.widget.TowerWidget
import org.droidplanner.android.fragments.widget.TowerWidgets

/**
 * Live preview from a USB Video Class (UVC) device — e.g. an Eachine ROTG /
 * Skydroid 5.8 GHz FPV receiver plugged into the phone's USB-OTG port.
 *
 * Backed by the maintained UVCAndroid library ([ICameraHelper]), which manages
 * the USB monitor, permission request and the native UVC pipeline. This widget
 * only draws the preview onto the [TextureView]; photo / video capture are not
 * wired up.
 */
abstract class BaseUVCVideoWidget : TowerWidget() {

    private val TAG = "UVCVideoWidget"

    protected val ASPECT_RATIO_4_3 = 3f / 4f
    protected val ASPECT_RATIO_16_9 = 9f / 16f
    protected val ASPECT_RATIO_21_9 = 9f / 21f
    protected val ASPECT_RATIO_1_1 = 1f
    protected var aspectRatio = ASPECT_RATIO_4_3

    private var cameraHelper: ICameraHelper? = null
    private var previewSurface: Surface? = null
    private var cameraOpen = false

    // Preview dimensions reported by the device, used by adjustAspectRatio.
    protected var previewWidth = 640
    protected var previewHeight = 480

    override fun getWidgetType() = TowerWidgets.UVC_VIDEO

    protected val textureView by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById<TextureView>(R.id.uvc_video_view)
    }

    private val videoStatus by lazy(LazyThreadSafetyMode.NONE) {
        view?.findViewById<TextView>(R.id.uvc_video_status)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureView?.surfaceTextureListener = surfaceTextureListener
        textureView?.let { tv ->
            if (tv.isAvailable) {
                onSurfaceReady(tv.surfaceTexture)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startCamera()
    }

    override fun onStop() {
        super.onStop()
        stopCamera()
    }

    override fun onApiConnected() {}

    override fun onApiDisconnected() {}

    private fun startCamera() {
        if (cameraHelper != null) return
        val helper = try {
            CameraHelper()
        } catch (t: Throwable) {
            Log.w(TAG, "UVCAndroid unavailable", t)
            showStatus(true)
            return
        }
        helper.setStateCallback(stateCallback)
        cameraHelper = helper
        showStatus(true)
        // The device may already be attached when the widget opens; the library's
        // periodic scan will also re-notify onAttach shortly.
        helper.deviceList?.firstOrNull { isUvcDevice(it) }?.let { helper.selectDevice(it) }
    }

    /**
     * The shared USB device filter also matches serial adapters (Pixhawk etc.),
     * so make sure we only ever select an actual video-class device.
     */
    private fun isUvcDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            device.deviceClass == UsbConstants.USB_CLASS_MISC) {
            return true
        }
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                return true
            }
        }
        return false
    }

    private fun stopCamera() {
        cameraOpen = false
        cameraHelper?.let { helper ->
            previewSurface?.let { s -> runCatching { helper.removeSurface(s) } }
            runCatching { helper.release() }
        }
        cameraHelper = null
        showStatus(true)
    }

    private val stateCallback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            // Request permission for / select the freshly attached device
            // (ignore serial adapters that also match the shared filter).
            if (isUvcDevice(device)) {
                cameraHelper?.selectDevice(device)
            }
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            cameraHelper?.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            val helper = cameraHelper ?: return
            runCatching {
                helper.startPreview()
                helper.previewSize?.let { size ->
                    previewWidth = size.width
                    previewHeight = size.height
                }
            }.onFailure { Log.w(TAG, "startPreview failed", it) }
            cameraOpen = true
            activity?.runOnUiThread {
                textureView?.let { adjustAspectRatio(it) }
                showStatus(false)
            }
            attachSurfaceIfReady()
        }

        override fun onCameraClose(device: UsbDevice) {
            cameraOpen = false
            previewSurface?.let { s -> runCatching { cameraHelper?.removeSurface(s) } }
            activity?.runOnUiThread { showStatus(true) }
        }

        override fun onDeviceClose(device: UsbDevice) {}

        override fun onDetach(device: UsbDevice) {
            cameraOpen = false
            activity?.runOnUiThread { showStatus(true) }
        }

        override fun onCancel(device: UsbDevice) {
            activity?.runOnUiThread { showStatus(true) }
        }

        override fun onError(device: UsbDevice?, e: CameraException?) {
            Log.w(TAG, "UVC camera error", e)
            cameraOpen = false
            activity?.runOnUiThread { showStatus(true) }
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            onSurfaceReady(surface)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            previewSurface?.let { s ->
                runCatching { cameraHelper?.removeSurface(s) }
                s.release()
            }
            previewSurface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun onSurfaceReady(surfaceTexture: SurfaceTexture?) {
        if (surfaceTexture == null) return
        previewSurface?.release()
        previewSurface = Surface(surfaceTexture)
        textureView?.let { adjustAspectRatio(it) }
        attachSurfaceIfReady()
    }

    private fun attachSurfaceIfReady() {
        val helper = cameraHelper ?: return
        val surface = previewSurface ?: return
        if (!cameraOpen) return
        runCatching { helper.addSurface(surface, false) }
            .onFailure { Log.w(TAG, "addSurface failed", it) }
    }

    private fun showStatus(visible: Boolean) {
        videoStatus?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    protected fun adjustAspectRatio(textureView: TextureView) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth == 0 || viewHeight == 0) return

        val newWidth: Int
        val newHeight: Int
        if (viewHeight > (viewWidth * aspectRatio)) {
            newWidth = viewWidth
            newHeight = (viewWidth * aspectRatio).toInt()
        } else {
            newHeight = viewHeight
            newWidth = (viewHeight / aspectRatio).toInt()
        }

        val xoff = (viewWidth - newWidth) / 2f
        val yoff = (viewHeight - newHeight) / 2f

        val transform = Matrix()
        textureView.getTransform(transform)
        transform.setScale(newWidth.toFloat() / viewWidth, newHeight.toFloat() / viewHeight)
        transform.postTranslate(xoff, yoff)
        textureView.setTransform(transform)
    }
}
