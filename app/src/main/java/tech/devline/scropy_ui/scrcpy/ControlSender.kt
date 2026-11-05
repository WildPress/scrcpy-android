package tech.devline.scropy_ui.scrcpy

import android.util.Log
import android.view.MotionEvent
import tech.devline.scropy_ui.adb.AdbStream
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

private const val TAG = "ControlSender"

/**
 * Serializes and sends scrcpy control messages to the device.
 *
 * Writes happen on a dedicated sender thread to avoid blocking the UI
 * thread on ADB flow-control (WRTE/OKAY round-trips).
 */
class ControlSender(private val stream: AdbStream) {

    @Volatile var deviceWidth:  Int = 0
    @Volatile var deviceHeight: Int = 0

    private val sendQueue = LinkedBlockingQueue<ByteArray>(64)
    @Volatile private var running = true

    private val senderThread = Thread({
        while (running) {
            try {
                val data = sendQueue.take()
                stream.write(data)
            } catch (_: InterruptedException) {
                break
            } catch (e: IOException) {
                Log.w(TAG, "Control send failed: ${e.message}")
            }
        }
    }, "control-sender").apply { isDaemon = true; start() }

    fun sendTouchEvent(event: MotionEvent, surfaceWidth: Int, surfaceHeight: Int) {
        if (deviceWidth == 0 || deviceHeight == 0) return

        val action    = event.actionMasked
        val scrcpyAct = mapAction(action) ?: return

        val pointerIdx = event.actionIndex
        val pointerId  = normalizePointerId(event.getPointerId(pointerIdx))

        val x = (event.getX(pointerIdx) / surfaceWidth  * deviceWidth).toInt()
        val y = (event.getY(pointerIdx) / surfaceHeight * deviceHeight).toInt()

        val pressure     = event.getPressure(pointerIdx)
        val actionButton = event.actionButton
        val buttons      = event.buttonState

        send(ScrcpyProtocol.buildTouchEvent(
            scrcpyAct, pointerId, x, y, deviceWidth, deviceHeight,
            pressure, actionButton, buttons,
        ))
    }

    fun sendScrollEvent(event: MotionEvent, surfaceWidth: Int, surfaceHeight: Int) {
        val x = (event.x / surfaceWidth  * deviceWidth).toInt()
        val y = (event.y / surfaceHeight * deviceHeight).toInt()
        val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        send(ScrcpyProtocol.buildScrollEvent(x, y, deviceWidth, deviceHeight, hScroll, vScroll))
    }

    fun sendKeyEvent(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) =
        send(ScrcpyProtocol.buildKeyEvent(action, keycode, repeat, metaState))

    fun sendText(text: String) = send(ScrcpyProtocol.buildTextEvent(text))

    fun sendBackButton()              = send(ScrcpyProtocol.buildEmpty(ScrcpyProtocol.TYPE_BACK_OR_SCREEN_ON))
    fun sendCollapseNotifications()   = send(ScrcpyProtocol.buildEmpty(ScrcpyProtocol.TYPE_COLLAPSE_PANELS))
    fun sendExpandNotifications()     = send(ScrcpyProtocol.buildEmpty(ScrcpyProtocol.TYPE_EXPAND_NOTIFICATION))
    fun sendRotateDevice()            = send(ScrcpyProtocol.buildEmpty(ScrcpyProtocol.TYPE_ROTATE_DEVICE))
    fun sendResetVideo()              = send(ScrcpyProtocol.buildEmpty(ScrcpyProtocol.TYPE_RESET_VIDEO))
    fun sendDisplayPower(on: Boolean) = send(ScrcpyProtocol.buildDisplayPower(on))

    fun stop() {
        running = false
        senderThread.interrupt()
    }

    private fun send(data: ByteArray) {
        if (!sendQueue.offer(data)) {
            Log.d(TAG, "Send queue full, dropping control message")
        }
    }

    private fun mapAction(action: Int): Int? = when (action) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN -> 0
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP   -> 1
        MotionEvent.ACTION_MOVE         -> 2
        else -> null
    }

    private fun normalizePointerId(androidId: Int): Long =
        if (androidId == 0) ScrcpyProtocol.POINTER_MOUSE else androidId.toLong()
}
