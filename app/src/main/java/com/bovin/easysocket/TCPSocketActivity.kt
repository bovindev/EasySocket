package com.bovin.easysocket

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bovin.library.TCPSocket

class TCPSocketActivity : AppCompatActivity() {
    private val mEtIp: EditText by lazy { findViewById(R.id.et_ip) }
    private val mEtPort: EditText by lazy { findViewById(R.id.et_port) }
    private val mBtnConnection: Button by lazy { findViewById(R.id.btn_connection) }
    private val mRtSend: EditText by lazy { findViewById(R.id.rt_send) }
    private val mBtnSend: Button by lazy { findViewById(R.id.btn_send) }
    private val mTvReceive: TextView by lazy { findViewById(R.id.tv_receive) }

    private var mTCPSocket: TCPSocket? = null
    private val stringBuilder = StringBuilder()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tcpsocket)
        mBtnConnection.setOnClickListener {
            mTCPSocket = TCPSocket.Builder()
                .setIP(mEtIp.text.toString())
                .setPort(mEtPort.text.toString().toInt())
                .setResponseMessageListener {
                    stringBuilder.append(it.toString())
                    mTvReceive.text = stringBuilder.toString()
                }.build()
        }
        mBtnSend.setOnClickListener {
            mTCPSocket?.sendMsg(mRtSend.text.toString().toByteArray())
        }
    }
}