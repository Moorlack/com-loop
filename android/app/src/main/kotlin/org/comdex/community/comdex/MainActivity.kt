package org.comdex.community.comdex

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pending: MethodChannel.Result? = null
    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        EventChannel(engine.dartExecutor.binaryMessenger, "comdex/events").setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(args: Any?, sink: EventChannel.EventSink) { BleService.sink = sink; BleService.instance?.snapshot() }
            override fun onCancel(args: Any?) { BleService.sink = null }
        })
        MethodChannel(engine.dartExecutor.binaryMessenger, "comdex/control").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "permissions" -> {
                        check(pending == null) { "Permission request pending" }
                        val wanted = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isEmpty()) result.success(true) else { pending = result; requestPermissions(missing.toTypedArray(), 42) }
                    }
                    "preferences" -> {
                        val prefs = getSharedPreferences("settings", 0)
                        call.argument<Map<String, Any>>("values")?.let { values -> val edit = prefs.edit(); values.forEach { (k,v) -> edit.putString(k, v.toString()) }; edit.apply() }
                        // Import the last successfully connected device from earlier builds.
                        for (kind in listOf("comdex", "cdrm")) {
                            val address = prefs.getString("${kind}Address", null) ?: continue
                            val key = "remembered:$kind:$address"
                            if (prefs.contains(key) || !BluetoothAdapter.checkBluetoothAddress(address)) continue
                            val name = runCatching {
                                getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.name
                            }.getOrNull()
                            if (DeviceRules.matchesName(kind, name)) prefs.edit().putString(key, name).apply()
                        }
                        result.success(prefs.all)
                    }
                    "snapshot" -> { BleService.instance?.snapshot() ?: BleService.emit(mapOf("type" to "session", "active" to false)); result.success(null) }
                    "stop" -> { BleService.instance?.stopSelf(); result.success(null) }
                    "start" -> {
                        val started = object : ResultReceiver(BleService.main) {
                            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                result.success(null)
                            }
                        }
                        val intent = Intent(this, BleService::class.java)
                            .putExtra(BleService.EXTRA_STARTED, started)
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                    }
                    "scan", "stopScan", "connect", "disconnect", "command", "refresh", "forget" -> {
                        val service = BleService.instance ?: error("Start Bluetooth session first")
                        service.handle(call.method, call.arguments as? Map<*, *> ?: emptyMap<Any,Any>(), result)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) { result.error("android", e.message, null) }
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 42) { pending?.success(grants.isNotEmpty() && grants.all { it == PackageManager.PERMISSION_GRANTED }); pending = null }
    }
    override fun onDestroy() { pending?.error("closed", "Activity closed", null); pending = null; super.onDestroy() }
}
