package com.example.realtime_detection

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors


class StreamActivity : AppCompatActivity() {
    private val TAG = "StreamTag"
    private lateinit var imageReader: ImageReader

    @Volatile
    private var isStreaming = false

    @Volatile
    private var socket: Socket? = null

    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val cameraManager = getSystemService(CameraManager::class.java)

        val cameraId = cameraManager.cameraIdList[0]

        val mainHandler = Handler(Looper.getMainLooper())

        imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 3)

        val queue = ConcurrentLinkedQueue<ByteArray>()

        val ipAddress = "192.168.1.3:8000"

        executor.execute {
            try {
                val ip = ipAddress.split(":")[0]
                val port = ipAddress.split(":")[1]

                socket = Socket(ip, port.toInt())
                socket?.sendBufferSize = 900000000
                socket?.receiveBufferSize = 900000000


                isStreaming = !isStreaming

                val socketWriter = DataOutputStream(socket!!.getOutputStream())
                val stack = ArrayDeque<Int>(10)
                var size = 0
                var start = 0L

                while (isStreaming) {
                    val frame = try {
                        queue.remove()
                    } catch (ex: java.util.NoSuchElementException) {
                        Log.d(TAG, "Empty queue")
                        continue
                    }

                    Log.d(TAG, "Buffer size: ${queue.size}")
                    start = System.currentTimeMillis()
                    size = frame.size

                    //this just writing the int size into bytes in the stack
                    while (size > 0) {
                        stack.addLast(size % 10)
                        size /= 10
                    }
                    socketWriter.writeByte(stack.size)
                    while (stack.isNotEmpty()) {
                        socketWriter.writeByte(stack.removeLast())
                    }
                    //written the size into byte format

                    socketWriter.write(frame)
                    socketWriter.flush()
                    Log.d(TAG, "Sent to server: ${frame.size} bytes")
                    Log.d(TAG, "Elapsed to send: ${System.currentTimeMillis() - start}")
                }

            } catch (exception: java.lang.Exception) {
                exception.printStackTrace()

                socket?.close()
                socket = null
                isStreaming = false

                mainHandler.post {
                    Toast.makeText(
                        this,
                        "Could not connect to: $ipAddress",
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }
    }
}