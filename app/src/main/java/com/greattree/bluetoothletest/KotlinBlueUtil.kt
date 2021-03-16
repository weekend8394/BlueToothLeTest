package com.greattree.bluetoothletest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import java.text.ParseException
import java.text.SimpleDateFormat
import kotlin.experimental.and


object KotlinBlueUtil {
    @SuppressLint("SimpleDateFormat")
    fun  byteArrayToHexStr(byteArray: ByteArray?, type: String?): String? {
        if (byteArray == null) {
            return null
        }
        var result = ""
        val data = StringBuilder(byteArray.size * 2)
        if (byteArray.size > 12) {
            when (type) {
                "date" -> {
                    var i = 0
                    while (i < byteArray.size) {
                        when (i) {
                            3 -> {
                                val date10 = Integer.toHexString(BigInteger(String.format("%02X", byteArray[4]), 16).toInt()) + Integer.toHexString(
                                    BigInteger(String.format("%02X", byteArray[3]), 16).toInt())
                                val date16 = Integer.valueOf(date10, 16).toString()
                                data.append(date16)
                                data.append("/")
                            }
                            5 -> {
                                data.append(BigInteger(String.format("%02X", byteArray[5]), 16))
                                data.append("/")
                            }
                            6 -> {
                                data.append(BigInteger(String.format("%02X", byteArray[6]), 16))
                                data.append(" ")
                            }
                            7 -> {
                                data.append(BigInteger(String.format("%02X", byteArray[7]), 16))
                                data.append(":")
                            }
                            8 -> {
                                data.append(BigInteger(String.format("%02X", byteArray[8]), 16))
                                data.append("")
                            }
                        }
                        i++
                    }
                    val spd = SimpleDateFormat("yyyy/MM/dd HH:mm")
                    try {
                        val date1 = SimpleDateFormat("yyyy/MM/dd HH:mm").parse(data.toString())
                        if (date1 != null) {
                            result = spd.format(date1)
                        }
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
                "roche" -> {
                    data.append(BigInteger(String.format("%02X", byteArray[12]), 16))
                    // 羅氏取第12個
                    data.append("")
                    result = data.toString()
                }
                "bene" -> result =
                    Integer.toHexString(BigInteger(String.format("%02X", byteArray[11]), 16).toInt()) + Integer.toHexString(BigInteger(String.format("%02X", byteArray[10]), 16).toInt())
                "type" -> {
                    result = Integer.toHexString((byteArray[1] and 0xFF.toByte()).toInt())
                    if (result.length == 1) result = "0$result"
                }
                "unit" -> {
                    result = Integer.toHexString((byteArray[0] and 0xFF.toByte()).toInt())
                    if (result.length == 1) result = "0$result"
                }
            }
        }
        return result
    }

    fun getBENEMeasure(value: String, unit: String, type: String?): String? {
        val a = value.substring(1).toInt(16)
        val b = Integer.toBinaryString(value.substring(0, 1).toInt(16))
        val c = Integer.valueOf(formatBENEMeasure(StringBuffer(b)), 2)
        val v = BigDecimal(a).multiply(BigDecimal.valueOf(Math.pow(10.0, -c.toDouble())))
        var result: BigDecimal? = null
        Log.d("TAG", "getBENEMeasure c = : $c")
        Log.d("TAG", "getBENEMeasure v = : $v")
        // 6:kg/l 5:mol/l
        var multiply = 0
        if (unit == "02") {
            multiply = 5
        } else {
            when (type) {
                "41", "61" -> multiply = 3
                "51" -> multiply = 2
            }
        }
        result =
            v.multiply(BigDecimal.valueOf(Math.pow(10.0, multiply.toDouble())).setScale(2, BigDecimal.ROUND_HALF_UP))
        //                .setScale(8, BigDecimal.ROUND_HALF_UP));
////                .multiply(new BigDecimal(10))
////                .intValue();
        return result.toString()
    }


    private fun formatBENEMeasure(str: StringBuffer): String {
        val n = str.length

        // Traverse the string to get first '1' from
        // the last of string
        var i: Int
        i = n - 1
        while (i >= 0) {
            if (str[i] == '1') break
            i--
        }

        // If there exists no '1' concat 1 at the
        // starting of string
        if (i == -1) return "1$str"

        // Continue traversal after the position of
        // first '1'
        for (k in i - 1 downTo 0) {
            // Just flip the values
            if (str[k] == '1') str.replace(k, k + 1, "0") else str.replace(k, k + 1, "1")
        }

        // return the modified string
        return str.toString()
    }

    /**
     * @return Returns **true** if property is writable
     */
    fun isCharacteristicWritable(pChar: BluetoothGattCharacteristic): Boolean {
        return pChar.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    }

    /**
     * @return Returns **true** if property is Readable
     */
    fun isCharacteristicReadable(pChar: BluetoothGattCharacteristic): Boolean {
        return pChar.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    }

    /**
     * @return Returns **true** if property is supports notification
     */
    fun isCharacteristicNotifiable(pChar: BluetoothGattCharacteristic): Boolean {
        return pChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }

    /**
     * @return Returns **true** if property is supports Indicate
     */
    fun isCharacteristicIndicate(pChar: BluetoothGattCharacteristic): Boolean {
        return pChar.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
    }
}