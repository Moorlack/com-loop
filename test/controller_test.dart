import 'package:comdex/src/controller.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ComdexController c;
  final calls = <MethodCall>[];
  setUp(() {
    c = ComdexController();
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(ComdexController.channel, (call) async {
          calls.add(call);
          return null;
        });
  });
  tearDown(() => c.dispose());

  testWidgets('A missing Android reply releases the busy state', (
    tester,
  ) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          ComdexController.channel,
          (_) => Future<Object?>.delayed(const Duration(seconds: 36)),
        );
    final action = c.perform(() async {
      await ComdexController.invoke<void>('snapshot');
    });
    expect(c.busy, isTrue);
    await tester.pump(const Duration(seconds: 35));
    expect(await action, isFalse);
    expect(c.busy, isFalse);
    expect(c.error, contains('Android не ответил'));
    await tester.pump(const Duration(seconds: 1));
    expect(c.busy, isFalse);
  });
  void state(String tail, int value) => c.accept({
    'type': 'value',
    'kind': 'comdex',
    'uuid': '$vendorPrefix$tail',
    'bytes': [value],
  });
  void ready() {
    c.accept({
      'type': 'connection',
      'kind': 'comdex',
      'status': 'ready',
      'address': 'AA:BB:CC:DD:EE:FF',
    });
    state('0004', 1);
    state('0007', 0);
    state('0005', 1);
  }

  test(
    'Unknown status, open loop, charging and disconnect disable controls',
    () {
      expect(c.comdex.canControl, isFalse);
      ready();
      expect(c.comdex.canControl, isTrue);
      state('0004', 0);
      expect(c.comdex.canControl, isFalse);
      state('0004', 1);
      state('0007', 1);
      expect(c.comdex.canControl, isFalse);
      c.accept({
        'type': 'connection',
        'kind': 'comdex',
        'status': 'disconnected',
      });
      expect(c.comdex.values, isEmpty);
      expect(c.comdex.canControl, isFalse);
    },
  );
  test('Disconnected commands never reach platform', () async {
    await expectLater(c.command('up'), throwsStateError);
    expect(calls, isEmpty);
  });
  test('Demo never requests Bluetooth or sends commands', () async {
    c.enterDemo();
    await c.command('up');
    await c.command('boost', value: 2);
    await c.command('stream', kind: 'cdrm', value: 2);
    expect(calls, isEmpty);
    expect(c.comdex.byte('0202'), 2);
    expect(c.cdrm.byte('1001'), 2);
    c.exitDemo();
    expect(c.comdex.canControl, isFalse);
    expect(c.cdrm.values, isEmpty);
  });
  test('Cannot enter demo while a real device is active', () {
    ready();
    c.enterDemo();
    expect(c.demo, isFalse);
    expect(c.error, isNotNull);
  });
  test('Pending reconnect session blocks demo until explicitly stopped', () {
    c.accept({'type': 'session', 'active': true});
    c.enterDemo();
    expect(c.demo, isFalse);
    c.accept({'type': 'session', 'active': false});
    c.enterDemo();
    expect(c.demo, isTrue);
  });
  test(
    'Platform failure is surfaced and does not synthesize device status',
    () async {
      ready();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(ComdexController.channel, (_) async {
            throw PlatformException(code: 'gatt', message: 'timeout');
          });
      expect(await c.perform(() => c.command('boost', value: 2)), isFalse);
      expect(c.error, 'timeout');
      expect(c.comdex.byte('0202'), isNull);
      expect(c.busy, isFalse);
    },
  );
  test('Wire values preserve non-boolean Remote Mic and boost enums', () async {
    ready();
    c.cdrm.status = 'ready';
    await c.command('boost', value: 2);
    await c.command('stream', kind: 'cdrm', value: 2);
    await c.command('led', kind: 'cdrm', value: 10);
    expect(calls.map((e) => (e.arguments as Map)['value']), [2, 2, 10]);
  });
  test('Room Off controls microphones in idle and during streaming', () async {
    ready();
    await c.surroundings();
    expect((calls.single.arguments as Map)['name'], 'microphone');
    expect((calls.single.arguments as Map)['value'], 0);
    state('0005', 3);
    state('0003', 1);
    await c.surroundings();
    expect((calls.last.arguments as Map)['name'], 'microphone');
    expect((calls.last.arguments as Map)['value'], 0);
    state('0003', 0);
    await c.surroundings();
    expect((calls.last.arguments as Map)['name'], 'microphone');
    expect((calls.last.arguments as Map)['value'], 1);
  });
  test('Saved devices appear before a scan and keep their numbered title', () {
    c.preferences = {
      'comdexAddress': 'AA:BB:CC:DD:EE:FF',
      'remembered:comdex:AA:BB:CC:DD:EE:FF': 'COM-DEX 415',
      'remembered:comdex:AA:BB:CC:DD:EE:00': 'COM-DEX 512',
      'remembered:cdrm:AA:BB:CC:DD:EE:11': 'Remote Mic',
    };
    expect(c.comdexTitle, 'COM-DEX 415');
    expect(c.devicesFor('comdex').map((d) => d['name']), [
      'COM-DEX 415',
      'COM-DEX 512',
    ]);
    c.accept({
      'type': 'device',
      'kind': 'comdex',
      'address': 'AA:BB:CC:DD:EE:FF',
      'name': 'COM-DEX 415',
    });
    expect(c.devicesFor('comdex'), hasLength(2));
  });
  test(
    'Legacy manual transport preference does not override native selection',
    () async {
      c.preferences['connectionMode'] = 'green';
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(ComdexController.channel, (call) async {
            calls.add(call);
            return call.method == 'permissions' ? true : null;
          });
      await c.connect('AA:BB:CC:DD:EE:FF');
      expect(calls.last.arguments, {
        'address': 'AA:BB:CC:DD:EE:FF',
        'kind': 'comdex',
      });
      await c.connect('AA:BB:CC:DD:EE:00', kind: 'cdrm');
      expect(calls.last.arguments, {
        'address': 'AA:BB:CC:DD:EE:00',
        'kind': 'cdrm',
      });
    },
  );
  test(
    'Remote Mic start is blocked during HFP, pause remains possible',
    () async {
      ready();
      c.cdrm.status = 'ready';
      state('0005', 2);
      expect(c.canStartRemoteStream, isFalse);
      await expectLater(
        c.command('stream', kind: 'cdrm', value: 2),
        throwsStateError,
      );
      expect(calls, isEmpty);
      await c.command('stream', kind: 'cdrm', value: 1);
      expect((calls.last.arguments as Map)['value'], 1);
    },
  );
  test(
    'Remote Mic start needs a working COM-DEX, not just the mic BLE link',
    () async {
      c.cdrm.status = 'ready';
      await expectLater(
        c.command('stream', kind: 'cdrm', value: 2),
        throwsStateError,
      );
      expect(calls, isEmpty);
    },
  );
  test('Media boost is blocked for calls without sending a command', () async {
    ready();
    state('0202', 0);
    state('0005', 2);
    expect(c.canBoost, isFalse);
    await expectLater(c.command('boost', value: 2), throwsStateError);
    expect(calls, isEmpty);
    state('0005', 3);
    expect(c.canBoost, isTrue);
  });
  test('Battery warnings match manufacturer boundaries', () {
    expect(c.comdex.batteryWarning, 0);
    for (final pair in [(21, 0), (20, 1), (5, 1), (4, 2), (0, 2), (255, 0)]) {
      c.comdex.values[batteryUuid] = [pair.$1];
      expect(c.comdex.batteryWarning, pair.$2);
    }
  });
}
