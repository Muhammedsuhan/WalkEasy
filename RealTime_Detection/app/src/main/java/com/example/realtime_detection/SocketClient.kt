package com.example.realtime_detection

import android.util.Log
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class SocketClient {
    private val TAG = "StreamTag";
//    val queue = ConcurrentLinkedQueue<ByteArray>();
    val queue = MyQueue()

    @Volatile
    private var isStreaming = false;

    @Volatile
    private var socket: Socket? = null;

    private val executor = Executors.newSingleThreadExecutor();
    val ipAddress = "192.168.1.3:8000";
    fun run(){
        executor.execute {
            try {
                Log.d(TAG, "Starting");
                val ip = ipAddress.split(":")[0];
                val port = ipAddress.split(":")[1];

                socket = Socket(ip, port.toInt());
                socket?.sendBufferSize = 900000000;
                socket?.receiveBufferSize = 900000000;

                Log.d(TAG, "Connection Established");
                isStreaming = true;

                val socketWriter = DataOutputStream(socket!!.getOutputStream());
                var size = 0;
                var start = 0L;

                while (isStreaming) {
                    val frame = try {
                        queue.remove();
                    } catch (ex: java.util.NoSuchElementException) {
//                        Log.d(TAG, "Empty queue");
                        continue;
                    }

                    start = System.currentTimeMillis();
                    size = frame.size;
//                    val first20 = frame.sliceArray(0 until 20)
//                    val last20 = frame.sliceArray(frame.size - 20 until frame.size)
//                    Log.d(TAG, "${first20} ..... ${last20}");

                    socketWriter.write(frame);
                    socketWriter.flush();

                    Log.d(TAG, "Sent to server: ${frame.size} bytes");
                    Log.d(TAG, "Elapsed to send: ${System.currentTimeMillis() - start}");
                }
            } catch (exception: java.lang.Exception) {
                exception.printStackTrace()

                socket?.close()
                socket = null
                isStreaming = false
                Log.d(TAG, "Could not connect to: $ipAddress");
            }
        }
    }
}