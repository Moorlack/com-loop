@file:Suppress("DEPRECATION", "MissingPermission")
package org.comdex.community.comdex

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.media.AudioManager
import android.os.*
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.UUID
import java.util.ArrayDeque

/** GATT state is confined to the main looper; one outstanding operation per link. */
class BleService : Service() {
    companion object {
        const val EXTRA_STARTED = "org.comdex.community.comdex.STARTED"
        var instance: BleService? = null
        var sink: EventChannel.EventSink? = null
        val main = Handler(Looper.getMainLooper())
        fun emit(value: Map<String, Any?>) { main.post { sink?.success(value) } }
        fun log(message: String) { emit(mapOf("type" to "log", "message" to message)) }
    }
    private val adapter get() = getSystemService(BluetoothManager::class.java)?.adapter
    private val links = mutableMapOf<String, Link>()
    private var scanning = false
    private var scanKind = "comdex"
    private val advertised = mutableSetOf<Pair<String, String>>()

    private fun serviceUuid(kind: String) =
        Protocol.uuid(if (kind == "cdrm") "1000" else "0000")

    private fun knownDevice(kind: String, device: BluetoothDevice): Boolean =
        (kind to device.address) in advertised ||
            getSharedPreferences("settings", 0).contains("remembered:$kind:${device.address}") ||
            (device.bondState == BluetoothDevice.BOND_BONDED && DeviceRules.matchesName(kind, device.name))

    private val scanEnd = Runnable { stopScan() }
    private val poll = object : Runnable {
        override fun run() { links.values.filter { it.ready }.forEach { it.refresh() }; main.postDelayed(this, 180000) }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                stopScan(); links.values.toList().forEach { it.close("Bluetooth выключен") }
            }
        }
    }
    override fun onCreate() {
        super.onCreate(); instance = this
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("connection", "COM-DEX connection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "connection") else Notification.Builder(this)
        startForeground(7, builder.setContentTitle("COM-Loop").setContentText("Bluetooth: управление устройством").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(open).setOngoing(true).build())
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)); main.postDelayed(poll, 180000); emit(mapOf("type" to "session", "active" to true))
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        intent?.getParcelableExtra<ResultReceiver>(EXTRA_STARTED)?.send(0, null)
        intent?.getStringExtra("command")?.let { name -> command("comdex", name, null, object : MethodChannel.Result {
            override fun success(value: Any?) {}
            override fun error(code: String, message: String?, details: Any?) { log("Widget: $message") }
            override fun notImplemented() {}
        }) }
        return START_NOT_STICKY
    }
    fun snapshot() {
        emit(mapOf("type" to "session", "active" to true))
        links.values.toList().forEach { link ->
            link.state()
            link.values.toMap().forEach { (uuid,data) -> emit(mapOf("type" to "value", "kind" to link.kind, "uuid" to uuid, "bytes" to data.map { it.toInt() and 255 })) }
        }
        emit(mapOf("type" to "idleMute", "value" to (links["comdex"]?.idleMuted == true)))
    }
    fun widgetCommand(kind: String, name: String) {
        val link = links[kind]
        val effective = if (name == "room") "microphone" else name
        val number = when(name) {
            "stream" -> if(link?.byte("1001") == 2) 1 else 2
            "gain" -> ((link?.byte("1002") ?: 0) + 1) % 3
            "microphone", "room" -> if (links["comdex"]?.byte("0003") == 0) 1 else 0
            else -> null
        }
        command(kind,effective,number,object : MethodChannel.Result {
            override fun success(value: Any?) { ControlWidget.refresh(this@BleService) }
            override fun error(code: String, message: String?, details: Any?) { android.widget.Toast.makeText(this@BleService,message,android.widget.Toast.LENGTH_LONG).show() }
            override fun notImplemented() {}
        })
    }
    fun widgetConnected() = links["comdex"]?.ready == true
    fun widgetRoomOff() = links["comdex"]?.byte("0003") == 0
    fun widgetBattery(kind: String): Int? = links[kind]?.values?.get("00002a19-0000-1000-8000-00805f9b34fb")?.firstOrNull()?.toInt()?.and(255)
    fun handle(method: String, args: Map<*,*>, result: MethodChannel.Result) {
        val kind = args["kind"] as? String ?: "comdex"
        when (method) {
            "scan" -> {
                check(adapter?.isEnabled == true) { "Включите Bluetooth" }; check(!scanning) { "Поиск уже запущен" }
                require(kind in listOf("comdex", "cdrm"))
                scanKind = kind
                advertised.removeAll { it.first == kind }
                adapter?.bondedDevices?.filter { knownDevice(kind, it) }?.forEach { device ->
                    emit(mapOf("type" to "device", "kind" to kind, "name" to device.name,
                        "address" to device.address, "bonded" to true))
                }
                val scanner = adapter?.bluetoothLeScanner ?: error("BLE недоступен")
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
                scanning = true; emit(mapOf("type" to "scan", "active" to true)); main.postDelayed(scanEnd, 15000)
                result.success(null)
            }
            "stopScan" -> { stopScan(); result.success(null) }
            "connect" -> {
                require(kind in listOf("comdex", "cdrm")); check(adapter?.isEnabled == true) { "Включите Bluetooth" }
                val address = args["address"] as? String ?: error("Нет адреса"); require(BluetoothAdapter.checkBluetoothAddress(address))
                val device = adapter!!.getRemoteDevice(address)
                check(knownDevice(kind, device)) { "Выберите совместимое устройство из результатов поиска" }
                stopScan()
                links.remove(kind)?.close("Новое подключение")
                val bonded = device.bondState == BluetoothDevice.BOND_BONDED
                val classicAvailable = kind == "comdex" && bonded && device.type != BluetoothDevice.DEVICE_TYPE_LE
                val savedMode = getSharedPreferences("settings", 0).getString("transport:$address", null)
                val mode = if (classicAvailable &&
                    (savedMode == "green" || device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC)
                ) "green" else "blue"
                val alternate = if (classicAvailable && device.type != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                    if (mode == "blue") "green" else "blue"
                } else null
                val link = Link(kind, address, mode = mode, alternate = alternate)
                links[kind] = link
                try { link.connect(); result.success(null) } catch(e: Exception) { links.remove(kind); link.close("Не удалось подключиться"); throw e }
            }
            "disconnect", "forget" -> {
                val link = links.remove(kind)
                link?.close("Отключено пользователем")
                if (method == "forget") {
                    val prefs = getSharedPreferences("settings", 0)
                    val address = link?.address ?: prefs.getString("${kind}Address", null)
                    prefs.edit().remove("${kind}Address").apply {
                        if (address != null) remove("remembered:$kind:$address")
                    }.apply()
                }
                result.success(null); if (links.isEmpty()) stopSelf()
            }
            "refresh" -> { links[kind]?.refresh(); result.success(null) }
            "command" -> command(kind, args["name"] as? String ?: "", (args["value"] as? Number)?.toInt(), result)
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            main.post {
                if (!scanning) return@post
                val kind = scanKind
                val device = result.device
                val hasService = result.scanRecord?.serviceUuids?.any {
                    it.toString() == serviceUuid(kind)
                } == true
                if (hasService) advertised.add(kind to device.address)
                if (!knownDevice(kind, device)) return@post
                emit(mapOf("type" to "device", "kind" to kind,
                    "name" to (result.scanRecord?.deviceName ?: device.name ?: if (kind == "cdrm") "Remote Mic" else "COM-DEX"),
                    "address" to device.address, "rssi" to result.rssi,
                    "bonded" to (device.bondState == BluetoothDevice.BOND_BONDED)))
            }
        }
        override fun onScanFailed(code: Int) { main.post { stopScan(); log("Ошибка BLE поиска: $code") } }
    }
    private fun stopScan() {
        main.removeCallbacks(scanEnd)
        if (scanning) { try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {} }
        scanning = false; emit(mapOf("type" to "scan", "active" to false))
    }
    private fun command(kind: String, name: String, number: Int?, result: MethodChannel.Result) {
        try {
            val link = links[kind] ?: error("Устройство не подключено"); check(link.ready) { "Устройство ещё не готово" }
            if (kind == "comdex") check(link.byte("0004") == 1 && link.byte("0007") == 0) { "Закройте шейную петлю и отключите зарядку" }
            val profile = link.byte("0005")
            val parent = links["comdex"]
            DeviceRules.commandError(kind,name,number,parent?.byte("0005"),parent?.ready == true && parent.byte("0004") == 1 && parent.byte("0007") == 0)?.let { error(it) }
            if (name in listOf("up", "down", "mute") && kind == "comdex" && profile in listOf(2,3)) {
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                val stream = if (profile == 3) AudioManager.STREAM_MUSIC else 6
                if (name == "mute") audio.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                else audio.adjustStreamVolume(stream, if (name == "up") 1 else -1, AudioManager.FLAG_SHOW_UI)
                result.success(null); return
            }
            if (name in listOf("up", "down", "mute") && kind == "comdex") check(profile in listOf(1,5)) { "Громкость недоступна в этом профиле" }
            fun one(tail: String): List<Pair<String,ByteArray>> { require(number in 0..1); return listOf(Protocol.uuid(tail) to byteArrayOf(number!!.toByte())) }
            val writes: List<Pair<String,ByteArray>> = if (kind == "cdrm") when (name) {
                "stream" -> { require(number in 1..2); listOf(Protocol.uuid("1001") to byteArrayOf(number!!.toByte())) }
                "gain" -> { require(number in 0..2); listOf(Protocol.uuid("1002") to byteArrayOf(number!!.toByte())) }
                "led" -> { require(number in listOf(10,50)); listOf(Protocol.uuid("1004") to byteArrayOf(number!!.toByte())) }
                else -> error("Неизвестная команда Remote Mic")
            } else when(name) {
                "up" -> listOf(Protocol.uuid("0100") to Protocol.VOL_UP_CMD)
                "down" -> listOf(Protocol.uuid("0100") to Protocol.VOL_DW_CMD)
                "mute" -> {
                    check(SystemClock.elapsedRealtime() - link.lastMute > 5000) { "Повтор mute через 5 секунд" }
                    link.lastMute = SystemClock.elapsedRealtime(); List(22) { Protocol.uuid("0100") to Protocol.VOL_DW_CMD }
                }
                "next" -> listOf(Protocol.uuid("0100") to Protocol.NEXT_PROGRAM)
                "zen" -> listOf(Protocol.uuid("0100") to Protocol.CHANGE_PROGRAM_MODE)
                "microphone" -> one("0003")
                "boost" -> { require(number in listOf(0,2)); listOf(Protocol.uuid("0202") to byteArrayOf(number!!.toByte())) }
                "speech" -> one("0203")
                "omni", "front", "rear", "left", "right" -> {
                    val focus = when(name) { "front" -> Protocol.FREE_FOCUS_FRONT; "rear" -> Protocol.FREE_FOCUS_REAR; "left" -> Protocol.FREE_FOCUS_LEFT; "right" -> Protocol.FREE_FOCUS_RIGHT; else -> Protocol.FREE_FOCUS_OMNI }
                    listOf(Protocol.uuid("0100") to Protocol.ROOM_ON, Protocol.uuid("0100") to focus)
                }
                else -> error("Неизвестная команда")
            }; link.writeBatch(writes, object : MethodChannel.Result {
                override fun success(value: Any?) {
                    if(kind == "comdex" && profile in listOf(1,5) && name in listOf("mute","up")) {
                        link.idleMuted = name == "mute"; emit(mapOf("type" to "idleMute", "value" to link.idleMuted))
                    }; result.success(value)
                }
                override fun error(code: String, message: String?, details: Any?) { result.error(code,message,details) }
                override fun notImplemented() { result.notImplemented() }
            })
        } catch(e: Exception) { result.error("command", e.message, null) }
    }
    private data class Op(val type: String, val uuid: String, val bytes: ByteArray? = null, val done: ((Boolean)->Unit)? = null)
    private inner class Link(val kind: String, val address: String, var attempt: Int = 0, val mode: String = "blue", private val alternate: String? = null) {
        var gatt: BluetoothGatt? = null
        var ready = false
        var closed = false
        var lastMute = -10000L
        var idleMuted = false
        val values = mutableMapOf<String,ByteArray>()
        private val queue = ArrayDeque<Op>()
        private var active: Op? = null
        private var initialized = false
        private val connectionTimeout = Runnable { initialFailure("Таймаут подключения") }
        private val opTimeout = Runnable { close("Таймаут GATT; команда не повторялась") }
        private val writeBatchTimeout = Runnable { close("Таймаут серии команд GATT") }
        fun byte(tail: String) = values[Protocol.uuid(tail)]?.firstOrNull()?.toInt()?.and(255)
        fun connect() {
            state("connecting"); log("$kind: GATT transport=$mode")
            gatt = adapter!!.getRemoteDevice(address).connectGatt(this@BleService, false, callback, DeviceRules.transport(mode))
            main.postDelayed(connectionTimeout, if (alternate != null) 15000 else 30000)
        }
        fun state(status: String = if (ready) "ready" else if (closed) "disconnected" else "initializing") {
            val name = getSharedPreferences("settings", 0).getString("remembered:$kind:$address", null)
            emit(mapOf("type" to "connection", "kind" to kind, "status" to status,
                "address" to address, "mode" to mode, "name" to name))
            ControlWidget.refresh(this@BleService)
        }
        fun value(uuid: String, data: ByteArray) {
            values[uuid] = data.copyOf(); emit(mapOf("type" to "value", "kind" to kind, "uuid" to uuid, "bytes" to data.map { it.toInt() and 255 }))
            if (kind == "comdex" && (uuid == Protocol.uuid("0003") || uuid == "00002a19-0000-1000-8000-00805f9b34fb")) {
                ControlWidget.refresh(this@BleService)
            }
            // The companion address is reported by COM-DEX itself, not guessed from advertising.
            if(kind == "comdex" && uuid == Protocol.uuid("0201")) {
                if(data.size == 6 && data.any { it.toInt() != 0 }) {
                    val mac = data.joinToString(":") { "%02X".format(it.toInt() and 255) }
                    main.postDelayed({ if(!closed && links["cdrm"]?.address != mac) { links.remove("cdrm")?.close("Другой Remote Mic"); Link("cdrm",mac).also { links["cdrm"]=it; it.connect() } } },2000)
                } else if(data.isEmpty() || (data.size == 6 && data.all { it.toInt() == 0 })) links.remove("cdrm")?.close("Remote Mic отключён от COM-DEX")
            }
        }
        private fun initialFailure(reason: String) {
            val fallback = alternate
            if (ready || closed || fallback == null) {
                close(reason)
                return
            }
            close(reason)
            if (links[kind] !== this || adapter?.isEnabled != true) return
            val next = Link(kind, address, mode = fallback)
            links[kind] = next
            try {
                next.connect()
            } catch (e: Exception) {
                next.close(e.message ?: "Ошибка подключения")
            }
        }

        private fun characteristic(uuid: String): BluetoothGattCharacteristic? {
            val id = UUID.fromString(uuid)
            val deviceInformation = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
            if (uuid.startsWith(Protocol.uuid(""))) {
                // Some COM-DEX firmware exposes vendor characteristics under Device Information.
                return gatt?.getService(UUID.fromString(serviceUuid(kind)))?.getCharacteristic(id)
                    ?: gatt?.getService(deviceInformation)?.getCharacteristic(id)
            }
            val service = when {
                uuid.startsWith("00002a19") -> UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
                else -> deviceInformation
            }
            return gatt?.getService(service)?.getCharacteristic(id)
        }

        private fun initialize() {
            val base = if (kind == "comdex") "0005" else "1001"
            if (characteristic(Protocol.uuid(base)) == null) {
                initialFailure("Устройство не поддерживает сервис $kind")
                return
            }
            val notify = if (kind == "comdex") listOf("0001","0003","0004","0005","0007","0202","0200","0201") else listOf("1001","1002")
            notify.forEach { tail -> characteristic(Protocol.uuid(tail))?.let { c -> if (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) queue.add(Op("notify", c.uuid.toString())) } }
            if (kind == "cdrm") characteristic("00002a19-0000-1000-8000-00805f9b34fb")?.let { if(it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) queue.add(Op("notify", it.uuid.toString())) }
            val read = if (kind == "comdex") listOf("0003","0005","0004","0007","0202","0203","0201","0200") else listOf("1001","1002","1003","1004")
            (read.map { Protocol.uuid(it) } + listOf("00002a25-0000-1000-8000-00805f9b34fb","00002a28-0000-1000-8000-00805f9b34fb","00002a19-0000-1000-8000-00805f9b34fb")).forEach { uuid -> characteristic(uuid)?.let { if(it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) queue.add(Op("read",uuid)) } }
            initialized = true; next()
        }
        fun refresh() {
            if (!ready || active != null || queue.isNotEmpty()) return
            val tails = if (kind == "comdex") listOf("0005","0004","0007") else listOf("1001","1002","1003")
            (tails.map { Protocol.uuid(it) } + "00002a19-0000-1000-8000-00805f9b34fb").forEach { uuid -> characteristic(uuid)?.let { if(it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) queue.add(Op("read",uuid)) } }; next()
        }
        fun writeBatch(writes: List<Pair<String,ByteArray>>, result: MethodChannel.Result) {
            check(active == null && queue.isEmpty()) { "Предыдущая операция ещё выполняется" }
            writes.forEach { (uuid,_) -> val c = characteristic(uuid) ?: error("Функция отсутствует в прошивке"); check(c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) { "Нет записи в характеристику" } }
            var remaining = writes.size; var responded = false
            writes.forEach { (uuid,data) -> queue.add(Op("write",uuid,data) { ok -> if (!responded) {
                if (!ok) { responded = true; result.error("gatt", "Запись не подтверждена; проверьте состояние устройства", null) }
                else if (--remaining == 0) {
                    responded = true
                    main.removeCallbacks(writeBatchTimeout)
                    result.success(null)
                }
            } }) }
            main.postDelayed(writeBatchTimeout, 25000)
            next()
        }
        private fun next() {
            if (closed || active != null) return
            if (queue.isEmpty()) {
                if (initialized && !ready) {
                    val required = if(kind == "comdex") listOf("0004","0005","0007") else listOf("1001")
                    if(required.any { byte(it) == null }) { close("Не получен обязательный статус устройства"); return }
                    ready = true; attempt = 0; main.removeCallbacks(connectionTimeout)
                    val prefs = getSharedPreferences("settings", 0)
                    val name = runCatching { gatt?.device?.name }.getOrNull()
                        ?: prefs.getString("remembered:$kind:$address", null)
                        ?: if (kind == "comdex") "COM-DEX" else "Remote Mic"
                    prefs.edit().putString("${kind}Address", address)
                        .putString("remembered:$kind:$address", name).apply()
                    if(kind == "comdex") getSharedPreferences("settings",0).edit().putString("transport:$address",mode).apply()
                    state()
                }; return
            }
            val op = queue.removeFirst(); active = op; val c = characteristic(op.uuid) ?: run { complete(false); return }; val g = gatt ?: return
            val started = when(op.type) {
                "read" -> g.readCharacteristic(c)
                "write" -> { c.writeType = if(c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; c.value = op.bytes; g.writeCharacteristic(c) }
                "notify" -> {
                    val d = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (d == null || !g.setCharacteristicNotification(c,true)) false else { d.value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE; g.writeDescriptor(d) }
                }
                else -> false
            }; if (started) main.postDelayed(opTimeout, 10000) else complete(false)
        }
        private fun complete(ok: Boolean) {
            main.removeCallbacks(opTimeout); val op = active ?: return; active = null
            if (!ok) { op.done?.invoke(false); close("Ошибка GATT ${op.type} ${op.uuid}"); return }
            op.done?.invoke(true); main.postDelayed({ next() }, 60)
        }
        fun close(reason: String, retry: Boolean = false) {
            if(closed) return
            closed = true; ready = false; initialized = false; main.removeCallbacks(opTimeout); main.removeCallbacks(connectionTimeout)
            main.removeCallbacks(writeBatchTimeout)
            active?.done?.invoke(false); active = null; queue.forEach { it.done?.invoke(false) }; queue.clear()
            try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}; gatt = null; values.clear(); state("disconnected"); log("$kind: $reason")
            if(retry && attempt < 3) {
                val delay = 2000L shl attempt
                main.postDelayed({
                    if(instance === this@BleService && links[kind] === this && closed && adapter?.isEnabled == true) {
                        log("$kind: повтор подключения ${attempt+1}/3, команды не повторяются")
                        Link(kind,address,attempt+1,mode).also { next -> links[kind]=next; try { next.connect() } catch(e: Exception) { next.close(e.message ?: "Ошибка подключения") } }
                    }
                },delay)
            }
        }
        private fun current(g: BluetoothGatt) = !closed && g === gatt
        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) { main.post {
                if (!current(g)) return@post
                if(status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) { if (ready || attempt > 0) close("Соединение закрыто: $status", retry = true) else initialFailure("Соединение закрыто: $status") }
                else if(newState == BluetoothProfile.STATE_CONNECTED) { state("initializing"); if(!g.discoverServices()) initialFailure("Не удалось запросить сервисы") }
            } }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { main.post { if(current(g)) { if(status == 0) initialize() else initialFailure("Сервисы: $status") } } }
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                val data = c.value?.copyOf() ?: byteArrayOf(); main.post { if(current(g) && active?.type == "read" && active?.uuid == c.uuid.toString()) { if(status == 0) value(c.uuid.toString(),data); complete(status == 0) } }
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { val data = c.value?.copyOf() ?: byteArrayOf(); main.post { if(current(g)) value(c.uuid.toString(),data) } }
            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { main.post { if(current(g) && active?.type == "write" && active?.uuid == c.uuid.toString()) {
                if(status == 0) { log("$kind: GATT write completed ${c.uuid}; type=${c.writeType} (2=response, 1=no-response)"); if(c.uuid.toString() != Protocol.uuid("0100")) active?.bytes?.let { value(c.uuid.toString(),it) } }; complete(status == 0)
            } } }
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { main.post { if(current(g) && active?.type == "notify" && active?.uuid == d.characteristic.uuid.toString()) complete(status == 0) } }
        }
    }
    override fun onDestroy() {
        stopScan(); main.removeCallbacks(poll); links.values.toList().forEach { it.close("Сервис остановлен") }; links.clear(); unregisterReceiver(receiver); instance = null; emit(mapOf("type" to "session", "active" to false)); super.onDestroy()
    }
}
