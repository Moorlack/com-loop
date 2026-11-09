import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const vendorPrefix = '95986f2d-d705-4421-9890-2d32bff7';
const batteryUuid = '00002a19-0000-1000-8000-00805f9b34fb';
const serialUuid = '00002a25-0000-1000-8000-00805f9b34fb';
const firmwareUuid = '00002a28-0000-1000-8000-00805f9b34fb';

class DeviceState {
  String status = 'disconnected';
  String? mode;
  String? address;
  final values = <String, List<int>>{};
  bool get ready => status == 'ready';
  int? byte(String tail) => values['$vendorPrefix$tail']?.firstOrNull;
  int? get battery => values[batteryUuid]?.firstOrNull;
  int get batteryWarning => battery == null || battery! > 100
      ? 0
      : battery! < 5
      ? 2
      : battery! <= 20
      ? 1
      : 0;
  String text(String uuid) => utf8
      .decode(values[uuid] ?? [], allowMalformed: true)
      .replaceAll('\u0000', '');
  bool get canControl => ready && byte('0004') == 1 && byte('0007') == 0;
  bool get canVolume => canControl && [1, 2, 3, 5].contains(byte('0005'));
  void clear() {
    values.clear();
    status = 'disconnected';
    address = null;
    mode = null;
  }
}

class ComdexController extends ChangeNotifier {
  static const channel = MethodChannel('comdex/control');
  static const events = EventChannel('comdex/events');

  static Future<T?> invoke<T>(String method, [Object? arguments]) {
    return channel
        .invokeMethod<T>(method, arguments)
        .timeout(
          Duration(seconds: method == 'permissions' ? 60 : 35),
          onTimeout: () => throw PlatformException(
            code: 'android_timeout',
            message:
                'Android не ответил ($method). Если связь не восстановилась, '
                'завершите Bluetooth-сеанс в разделе «Статус» и подключитесь снова.',
          ),
        );
  }

  StreamSubscription<dynamic>? _subscription;
  final comdex = DeviceState();
  final cdrm = DeviceState();
  final devices = <String, Map<String, dynamic>>{};
  final logs = <String>[];
  Map<String, dynamic> preferences = {};
  Map<String, String> strings = {};
  Map<String, String> english = {};
  bool demo = false, scanning = false, busy = false;
  String? error;
  String locale = 'ru';
  String? focus;
  bool idleMuted = false;
  bool sessionActive = false;
  String get comdexTitle {
    final address = comdex.address ?? preferences['comdexAddress'];
    return preferences['remembered:comdex:$address'] as String? ?? 'COM-Loop';
  }

  List<Map<String, dynamic>> devicesFor(String kind) {
    final prefix = 'remembered:$kind:';
    final results = <String, Map<String, dynamic>>{};
    for (final entry in preferences.entries) {
      if (!entry.key.startsWith(prefix) || entry.value is! String) continue;
      final address = entry.key.substring(prefix.length);
      results[address] = {
        'name': entry.value,
        'address': address,
        'remembered': true,
      };
    }
    for (final entry in devices.entries) {
      if (entry.value['kind'] != kind) continue;
      results.putIfAbsent(entry.key, () => entry.value);
    }
    return results.values.toList();
  }

  bool get canStartRemoteStream =>
      cdrm.ready &&
      comdex.canControl &&
      [1, 3, 4, 5].contains(comdex.byte('0005'));
  bool get canChangeRemoteStream =>
      cdrm.ready && (cdrm.byte('1001') == 2 || canStartRemoteStream);
  bool get canBoost =>
      comdex.canControl &&
      comdex.byte('0005') != 2 &&
      comdex.byte('0202') != null;
  String tr(String key) => strings[key] ?? english[key] ?? key;
  String get profileLabel => switch (comdex.byte('0005')) {
    0 => locale == 'ru' ? 'Нет аудиосоединения' : 'No audio connection',
    1 || 5 => locale == 'ru' ? 'Обычный режим' : 'Idle',
    2 => tr('stream_phone_call'),
    3 => tr('streaming_music'),
    4 => tr('stream_microphone'),
    _ => '—',
  };
  Future<void> initialize() async {
    english = Map<String, String>.from(
      jsonDecode(await rootBundle.loadString('assets/text/en.json')) as Map,
    );
    try {
      preferences = Map<String, dynamic>.from(
        await invoke<Map<Object?, Object?>>('preferences') ?? {},
      );
      locale = preferences['locale'] as String? ?? 'ru';
      _subscription = events.receiveBroadcastStream().listen(
        (dynamic e) => accept(Map<String, dynamic>.from(e as Map)),
        onError: (Object e) {
          error = '$e';
          notifyListeners();
        },
      );
      await invoke<void>('snapshot');
    } on PlatformException catch (e) {
      error = e.message;
    }
    await loadLocale(locale == 'en' ? 'en' : 'ru');
  }

  Future<void> loadLocale(String value) async {
    locale = value;
    strings = Map<String, String>.from(
      jsonDecode(await rootBundle.loadString('assets/text/$value.json')) as Map,
    );
    notifyListeners();
  }

  void accept(Map<String, dynamic> event) {
    switch (event['type']) {
      case 'device':
        devices[event['address'] as String] = event;
      case 'scan':
        scanning = event['active'] == true;
      case 'idleMute':
        if (!demo) idleMuted = event['value'] == true;
      case 'session':
        sessionActive = event['active'] == true;
        if (!sessionActive && !demo) {
          comdex.clear();
          cdrm.clear();
        }
      case 'log':
        log(event['message'] as String);
      case 'connection':
        if (demo) return;
        final d = event['kind'] == 'cdrm' ? cdrm : comdex;
        if (event['status'] == 'disconnected' ||
            event['status'] == 'connecting') {
          d.values.clear();
          if (event['kind'] != 'cdrm') {
            idleMuted = false;
            focus = null;
          }
        }
        d.status = event['status'] as String;
        d.address = event['address'] as String?;
        d.mode = event['mode'] as String?;
        if (d.ready && d.address != null && event['name'] is String) {
          final kind = event['kind'] as String;
          preferences['${kind}Address'] = d.address;
          preferences['remembered:$kind:${d.address}'] = event['name'];
        }
      case 'value':
        if (demo) return;
        final d = event['kind'] == 'cdrm' ? cdrm : comdex;
        d.values[event['uuid'] as String] = List<int>.from(
          event['bytes'] as List,
        );
    }
    notifyListeners();
  }

  void log(String message) {
    logs.add('${DateTime.now().toIso8601String()}  $message');
    if (logs.length > 500) logs.removeAt(0);
  }

  Future<bool> perform(Future<void> Function() action) async {
    if (busy) return false;
    busy = true;
    error = null;
    notifyListeners();
    try {
      await action();
      return true;
    } catch (e) {
      error = e is PlatformException ? e.message ?? e.code : '$e';
      log('ERROR: $error');
      return false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> save(String key, Object value) async {
    preferences[key] = value.toString();
    await invoke<void>('preferences', {
      'values': {key: value.toString()},
    });
    notifyListeners();
  }

  Future<void> start() async {
    final granted = await invoke<bool>('permissions') ?? false;
    if (!granted) {
      throw StateError('Разрешите доступ к Bluetooth в настройках приложения.');
    }
    await invoke<void>('start');
  }

  Future<void> scan({String kind = 'comdex'}) async {
    if (demo) exitDemo();
    devices.clear();
    await start();
    await invoke<void>('scan', {'kind': kind});
  }

  Future<void> connect(String address, {String kind = 'comdex'}) async {
    if (demo) exitDemo();
    await start();
    final d = kind == 'cdrm' ? cdrm : comdex;
    d.clear();
    d.status = 'connecting';
    d.address = address;
    notifyListeners();
    try {
      await invoke<void>('connect', {'address': address, 'kind': kind});
    } catch (_) {
      d.clear();
      rethrow;
    }
  }

  Future<void> disconnect({String kind = 'comdex', bool forget = false}) async {
    if (demo) {
      exitDemo();
      return;
    }
    final device = kind == 'cdrm' ? cdrm : comdex;
    final address = device.address ?? preferences['${kind}Address'];
    await invoke<void>(forget ? 'forget' : 'disconnect', {'kind': kind});
    device.clear();
    if (forget) {
      preferences.remove('${kind}Address');
      preferences.remove('remembered:$kind:$address');
    }
    notifyListeners();
  }

  Future<void> command(
    String name, {
    int? value,
    String kind = 'comdex',
  }) async {
    final d = kind == 'cdrm' ? cdrm : comdex;
    if (!d.ready || (kind == 'comdex' && !d.canControl)) {
      throw StateError('Устройство не готово к управлению');
    }
    if (kind == 'cdrm' &&
        name == 'stream' &&
        value == 2 &&
        !canStartRemoteStream) {
      throw StateError(
        comdex.byte('0005') == 2
            ? 'Remote Mic нельзя запускать во время телефонного разговора'
            : 'Сначала подключите COM-DEX и проверьте аудиосоединение',
      );
    }
    if (kind == 'comdex' && name == 'boost' && comdex.byte('0005') == 2) {
      throw StateError(
        'Усиление мультимедиа недоступно во время телефонного разговора',
      );
    }
    if (demo) {
      final tail = (kind == 'cdrm'
          ? {'stream': '1001', 'gain': '1002', 'led': '1004'}
          : {'microphone': '0003', 'boost': '0202', 'speech': '0203'})[name];
      if (tail != null && value != null) {
        d.values['$vendorPrefix$tail'] = [value];
      }
      if (['omni', 'front', 'rear', 'left', 'right'].contains(name)) {
        focus = name;
      }
      if (name == 'mute') idleMuted = true;
      if (name == 'up') idleMuted = false;
      log('DEMO: $kind $name ${value ?? ''}');
      notifyListeners();
      return;
    }
    await invoke<void>('command', {
      'kind': kind,
      'name': name,
      'value': ?value,
    });
    if (['omni', 'front', 'rear', 'left', 'right'].contains(name)) focus = name;
    if (kind == 'comdex' &&
        name == 'mute' &&
        [1, 5].contains(comdex.byte('0005'))) {
      idleMuted = true;
    }
    if (kind == 'comdex' && name == 'up') idleMuted = false;
    log(
      'Команда $name: операция завершена Android; результат в слуховых аппаратах не считывается',
    );
    notifyListeners();
  }

  Future<void> surroundings() async {
    await command('microphone', value: comdex.byte('0003') == 0 ? 1 : 0);
  }

  void enterDemo() {
    if (sessionActive ||
        comdex.status != 'disconnected' ||
        cdrm.status != 'disconnected' ||
        scanning) {
      error = 'Сначала отключите устройства и остановите поиск';
      notifyListeners();
      return;
    }
    demo = true;
    error = null;
    comdex.status = cdrm.status = 'ready';
    comdex.values.addAll({
      '$vendorPrefix${'0004'}': [1],
      '$vendorPrefix${'0005'}': [1],
      '$vendorPrefix${'0007'}': [0],
      '$vendorPrefix${'0003'}': [1],
      '$vendorPrefix${'0202'}': [0],
      '$vendorPrefix${'0203'}': [0],
      batteryUuid: [80],
    });
    cdrm.values.addAll({
      '$vendorPrefix${'1001'}': [1],
      '$vendorPrefix${'1002'}': [1],
      '$vendorPrefix${'1004'}': [50],
      batteryUuid: [65],
    });
    notifyListeners();
  }

  void exitDemo() {
    demo = false;
    comdex.clear();
    cdrm.clear();
    focus = null;
    idleMuted = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }
}
