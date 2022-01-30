package com.bovin.library

import android.text.TextUtils
import android.util.Log

import java.io.IOException
import java.lang.reflect.Array
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class TCPSocket(builder: Builder) {
    private val TAG = "TCPTCPSocket"
    private var mSendMsg: ByteArray? = null
    private var mSelector: Selector? = null
    private var mSocketChannel: SocketChannel? = null
    private var mConnectThreadPool: ThreadPoolExecutor? = null // 消息连接和接收的线程池

    /**
     * 可配置参数
     */
    private var mServerIp = ""//服务器IP
    private var mServerPort = 0//服务器端口号
    private var mConnTimeout = 0L//超时时间
    private var responseMsg: ((ByteArray) -> Unit)? = null//收到的消息
    private var errorMsg: (() -> Unit)? = null//错误消息

    /**
     *初始化
     */
    init {
        mServerIp = builder.ip
        mServerPort = builder.port
        mConnTimeout = builder.timeOut ?: 10 * 1000//如没配置，默认10秒
        responseMsg = builder.responseMsg
        errorMsg = builder.errorMsg

        mConnectThreadPool = ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(),
            { r -> Thread(r, "client_connection_thread_pool") }
        ) { r, executor ->
            Log.i(TAG, "Connected, please do not repeat the operation")
        }
        startConnectTcp()

    }

    /**
     * 请求连接服务端
     */
    private fun startConnectTcp(): TCPSocket {
        mConnectThreadPool!!.execute { initSocketAndReceiveMsgLoop() }
        return this
    }

    private fun initSocketAndReceiveMsgLoop() {
        try {
            mSocketChannel = SocketChannel.open()
            // 设置为非阻塞方式
            mSocketChannel!!.configureBlocking(false)
            // 连接服务端地址和端口
            mSocketChannel!!.connect(InetSocketAddress(mServerIp, mServerPort))

            // 注册到Selector，请求连接
            mSelector = Selector.open()
            mSocketChannel!!.register(mSelector, SelectionKey.OP_CONNECT)
            while (mSelector != null && mSelector!!.isOpen && mSocketChannel != null && mSocketChannel!!.isOpen) {
                // 选择一组对应Channel已准备好进行I/O的Key
                val select = mSelector!!.select() // 当没有消息时，这里也是会阻塞的
                if (select <= 0) {
                    continue
                }
                val selectionKeys = mSelector!!.selectedKeys()
                val iterator = selectionKeys.iterator()
                while (iterator.hasNext()) {
                    val selectionKey = iterator.next()

                    // 移除当前的key
                    iterator.remove()
                    if (selectionKey.isValid && selectionKey.isConnectable) {
                        handleConnect()
                    }
                    if (selectionKey.isValid && selectionKey.isReadable) {
                        try {
                            handleRead()
                        } catch (e: Exception) {
                            Log.e(TAG, e.message.toString())

                        }
                    }
                    if (selectionKey.isValid && selectionKey.isWritable) {
                        handleWrite()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
        } finally {
            close()
        }
    }

    @Throws(IOException::class)
    private fun handleConnect() {
        // 判断此通道上是否正在进行连接操作。
        if (mSocketChannel!!.isConnectionPending) {
            mSocketChannel!!.finishConnect()
            mSocketChannel!!.register(mSelector, SelectionKey.OP_READ)
            Log.i(TAG, "connection with the server")
        }
    }

    @Throws(IOException::class)
    private fun handleRead() {
        val byteBuffer = ByteBuffer.allocate(1024)
        val bytesRead = mSocketChannel!!.read(byteBuffer)
        if (bytesRead > 0) {
            Log.i(TAG, "TCP:from${mServerIp}data: ${Arrays.toString(byteBuffer.array())}")
            responseMsg?.let { it(byteBuffer.array()) }
        } else {
            Log.i(TAG, "disconnect from server!")
            errorMsg?.let { it() }
            close()
        }
    }

    @Throws(IOException::class)
    private fun handleWrite() {
        if (TextUtils.isEmpty(mSendMsg.toString())) {
            Log.i(TAG, "content is empty")
            return
        }
        val sendBuffer = ByteBuffer.allocate(1024)
        sendBuffer.put(mSendMsg!!)
        sendBuffer.flip()
        mSocketChannel!!.write(sendBuffer)
        Log.i(TAG, "send message： $mSendMsg")

        mSendMsg = null
        mSocketChannel!!.register(mSelector, SelectionKey.OP_READ)
    }

    /**
     * 发送数据
     *
     * @param msg
     * @throws IOException
     */
    fun sendMsg(msg: ByteArray?) {
        if (mSelector == null || !mSelector!!.isOpen || mSocketChannel == null || !mSocketChannel!!.isOpen) {
            Log.i(TAG, "Failed to send message！")
            return
        }
        try {
            mSendMsg = msg
            mSocketChannel!!.register(mSelector, SelectionKey.OP_WRITE)
            mSelector!!.wakeup()
            Log.i(TAG, "send message：$mSendMsg")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 断开连接
     */
    private fun close() {
        try {
            if (mSelector != null && mSelector!!.isOpen) {
                mSelector!!.close()
            }
            if (mSocketChannel != null && mSocketChannel!!.isOpen) {
                mSocketChannel!!.close()
            }
        } catch (e: IOException) {
            Log.e(TAG, "close: ${e.message}")
            e.printStackTrace()
        }
    }

    class Builder {
        var ip = ""
        var port = 0//端口号
        var responseMsg: ((ByteArray) -> Unit)? = null//收到的消息
        var errorMsg: (() -> Unit)? = null//错误消息
        var timeOut: Long? = null//超时时间

        //设置IP
        fun setIP(ip: String): Builder {
            this.ip = ip
            return this
        }

        //设置端口
        fun setPort(port: Int): Builder {
            this.port = port
            return this
        }

        //设置消息监听
        fun setResponseMessageListener(responseMsg: (ByteArray) -> Unit): Builder {
            this.responseMsg = responseMsg
            return this
        }

        //设置错误监听
        fun setErrorMessageListener(errorMsg: () -> Unit): Builder {
            this.errorMsg = errorMsg
            return this
        }


        //设置超时时间
        fun setTimeOut(timeOut: Long): Builder {
            this.timeOut = timeOut * 1000
            return this
        }


        fun build(): TCPSocket {
            return TCPSocket(this)
        }
    }
}
