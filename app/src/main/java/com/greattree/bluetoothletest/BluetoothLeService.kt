package com.greattree.bluetoothletest

import android.Manifest
import android.app.Activity
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.greattree.bluetoothletest.KotlinBlueUtil.byteArrayToHexStr
import com.greattree.bluetoothletest.KotlinBlueUtil.getBENEMeasure
import kotlinx.coroutines.*
import java.util.*

class BluetoothLeService : Service() , CoroutineScope by MainScope() {
    companion object {
        private val TAG = BluetoothLeService::class.java.canonicalName
        const val PERMISSION_CODE = 8686
        const val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"
    }

    private val binder = LocalBinder()
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mContext: Context? = null
    private var sendValue: ByteArray? = null
    private var mDevice: BluetoothDevice? = null
    private var beneCounter = 0
    private var needPing = false
    private var connectJob : Job?= null

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
            //BeneCheck ??????
            //FORA
            //meter ??????
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

    override fun onUnbind(intent: Intent?): Boolean {
        disconnectGatt()
        return super.onUnbind(intent)
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
                (mContext as Activity).requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_CODE)
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

    fun disconnectGatt() {
        mBluetoothGatt?.disconnect()
        mBluetoothGatt?.close()
        mBluetoothGatt = null
    }

    fun reConnectGatt() {
        connectDeviceGATT(mDevice)
    }

    private val broadCastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_PAIRING_REQUEST == action) {
                needPing = true
                Log.e(TAG, "pin entered and request sent...")
            }
        }
    }

    private fun connectDeviceGATT(device: BluetoothDevice?) {
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
        intentFilter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        mContext?.registerReceiver(broadCastReceiver, intentFilter)

        mBluetoothGatt = device?.connectGatt(mContext, false, object : BluetoothGattCallback() {
            /**
             * ????????????
             * */
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                val intentAction: String
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "onConnectionStateChange : ?????????")
                        Log.i(TAG, "Attempting to start service discovery: " + gatt?.discoverServices())
                        intentAction = ACTION_GATT_CONNECTED
                        broadcastUpdate(intentAction)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "onConnectionStateChange : ????????????")
                        intentAction = ACTION_GATT_DISCONNECTED
                        broadcastUpdate(intentAction)
                    }
                }
            }

            /**
             * ????????????
             * */
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                Log.i(TAG, "onServicesDiscovered : ????????????")
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

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

                    BluetoothGatt.GATT_FAILURE -> {

                    }
                }
            }

            fun enableNotification(characteristic: BluetoothGattCharacteristic): Boolean {
                var success = false
                mBluetoothGatt?.setCharacteristicNotification(characteristic, true)?.run {
                    Log.d(TAG, "setCharacteristicNotification: $this")
                    success = this
                    if (success) {
                        Log.d(
                            TAG,
                            "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------"
                        )
                        Log.d(TAG, "??????????????????????????????Descriptors...")
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
                }else if (needPing){
                    needPing = false
                    connectJob?.cancel()
                    connectJob = launch {
                        mBluetoothGatt?.getService(UUID.fromString("00001808-0000-1000-8000-00805f9b34fb"))?.run {
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
                                        enableNotification(characteristic)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                Log.d(TAG, "onDescriptorWrite: " + characteristic?.uuid)
                Log.d(TAG, "onCharacteristicWrite characteristic : ${characteristic?.value?.get(0)}  status $status")
                Log.d(TAG, "onCharacteristicWrite ??????????????????: ${characteristic?.value?.equals(sendValue)}")
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
                    //??????
                    device.name.contains("meter") -> {
                        val value = byteArrayToHexStr(characteristic?.value, "roche")
                        val rocheData = RocheData(byteArrayToHexStr(characteristic?.value, "date"), value!!, type!!)
                        Log.i(TAG, "onCharacteristicChanged: $rocheData  type : $type   unit : $unit")
                        broadcastUpdate(EXTRA_DATA)

                    }

                    //??????
                    device.name.contains("BeneCheck") -> {
                        beneCounter++
                        if (beneCounter <= 1) {
                            val beneValue = getBENEMeasure(byteArrayToHexStr(characteristic?.value, "bene") ?: "", unit ?: "", type)
                            Log.i(TAG, "onCharacteristicChanged: $beneValue  type : $type   unit : $unit")
                            disconnectGatt()
                            broadcastUpdate(EXTRA_DATA)
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
                                "\n????????????"
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

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        mContext?.sendBroadcast(intent)
    }
}