package com.example.realtime_detection

import android.provider.ContactsContract.Data
import android.util.Log
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class UDP_Socket_Client {
    private val TAG = "StreamTag";
    val queue = ConcurrentLinkedQueue<ByteArray>();
//    val queue = MyQueue(20)

    @Volatile
    private var isStreaming = false;

    @Volatile
    var socket: DatagramSocket? = null;
    val executor = Executors.newSingleThreadExecutor();
    val ipAddress = "192.168.1.5:8000";
    fun run(){
        executor.execute {
            try {
                Log.d(TAG, "Starting");
                val ip = Inet4Address.getByName(ipAddress.split(":")[0]);
                val port = (ipAddress.split(":")[1]).toInt();

                socket = DatagramSocket();
                socket?.sendBufferSize = 900000000;
                socket?.receiveBufferSize = 900000000;

                Log.d(TAG, "Connection Established");
                isStreaming = true;

                var start = 0L;

                while (isStreaming) {
                    val frame = try {
                        queue.remove();
                    } catch (ex: java.util.NoSuchElementException) {
//                        Log.d(TAG, "Empty queue");
                        continue;
                    }

                    start = System.currentTimeMillis();

                    val packet = DatagramPacket(frame, frame.size, ip, port);
                    socket!!.send(packet);

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