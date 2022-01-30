package com.bovin.easysocket

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private val mBtnTcp: Button by lazy { findViewById(R.id.btn_tcp) }
    private val mBtnUdp: Button by lazy { findViewById(R.id.btn_udp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mBtnTcp.setOnClickListener {
            startActivity(Intent(this, TCPSocketActivity::class.java))
        }
        mBtnUdp.setOnClickListener {
            startActivity(Intent(this, UDPSocketActivity::class.java))
        }
    }
}