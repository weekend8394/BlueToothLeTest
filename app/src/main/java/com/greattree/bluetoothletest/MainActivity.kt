package com.greattree.bluetoothletest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = MainActivity::class.java.canonicalName
    }

    private var mBluetoothLeService: BluetoothLeService? = null

    private val serviceConnect = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()

            mBluetoothLeService?.let { bluetoothLeService ->
                bluetoothLeService.initialize().also { isInitSuccess ->
                    //藍芽初始化成功
                    if (isInitSuccess) {
                        //檢查藍芽是否開啟 及 permission
                        if (bluetoothLeService.checkBlueTooth(this@MainActivity) && bluetoothLeService.hasPermission()) {
                            //開始尋找附近藍芽裝置
                            bluetoothLeService.run {
                                this.LocalBinder()
                            }
                            bluetoothLeService.startDiscovery()
                        }
                    } else {
                        Log.d(TAG, "onServiceConnected: 藍牙初始化失敗")
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBluetoothLeService = null
            Log.d(TAG, "onServiceDisconnected: 伺服器建立失敗")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //綁定伺服器
        bindService()
        tv.setOnClickListener {
            mBluetoothLeService?.startDiscovery()
        }
    }

    private fun bindService() {
        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnect, Context.BIND_AUTO_CREATE)
    }
}