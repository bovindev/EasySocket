package com.bovin.library

import android.util.Log
import java.io.IOException
import java.net.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * UDP连接
 */
class UDPSocket(builder: Builder) {
    private val TAG = "UDPSocket"
    private var lastReceiveTime = System.currentTimeMillis()//创建对象时的时间，最后接收的时间
    private val CPU_NUMBER = Runtime.getRuntime().availableProcessors()//Cpu 的数量
    private var mThreadPool: ExecutorService? = null//线程池
    private var client: DatagramSocket? = null
    private var receivePacket: DatagramPacket? = null//接收到的数据包
    private var clientThread: Thread? = null//客户线程
    private var isThreadRunning = false//线程是否在运行
    private var timer: HeartbeatTimer? = null//计时器

    /**
     * 可配置项
     */
    private var port: Int = 0//端口号
    private var address = ""
    private var msg: ((String) -> Unit)? = null//收到的消息
    private var notMsg: (() -> Unit)? = null//收不到的消息
    private var timeOut = 0L//超时时间
    private var heartbeatTime = 0L//心跳间隔时间
    private var poolSize = 0//线程数
    private var bufferLength = 0//要读取的字节长度
    private var receiveByte: ByteArray? = null


    init {
        this.port = builder.port
        this.address = builder.address
        this.msg = builder.msg
        this.notMsg = builder.notMsg
        this.timeOut = builder.timeOut ?: (120 * 1000).toLong()
        this.heartbeatTime = builder.heartbeatTime ?: (5 * 1000).toLong()
        this.bufferLength = builder.bufferLength ?: 1024
        this.receiveByte = ByteArray(bufferLength)
        this.poolSize = builder.poolSize ?: 5

        // 根据CPU数目初始化线程池
        mThreadPool =
            Executors.newFixedThreadPool(CPU_NUMBER * poolSize)
        startUDPSocket()
    }

    private fun startUDPSocket() {
        if (client != null) return
        try {
            client = DatagramSocket(port)
            if (receivePacket == null) {
                receivePacket = DatagramPacket(
                    receiveByte,
                    bufferLength
                )
            }
            startSocketThread()
        } catch (e: SocketException) {
            Log.e(TAG, e.toString())
        }
    }

    //开启发送数据的线程
    private fun startSocketThread() {
        clientThread = Thread {
            Log.d(
                TAG,
                "Receive thread runs"
            )
            receiveMessage()
        }
        isThreadRunning = true
        clientThread?.start()
        startHeartbeatTimer()
    }


    //处理接受到的消息
    private fun receiveMessage() {
        while (isThreadRunning) {
            try {
                client?.receive(receivePacket)
                lastReceiveTime = System.currentTimeMillis()
                Log.i(TAG, "${port}:accept success!")
            } catch (e: IOException) {
                Log.e(TAG, "${port}:packet reception failed！thread stop!${e.message}")
                stopUDPSocket()
                return
            }
            if (receivePacket == null || receivePacket!!.length == 0) {
                Log.e(TAG, "${port}:Unable to receive UDP data or the received UDP data is empty")
                continue
            }
            val strReceive = String(receivePacket!!.data, 0, receivePacket!!.length)
            Log.d(
                TAG,
                "$strReceive from ${receivePacket?.address?.hostAddress}: ${receivePacket?.port}"
            )
            msg?.let { it(strReceive) }
            // 每次接收完UDP数据后，重置长度。否则可能会导致下次收到数据包被截断。
            receivePacket?.let {
                it.length = bufferLength
            }
            Log.i(
                TAG,
                "$strReceive from ${receivePacket?.address?.hostAddress}: ${receivePacket?.port}"
            )
        }
    }

    private fun stopUDPSocket() {
        isThreadRunning = false
        receivePacket = null
        clientThread?.interrupt()
        client?.let {
            it.close()
            client = null
        }
        timer?.exit()
    }


    //启动心跳，timer 间隔五秒
    private fun startHeartbeatTimer() {
        timer = HeartbeatTimer()

        timer?.setOnScheduleListener {
            Log.d(TAG, "timer starts")
            val duration = System.currentTimeMillis() - lastReceiveTime
            Log.d(TAG, "duration:$duration")
            if (duration > timeOut) { //若超过timeOut设置的的时间都没收到我心跳包，则认为对方不在线。
                Log.d(TAG, "${port}:Timeout, the other party has been offline ")
                notMsg?.let { it() }
                // 刷新时间，重新进入下一个心跳周期
                lastReceiveTime = System.currentTimeMillis()
            } else if (duration > heartbeatTime) { //若超过五秒他没收到我的心跳包，则重新发一个。
                val string = "hello,this is a heartbeat message"
                sendMessage(string)
            }
        }
        timer?.startTimer(0, 1000 * 10)
    }


    fun sendMessage(message: String) {
        mThreadPool?.execute {
            try {
                val targetAddress =
                    InetAddress.getByName(address)
                val packet = DatagramPacket(
                    message.toByteArray(),
                    message.length,
                    targetAddress,
                    port
                )
                client?.send(packet)
                // 数据发送事件
                Log.d(TAG, "data sent successfully${message}")
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    class Builder {
        var port: Int = 0//端口号
        var address: String = ""
        var msg: ((String) -> Unit)? = null//收到的消息
        var notMsg: (() -> Unit)? = null//收不到的消息
        var bufferLength: Int? = null//要读取的字节长度
        var timeOut: Long? = null//超时时间
        var heartbeatTime: Long? = null//心跳间隔时间
        var poolSize: Int? = null//线程数

        //设置端口
        fun setPort(port: Int): Builder {
            this.port = port
            return this
        }

        //设置地址
        fun setAddress(address: String): Builder {
            this.address = address
            return this
        }

        //设置消息监听
        fun setMessageListener(msg: (String) -> Unit): Builder {
            this.msg = msg
            return this
        }

        //设置收不到消息的监听
        fun setNotMessageListener(notMsg: () -> Unit): Builder {
            this.notMsg = notMsg
            return this
        }

        //设置要读取的字节长度
        fun setBufferLength(bufferLength: Int): Builder {
            this.bufferLength = bufferLength
            return this

        }

        //设置超时时间
        fun setTimeOut(timeOut: Long): Builder {
            this.timeOut = timeOut * 1000
            return this
        }

        //设置心跳间隔
        fun setHeartbeatTime(heartbeatTime: Long): Builder {
            this.heartbeatTime = heartbeatTime * 1000
            return this
        }

        //设置线程数
        fun setPoolSize(poolSize: Int): Builder {
            this.poolSize = poolSize
            return this
        }

        fun build(): UDPSocket {
            return UDPSocket(this)
        }
    }


}

class HeartbeatTimer {
    private var timer: Timer = Timer()
    private var task: TimerTask? = null

    var listener: (() -> Unit)? = null

    fun startTimer(delay: Long, period: Long) {
        task = object : TimerTask() {
            override fun run() {
                listener?.let { it() }
            }
        }
        timer.schedule(task, delay, period)
    }

    fun exit() {
        timer.cancel()
        task?.cancel()
    }

    fun setOnScheduleListener(listener: (() -> Unit)) {
        this.listener = listener
    }
}

