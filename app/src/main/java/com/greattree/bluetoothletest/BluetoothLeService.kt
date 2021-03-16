package com.greattree.bluetoothletest

import android.Manifest
import android.app.Activity
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.greattree.bluetoothletest.KotlinBlueUtil.byteArrayToHexStr
import com.greattree.bluetoothletest.KotlinBlueUtil.getBENEMeasure
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class BluetoothLeService : Service() {
    companion object {
        private val TAG = BluetoothLeService::class.java.canonicalName
    }

    private val binder = LocalBinder()
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mContext: Context? = null
    private var sendValue: ByteArray? = null
    private var mDevice:BluetoothDevice? = null
    private var beneCounter = 0

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (result.device?.name.isNullOrBlank()) {
                return
            }
            val device: BluetoothDevice = result.device
            val deviceName = device.name ?: ""
            val deviceHardwareAddress = device.address ?: ""
            val deviceUUID = result.scanRecord?.serviceUuids
            Log.i(TAG, "  \n*deviceName : $deviceName  \n*deviceHardwareAddress : $deviceHardwareAddress  \n*deviceUUID : $deviceUUID")
            //BeneCheck 信昌
            //FORA
            //meter 羅市
            if ((deviceName.contains("meter") || deviceName.contains("BeneCheck") || deviceName.contains("FORA")) && scanning) {
                mDevice = device
                stopScan()
                connectDeviceGATT(device)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    fun initialize(): Boolean {
        // If bluetoothManager is null, try to set it
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(BluetoothManager::class.java)
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        // For API level 18 and higher, get a reference to BluetoothAdapter through
        // BluetoothManager.
        mBluetoothManager?.let { manager ->
            mBluetoothAdapter = manager.adapter
            if (mBluetoothAdapter == null) {
                Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
                return false
            }
            mBluetoothLeScanner = mBluetoothAdapter?.bluetoothLeScanner
            return true
        } ?: return false
    }

    fun checkBlueTooth(context: Context): Boolean {
        mContext = context
        if (mBluetoothAdapter != null && mBluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            (context as Activity).startActivityForResult(enableBtIntent, 123)
            return false
        }
        return true
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mContext?.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                (mContext as Activity).requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 123)
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    fun startDiscovery() {
        mBluetoothLeScanner?.let {
            if (!scanning) {
                startScan()
            } else {
                stopScan()
            }
        }
    }

    private fun startScan() {
        scanning = true
        mBluetoothLeScanner?.startScan(leScanCallback)
    }

    private fun stopScan() {
        scanning = false
        mBluetoothLeScanner?.stopScan(leScanCallback)
    }

    fun disconnectGatt(){
        mBluetoothGatt?.disconnect()
        mBluetoothGatt?.close()
        mBluetoothGatt = null
    }

    fun reConnectGatt(){
        connectDeviceGATT(mDevice)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnectGatt()
        return super.onUnbind(intent)
    }

    private fun connectDeviceGATT(device: BluetoothDevice?) {
        mBluetoothGatt = device?.connectGatt(mContext, false, object : BluetoothGattCallback() {
            /**
             * 連線狀態
             * */
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "onConnectionStateChange : 已連線")
                        Log.i(TAG, "Attempting to start service discovery: " + gatt?.discoverServices())
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "onConnectionStateChange : 失去連線")
                    }
                }
            }

            /**
             * 發現服務
             * */
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                Log.i(TAG, "onServicesDiscovered : 發現服務")
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        var isEnableNotification = false

                        gatt?.services?.forEach { service ->
                            if (isEnableNotification) {
                                return@forEach
                            }
                            Log.i(
                                TAG,
                                "-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- \n" +
                                        "service.uuid : ${service.uuid}"
                            )

                            GlobalScope.launch {
                                if (service.uuid == UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")) {
                                    service?.run {
                                        this.characteristics.forEach { characteristic ->
                                            Log.d(TAG, "characteristic.uuid: ${characteristic.uuid}")
                                            characteristic?.also { c ->
                                                if (KotlinBlueUtil.isCharacteristicNotifiable(characteristic) || KotlinBlueUtil.isCharacteristicIndicate(characteristic)) {
                                                    Log.e(TAG, "characteristicUUID: ${c.uuid}")
                                                    Log.e(TAG, "isWritable : ${KotlinBlueUtil.isCharacteristicWritable(characteristic)}")
                                                    Log.e(TAG, "isReadable : ${KotlinBlueUtil.isCharacteristicReadable(characteristic)}")
                                                    Log.e(TAG, "isNotifiable : ${KotlinBlueUtil.isCharacteristicNotifiable(characteristic)}")
                                                    Log.e(TAG, "isIndicate : ${KotlinBlueUtil.isCharacteristicIndicate(characteristic)}")
                                                    delay(1000)
                                                    if (enableNotification(characteristic)) {
                                                        isEnableNotification = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            fun enableNotification(characteristic: BluetoothGattCharacteristic): Boolean {
                var success = false
                mBluetoothGatt?.setCharacteristicNotification(characteristic, true)?.run {
                    Log.d(TAG, "setCharacteristicNotification: $this")
                    Thread.sleep(1000)
                    success = this
                    if (success) {
                        Log.d(
                            TAG,
                            "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------"
                        )
                        Log.d(TAG, "開始尋找可通知更新的Descriptors...")
                        characteristic.descriptors.forEach { descriptor ->
                            descriptor?.run {
                                Log.d(TAG, "descriptors.uuid : " + descriptor.uuid)
                                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    Log.d(TAG, "enableNotification PROPERTY_NOTIFY")
                                } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                    Log.d(TAG, "enableNotification PROPERTY_INDICATE}")
                                }
                                descriptor.value = byteArrayOf(0x01, 0x06)
                                mBluetoothGatt?.writeDescriptor(descriptor)
                            }
                        }
                        Log.d(
                            TAG,
                            "---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------"
                        )
                    }
                }
                return success
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor, status: Int) {
                super.onDescriptorWrite(gatt, descriptor, status)
                Log.d(TAG, "onDescriptorWrite: " + descriptor.uuid)
                Log.d(TAG, "onDescriptorWrite: $status")
                if (KotlinBlueUtil.isCharacteristicWritable(descriptor.characteristic)) {
                    writeCustomCharacteristic(descriptor.characteristic)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                Log.d(TAG, "onDescriptorWrite: " + characteristic?.uuid)
                Log.d(TAG, "onCharacteristicWrite characteristic : ${characteristic?.value?.get(0)}  status $status")
                Log.d(TAG, "onCharacteristicWrite 檢查寫入資料: ${characteristic?.value?.equals(sendValue)}")
            }

            override fun onDescriptorRead(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                super.onDescriptorRead(gatt, descriptor, status)
                Log.d(TAG, "onDescriptorRead: $status")
                Log.d(TAG, "onDescriptorRead descriptor : $descriptor")
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                Log.d(TAG, "onCharacteristicRead: $status")
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                val type = byteArrayToHexStr(characteristic?.value, "type")
                val unit: String? = byteArrayToHexStr(characteristic?.value, "unit")

                when {
                    //羅氏
                    device.name.contains("meter") -> {
                        val value = byteArrayToHexStr(characteristic?.value, "roche")
                        val rocheData = RocheData(byteArrayToHexStr(characteristic?.value, "date"), value!!, type!!)
                        Log.i(TAG, "onCharacteristicChanged: $rocheData  type : $type   unit : $unit")
                    }

                    //杏倉
                    device.name.contains("BeneCheck") -> {
                        beneCounter++
                        if(beneCounter <=1){
                            val beneValue = getBENEMeasure(byteArrayToHexStr(characteristic?.value, "bene") ?: "", unit ?: "", type)
                            Log.i(TAG, "onCharacteristicChanged: $beneValue  type : $type   unit : $unit")
                            disconnectGatt()
                        }
                    }

                    device.name.contains("FORA") -> {

                    }
                }
            }

            fun writeCustomCharacteristic(characteristic: BluetoothGattCharacteristic?) {
                val byte = byteArrayOf(0x01, 0x01)
                characteristic?.value = byte
                mBluetoothGatt?.run {
                    Log.d(
                        TAG,
                        "------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------" +
                                "\n寫入資料"
                    )
                    Log.e(TAG, "isWriteSuccess : ${this.writeCharacteristic(characteristic)}")
                    Log.d(TAG, "|TESTTEST service: " + characteristic?.service)
                    Log.d(TAG, "|TESTTEST service.uuid: " + characteristic?.service?.uuid)
                    Log.d(TAG, "|TESTTEST characteristic.uuid: " + characteristic?.uuid)
                    Log.d(TAG, "|TESTTEST writeType:" + characteristic?.writeType)
                    Log.d(
                        TAG,
                        "------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------"
                    )
                }
            }
        })
    }
}