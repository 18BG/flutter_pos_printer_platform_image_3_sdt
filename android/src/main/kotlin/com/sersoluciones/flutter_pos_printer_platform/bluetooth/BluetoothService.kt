package com.sersoluciones.flutter_pos_printer_platform.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sersoluciones.flutter_pos_printer_platform.models.LocalBluetoothDevice
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result


class BluetoothService(mContext: Context, private var bluetoothHandler: Handler?) {
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentActivity: Activity? = null
    private var mConnectedDeviceAddress: String? = ""
    private val mHandlerAutoConnect = Handler(Looper.getMainLooper())
    private var reconnectBluetooth = false
    private var result: Result? = null
    private val appContext: Context = mContext.applicationContext
    private var classicDiscoveryReceiver: BroadcastReceiver? = null
    private var classicDiscoveryChannel: MethodChannel? = null
    private val classicDiscoveryAddresses: MutableSet<String> = mutableSetOf()

    val mBluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        mBluetoothAdapter.bluetoothLeScanner
    }
    private var devicesBle: MutableList<LocalBluetoothDevice> = mutableListOf()

    init {
        scanning = false
    }

    fun setHandler(handler: Handler?) {
        bluetoothHandler = handler
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Scan bluetooth
    ////////////////////////////////////////////////////////////////////////////////////////////////
    fun scanBluDevice(mChannel: MethodChannel) {
        val list = ArrayList<HashMap<*, *>>()
        bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_START_SCANNING, -1, -1)
            ?.sendToTarget()
        val pairedDevices: Set<BluetoothDevice>? = mBluetoothAdapter.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName =
                if (device.name == null) device.address else device.name
            val deviceHardwareAddress = device.address // MAC address
            val majorClass = device.bluetoothClass?.majorDeviceClass
            val deviceMap: HashMap<String, Any?> = HashMap()
            deviceMap["name"] = deviceName
            deviceMap["address"] = deviceHardwareAddress
            deviceMap["isBle"] = false
            deviceMap["type"] = "bluetooth"
            deviceMap["majorClass"] = majorClass
            deviceMap["isLikelyPrinter"] = majorClass == 1536
            deviceMap["source"] = "bonded"
            list.add(deviceMap)
            Log.d(TAG, "deviceName $deviceName deviceHardwareAddress $deviceHardwareAddress")

            mChannel.invokeMethod("ScanResult", deviceMap)

//            currentActivity?.runOnUiThread { channel.invokeMethod("ScanResult", deviceMap) }
//            devicesSink?.success(deviceMap)
        }

        startClassicPrinterDiscovery(mChannel)
    }

    fun startClassicPrinterDiscovery(mChannel: MethodChannel) {
        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: enter")
        classicDiscoveryChannel = mChannel
        classicDiscoveryAddresses.clear()

        if (mBluetoothAdapter.isDiscovering) {
            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: cancel previous discovery")
            mBluetoothAdapter.cancelDiscovery()
        }

        if (classicDiscoveryReceiver == null) {
            classicDiscoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            @Suppress("DEPRECATION")
                            val device: BluetoothDevice? =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            val address = device?.address

                            if (address.isNullOrEmpty()) {
                                Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: ignore device with empty address")
                                return
                            }

                            if (!classicDiscoveryAddresses.add(address)) {
                                Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: ignore duplicate address=$address")
                                return
                            }

                            val deviceName = if (device.name == null) address else device.name
                            val majorClass = device.bluetoothClass?.majorDeviceClass
                            val deviceMap: HashMap<String, Any?> = HashMap()
                            deviceMap["name"] = deviceName
                            deviceMap["address"] = address
                            deviceMap["isBle"] = false
                            deviceMap["type"] = "bluetooth"
                            deviceMap["majorClass"] = majorClass
                            deviceMap["isLikelyPrinter"] = majorClass == 1536
                            deviceMap["source"] = "classic_discovery"

                            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: found name=$deviceName address=$address type=${device.type} bondState=${device.bondState} majorClass=$majorClass isLikelyPrinter=${majorClass == 1536}")
                            classicDiscoveryChannel?.invokeMethod("ScanResult", deviceMap)
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: discovery finished count=${classicDiscoveryAddresses.size}")
                            bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STOP_SCANNING, -1, -1)
                                ?.sendToTarget()
                        }
                    }
                }
            }

            val filter = IntentFilter()
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            appContext.registerReceiver(classicDiscoveryReceiver, filter)
        }

        val started = mBluetoothAdapter.startDiscovery()
        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.startClassicPrinterDiscovery: startDiscovery=$started")
        if (!started) {
            bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STOP_SCANNING, -1, -1)
                ?.sendToTarget()
        }
    }

    fun stopClassicPrinterDiscovery() {
        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.stopClassicPrinterDiscovery: enter")

        if (mBluetoothAdapter.isDiscovering) {
            mBluetoothAdapter.cancelDiscovery()
            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.stopClassicPrinterDiscovery: cancelDiscovery called")
        }

        classicDiscoveryReceiver?.let { receiver ->
            try {
                appContext.unregisterReceiver(receiver)
                Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.stopClassicPrinterDiscovery: receiver unregistered")
            } catch (e: Exception) {
                Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.stopClassicPrinterDiscovery: receiver unregister ignored ${e.message}")
            }
        }

        classicDiscoveryReceiver = null
        classicDiscoveryChannel = null
        classicDiscoveryAddresses.clear()
        bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STOP_SCANNING, -1, -1)
            ?.sendToTarget()
    }

    fun getBondedBluetoothPrinters(): ArrayList<HashMap<String, Any?>> {
        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.getBondedBluetoothPrinters: enter")

        val printers = ArrayList<HashMap<String, Any?>>()
        val pairedDevices: Set<BluetoothDevice>? = mBluetoothAdapter.bondedDevices

        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.getBondedBluetoothPrinters: bondedDevicesCount=${pairedDevices?.size ?: 0}")

        pairedDevices?.forEach { device ->
            val majorClass = device.bluetoothClass?.majorDeviceClass
            val isLikelyPrinter = majorClass == 1536
            val deviceName = if (device.name == null) device.address else device.name
            val deviceMap: HashMap<String, Any?> = HashMap()

            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.getBondedBluetoothPrinters: device name=$deviceName address=${device.address} type=${device.type} bondState=${device.bondState} majorClass=$majorClass isLikelyPrinter=$isLikelyPrinter")

            deviceMap["name"] = deviceName
            deviceMap["address"] = device.address
            deviceMap["isBle"] = false
            deviceMap["type"] = "bluetooth"
            deviceMap["majorClass"] = majorClass
            deviceMap["isLikelyPrinter"] = isLikelyPrinter
            deviceMap["source"] = "bonded"

            printers.add(deviceMap)
        }

        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.getBondedBluetoothPrinters: finalCount=${printers.size}")

        return printers
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Scan ble
    ////////////////////////////////////////////////////////////////////////////////////////////////
    fun scanBleDevice(mChannel: MethodChannel) {
        if (bleScanner == null) return
        devicesBle.clear()
        handler.removeCallbacksAndMessages(null)
        // Device scan callback.
        val leScanCallback = MyScanCallback()
        leScanCallback.init(mChannel)
        val list = ArrayList<HashMap<*, *>>()

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                bleScanner.stopScan(leScanCallback)
                bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STOP_SCANNING, -1, -1)
                    ?.sendToTarget()
                Log.d(TAG, "----- stop scanning ble ------- ")
                for (device in devicesBle) {
                    val deviceMap: HashMap<String?, String?> = HashMap()
                    deviceMap["name"] = device.name
                    deviceMap["address"] = device.address
                    list.add(deviceMap)
                }
            }, SCAN_PERIOD)
            Log.d(TAG, "----- start scanning ble ------ ")
            scanning = true
            bleScanner.startScan(leScanCallback)
            bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_START_SCANNING, -1, -1)
                ?.sendToTarget()
        } else {
            scanning = false
            bleScanner.stopScan(leScanCallback)
            bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STOP_SCANNING, -1, -1)
                ?.sendToTarget()
        }
    }

    fun cleanHandlerBtBle() {
        handler.removeCallbacksAndMessages(null)
    }

    inner class MyScanCallback : ScanCallback() {

        private var mmChannel: MethodChannel? = null
        fun init(channel: MethodChannel) {
            mmChannel = channel
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val deviceHardwareAddress = result.device?.address // MAC address
            val deviceName =
                if (result.device?.name == null) result.device?.address else result.device?.name

            if (!devicesBle.any { e -> e.address == deviceHardwareAddress }) {
                val deviceBT = LocalBluetoothDevice(
                    name = deviceName ?: "Unknown",
                    address = deviceHardwareAddress
                )
                val deviceMap: HashMap<String?, String?> = HashMap()
                deviceMap["name"] = deviceName
                deviceMap["address"] = deviceHardwareAddress
                if (result.device?.name != null)
                    mmChannel?.invokeMethod("ScanResult", deviceMap)
                devicesBle.add(deviceBT)
                Log.d(TAG, "deviceName ${result.device.name} deviceHardwareAddress ${result.device.address}")

            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Bluetooth control
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private fun bluetoothConnect(address: String?, result: Result) {
        bluetoothConnection?.connect(address!!, result)
    }

    fun bluetoothDisconnect() {
        bluetoothConnection?.stop()
        bluetoothConnection = null

        mHandlerAutoConnect.removeCallbacks(reconnect)
    }


    fun onStartConnection(context: Context, address: String?, result: Result, isBle: Boolean = false, autoConnect: Boolean = false) {
        if (bluetoothConnection == null)
            bluetoothConnection =
                if (isBle) BluetoothBleConnection(mContext = context, bluetoothHandler!!, autoConnect = autoConnect)
                else BluetoothConnection(context, bluetoothHandler!!)
        this.result = result
        reconnectBluetooth = bluetoothConnection is BluetoothConnection && autoConnect
        mConnectedDeviceAddress = address
        if ("" != address && bluetoothConnection!!.state == BluetoothConstants.STATE_NONE) {
//            Log.d(TAG, " ------------- mac Address BT: $address")
            bluetoothConnect(address, result)
        } else if (bluetoothConnection!!.state == BluetoothConstants.STATE_CONNECTED) {
            result.success(true)
            bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, bluetoothConnection!!.state, -1)?.sendToTarget()
        } else {
            result.success(false)
            bluetoothHandler?.obtainMessage(BluetoothConstants.MESSAGE_STATE_CHANGE, bluetoothConnection!!.state, -1)?.sendToTarget()
        }
    }

    /// permite reconectar el dispositivo
    private val reconnect = Runnable {
        bluetoothConnection?.stop()
        if (result != null)
            bluetoothConnect(mConnectedDeviceAddress, result!!)
    }

    fun autoConnectBt() {
        if (bluetoothConnection is BluetoothConnection && reconnectBluetooth) {
            mHandlerAutoConnect.removeCallbacks(reconnect)
            mHandlerAutoConnect.postDelayed(reconnect, (1000 + Math.random() * 4000).toLong())
        }
    }

    fun removeReconnectHandlers() {
        mHandlerAutoConnect.removeCallbacks(reconnect)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Comandos de envio por Bluetooth
    ////////////////////////////////////////////////////////////////////////////////////////////////
    fun sendData(data: String) {
        if (bluetoothConnection?.state == BluetoothConstants.STATE_CONNECTED) {
            bluetoothConnection?.write(data.toByteArray())
        }
    }

    fun sendDataByte(bytes: ByteArray?): Boolean {
        val currentState = bluetoothConnection?.state
        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.sendDataByte: state=$currentState")
        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.sendDataByte: bytesSize=${bytes?.size}")

        if (currentState == BluetoothConstants.STATE_CONNECTED) {
            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.sendDataByte: write before")
            bluetoothConnection?.write(bytes!!)
            Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.sendDataByte: write after")
            return true
        }

        Log.d("BUMO_PRINTER_PLUGIN", "BluetoothService.sendDataByte: return false because state is not connected")
        return false
    }

    @Suppress("unused")
    private fun setUpBluetooth() {

        if (!mBluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            currentActivity?.startActivityForResult(enableBtIntent, 0)
            while (true) {
                if (mBluetoothAdapter.isEnabled) break
            }
            return
        }
        return
    }

    fun setActivity(activity: Activity?) {
        this.currentActivity = activity
    }

    companion object {

        private var mInstance: BluetoothService? = null
        var bluetoothConnection: IBluetoothConnection? = null


        fun getInstance(mContext: Context, bluetoothHandler: Handler): BluetoothService {
            if (mInstance == null) {
                mInstance = BluetoothService(mContext, bluetoothHandler)
            }
            return mInstance!!
        }

        // Stops scanning after 4 seconds.
        private const val SCAN_PERIOD: Long = 4 * 1000


        const val TAG = "BluetoothPrinter"
    }
}
