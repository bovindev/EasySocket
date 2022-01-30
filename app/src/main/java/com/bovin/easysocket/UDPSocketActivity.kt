package com.bovin.easysocket

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.bovin.library.UDPSocket
import java.lang.StringBuilder

class UDPSocketActivity : AppCompatActivity() {
    private val mEtIp: EditText by lazy { findViewById(R.id.et_ip) }
    private val mEtPort: EditText by lazy { findViewById(R.id.et_port) }
    private val mBtnConnection: Button by lazy { findViewById(R.id.btn_connection) }
    private val mRtSend: EditText by lazy { findViewById(R.id.rt_send) }
    private val mBtnSend: Button by lazy { findViewById(R.id.btn_send) }
    private val mTvReceive: TextView by lazy { findViewById(R.id.tv_receive) }

    private var mUDPSocket: UDPSocket? = null
    private val stringBuilder = StringBuilder()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_udpsocket)
        mBtnConnection.setOnClickListener {
            mUDPSocket = UDPSocket.Builder()
                .setPort(mEtPort.text.toString().toInt())
                .setAddress(mEtIp.text.toString())
                .setMessageListener {
                    stringBuilder.append(it)
                    mTvReceive.text=stringBuilder.toString()
                }
                .setNotMessageListener {
                    stringBuilder.append("已断开")
                    mTvReceive.text=stringBuilder.toString()
                }.build()
        }
        mBtnSend.setOnClickListener {
            mUDPSocket?.sendMessage(mRtSend.text.toString())
        }
    }
}