package com.example.realtime_detection

import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class TCP_Socket_Client {
    private val TAG = "StreamTag";
    val queue = ConcurrentLinkedQueue<ByteArray>();

    private var isStreaming = false;
    var socket: Socket? = null;
    val executor = Executors.newSingleThreadExecutor();
    val ipAddress = "192.168.1.5:8000";

    fun addToQueue(image : TensorImage){
        var buffer = image.buffer;
//                Log.d("StreamTag", "Making byte array ${buffer.remaining()}");
        var byteArray = ByteArray(buffer.remaining());
//                Log.d("StreamTag", "copying byte array");
        buffer.get(byteArray);

        byteArray = "FRAME_START".toByteArray() + byteArray;
        queue.add(byteArray);
        Log.d("StreamTag", "queue size = ${queue.size}");
    }
    fun run(){
        executor.execute {
            try {
                // grap the ip and port
                Log.d(TAG, "Starting");
                val ip = ipAddress.split(":")[0];
                val port = ipAddress.split(":")[1];

                //initialize the socket object
                socket = Socket(ip, port.toInt());
                socket?.sendBufferSize = 900000000;
                socket?.receiveBufferSize = 900000000;

                Log.d(TAG, "Connection Established");
                isStreaming = true;

                //we need the TcP output stream to write to
                val socketWriter = DataOutputStream(socket!!.getOutputStream());
                var size = 0;
                var start = 0L;

                while (isStreaming) {
                    val frame = try {
                        queue.remove();
                    } catch (ex: java.util.NoSuchElementException) {
                        // Log.d(TAG, "Empty queue");
                        continue;
                    }

                    start = System.currentTimeMillis();

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