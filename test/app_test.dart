import 'package:comdex/main.dart';
import 'package:comdex/src/controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'Settings and conditional Remote Mic tab render on a small phone',
    (tester) async {
      tester.view.physicalSize = const Size(360, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final c = ComdexController();
      await c.loadLocale('ru');
      await tester.pumpWidget(ComdexApp(controller: c));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.byIcon(Icons.mic_none), findsNothing);
      expect(find.byIcon(Icons.tune), findsNothing);
      await tester.tap(find.byIcon(Icons.settings_outlined));
      await tester.pumpAndSettle();
      expect(find.text('Язык'), findsOneWidget);
      c.accept({'type': 'connection', 'kind': 'cdrm', 'status': 'ready'});
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.mic_none));
      await tester.pumpAndSettle();
      c.accept({
        'type': 'connection',
        'kind': 'cdrm',
        'status': 'disconnected',
      });
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.mic_none), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      c.dispose();
    },
  );
}
