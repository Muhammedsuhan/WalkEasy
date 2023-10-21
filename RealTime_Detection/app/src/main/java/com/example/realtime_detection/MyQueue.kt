package com.example.realtime_detection

import java.util.LinkedList
import java.util.NoSuchElementException

class MyQueue(var maxQueueSize: Int = 10) {
    // Initialize your queue
    val queue = LinkedList<ByteArray>() // You can use any appropriate queue implementation

    // Add data to the queue
    fun add(data: ByteArray) {
        synchronized(queue) {
            if (queue.size >= maxQueueSize) {
                // Remove older elements if the queue is full
                queue.removeFirst()
            }
            queue.add(data)
        }
    }

    // Retrieve and remove data from the queue
    fun remove(): ByteArray {
        synchronized(queue) {
            if (queue.isNotEmpty()) {
                return queue.removeFirst()
            }
            throw NoSuchElementException()
        }
    }

    // Periodically check and trim the queue size
    fun checkQueueSize() {
        synchronized(queue) {
            while (queue.size > maxQueueSize) {
                queue.removeFirst()
            }
        }
    }
}