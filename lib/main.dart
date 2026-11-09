import 'package:flutter/material.dart';
import 'src/controller.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ComdexApp());
}

const _mint = Color(0xff64e5c2);

class _SignalMark extends StatelessWidget {
  const _SignalMark();

  @override
  Widget build(BuildContext context) => Container(
    width: 36,
    height: 36,
    decoration: BoxDecoration(
      color: const Color(0xff102425),
      borderRadius: BorderRadius.circular(11),
    ),
    child: const Center(
      child: SizedBox.square(
        dimension: 23,
        child: CustomPaint(painter: _LoopMarkPainter()),
      ),
    ),
  );
}

class _LoopMarkPainter extends CustomPainter {
  const _LoopMarkPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 66, size.height / 66);
    canvas.translate(-21, -21);
    final loop = Path()
      ..moveTo(64, 31)
      ..cubicTo(53, 23, 39, 28, 36, 40)
      ..cubicTo(33, 52, 40, 60, 49, 68)
      ..moveTo(73, 45)
      ..cubicTo(73, 54, 67, 61, 59, 68);
    canvas.drawPath(
      loop,
      Paint()
        ..color = _mint
        ..style = PaintingStyle.stroke
        ..strokeWidth = 9
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTRB(42, 65, 66, 84),
        const Radius.circular(4),
      ),
      Paint()..color = _mint,
    );
  }

  @override
  bool shouldRepaint(_LoopMarkPainter oldDelegate) => false;
}

class ComdexApp extends StatefulWidget {
  const ComdexApp({super.key, this.controller});
  final ComdexController? controller;
  @override
  State<ComdexApp> createState() => _ComdexAppState();
}

class _ComdexAppState extends State<ComdexApp> {
  late final ComdexController c = widget.controller ?? ComdexController();
  @override
  void initState() {
    super.initState();
    if (widget.controller == null) c.initialize();
  }

  @override
  void dispose() {
    if (widget.controller == null) c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) => MaterialApp(
      title: 'COM-Loop',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: _mint,
          brightness: Brightness.dark,
        ),
        scaffoldBackgroundColor: const Color(0xff101617),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xff101617),
          surfaceTintColor: Colors.transparent,
          centerTitle: false,
        ),
        navigationBarTheme: const NavigationBarThemeData(
          backgroundColor: Color(0xff192223),
          indicatorColor: Color(0xff254942),
        ),
        cardTheme: CardThemeData(
          color: const Color(0xff1b2526),
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
        ),
      ),
      home: Home(c: c),
    ),
  );
}

class Home extends StatefulWidget {
  const Home({super.key, required this.c});
  final ComdexController c;
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> with WidgetsBindingObserver {
  int tab = 0;
  ComdexController get c => widget.c;
  String t(String k) => c.tr(k);
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !c.demo) {
      c.perform(() async {
        await ComdexController.invoke<void>('snapshot');
      });
    }
  }

  void action(Future<void> Function() f) {
    c.perform(f);
  }

  void page(String title, Widget Function(BuildContext) body) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => ListenableBuilder(
          listenable: c,
          builder: (context, _) => Scaffold(
            appBar: AppBar(title: Text(title)),
            body: SafeArea(
              child: Column(
                children: [
                  if (c.error != null) errorBanner(),
                  Expanded(child: body(context)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget errorBanner() => Material(
    color: const Color(0xff653c3c),
    child: ListTile(
      leading: const Icon(Icons.info_outline),
      title: Text(c.error!),
      trailing: IconButton(
        icon: const Icon(Icons.close),
        onPressed: () {
          c.error = null;
          setState(() {});
        },
      ),
    ),
  );
  @override
  Widget build(BuildContext context) {
    final showMic = c.cdrm.ready && !c.demo;
    final selected = tab == 1 && !showMic ? 0 : tab;
    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            const _SignalMark(),
            const SizedBox(width: 12),
            Flexible(
              child: Text(
                c.comdexTitle,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.1,
                ),
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: t('status'),
            icon: const Icon(Icons.battery_std),
            onPressed: status,
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            if (c.error != null) errorBanner(),
            Card(
              margin: const EdgeInsets.fromLTRB(20, 8, 20, 0),
              child: InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: status,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 14,
                  ),
                  child: Row(
                    children: [
                      Icon(
                        c.comdex.canControl
                            ? Icons.bluetooth_connected
                            : Icons.bluetooth,
                        color: c.comdex.canControl
                            ? _mint
                            : const Color(0xffa6b5b5),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              c.comdex.ready
                                  ? c.profileLabel
                                  : connectionLabel(c.comdex.status),
                            ),
                            if (c.comdex.ready && !c.comdex.canControl)
                              Text(
                                c.comdex.byte('0007') == 1
                                    ? 'Зарядка · управление недоступно'
                                    : t('neckopen'),
                                style: const TextStyle(color: Colors.amber),
                              ),
                          ],
                        ),
                      ),
                      Text(
                        c.comdex.battery == null ? '—' : '${c.comdex.battery}%',
                        style: TextStyle(
                          color: c.comdex.batteryWarning == 2
                              ? Colors.redAccent
                              : c.comdex.batteryWarning == 1
                              ? Colors.amber
                              : null,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            Expanded(
              child: switch (selected) {
                1 => microphone(),
                2 => settings(),
                _ => sound(),
              },
            ),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: selected == 2 && !showMic ? 1 : selected,
        onDestinationSelected: (value) => setState(() {
          tab = !showMic && value == 1 ? 2 : value;
        }),
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.volume_up_outlined),
            label: t('remote'),
          ),
          if (showMic)
            NavigationDestination(
              icon: const Icon(Icons.mic_none),
              label: t('microphone'),
            ),
          NavigationDestination(
            icon: const Icon(Icons.settings_outlined),
            label: t('settings'),
          ),
        ],
      ),
    );
  }

  String connectionLabel(String value) => switch (value) {
    'ready' => t('connected'),
    'connecting' => 'Подключение…',
    'initializing' => 'Чтение состояния…',
    _ => t('disconnected'),
  };
  Widget round(
    IconData icon,
    String label,
    VoidCallback? onTap, {
    VoidCallback? hold,
  }) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      Semantics(
        label: label,
        button: true,
        enabled: onTap != null,
        child: GestureDetector(
          onLongPress: hold,
          child: IconButton.filledTonal(
            onPressed: onTap,
            icon: Icon(icon, size: 44),
            padding: const EdgeInsets.all(22),
            tooltip: label,
          ),
        ),
      ),
      const SizedBox(height: 10),
      Text(label, textAlign: TextAlign.center),
    ],
  );
  Widget sound() => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      Text(t('remote'), style: Theme.of(context).textTheme.headlineMedium),
      const SizedBox(height: 20),
      Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              round(
                Icons.remove,
                t('less_sound'),
                c.comdex.canVolume && !c.busy
                    ? () => action(() => c.command('down'))
                    : null,
                hold: c.comdex.canVolume && !c.busy
                    ? () => action(() => c.command('mute'))
                    : null,
              ),
              round(
                Icons.add,
                t('more_sound'),
                c.comdex.canVolume && !c.busy
                    ? () => action(() => c.command('up'))
                    : null,
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 20),
      Center(
        child: round(
          c.comdex.byte('0003') == 0
              ? Icons.volume_off_outlined
              : Icons.surround_sound_outlined,
          t('surroundings'),
          c.comdex.canControl && !c.busy ? () => action(c.surroundings) : null,
        ),
      ),
      if (!c.comdex.ready)
        Padding(
          padding: const EdgeInsets.only(top: 24),
          child: FilledButton.icon(
            onPressed: () => devices(),
            icon: const Icon(Icons.bluetooth_searching),
            label: Text(t('select_your_device')),
          ),
        ),
    ],
  );
  Widget microphone() => ListView(
    padding: const EdgeInsets.all(20),
    children: [
      ListTile(
        title: Text(t('microphone')),
        subtitle: Text(connectionLabel(c.cdrm.status)),
        trailing: Text('${c.cdrm.battery ?? '—'}%'),
      ),
      SwitchListTile(
        title: Text(t('streaming')),
        value: c.cdrm.byte('1001') == 2,
        onChanged: c.canChangeRemoteStream && !c.busy
            ? (v) => action(
                () => c.command('stream', kind: 'cdrm', value: v ? 2 : 1),
              )
            : null,
      ),
      const SizedBox(height: 20),
      Text(t('microphone'), textAlign: TextAlign.center),
      const SizedBox(height: 12),
      Wrap(
        alignment: WrapAlignment.center,
        spacing: 10,
        children: [
          for (var i = 0; i < 3; i++)
            ChoiceChip(
              label: Text(t(['low', 'medium', 'high'][i])),
              selected: c.cdrm.byte('1002') == i,
              onSelected: c.cdrm.ready && !c.busy
                  ? (_) =>
                        action(() => c.command('gain', kind: 'cdrm', value: i))
                  : null,
            ),
        ],
      ),
      const SizedBox(height: 24),
      SwitchListTile(
        title: Text(t('surroundings')),
        value: c.comdex.byte('0003') == 1,
        onChanged: c.comdex.canControl && !c.busy
            ? (v) => action(() => c.command('microphone', value: v ? 1 : 0))
            : null,
      ),
      SwitchListTile(
        title: Text(t('reduce_remote_mic_light')),
        value: c.cdrm.byte('1004') != null && c.cdrm.byte('1004')! <= 20,
        onChanged: c.cdrm.ready && !c.busy
            ? (v) => action(
                () => c.command('led', kind: 'cdrm', value: v ? 10 : 50),
              )
            : null,
      ),
      SwitchListTile(
        title: Text(t('enhance_speech_program')),
        value: c.comdex.byte('0203') == 1,
        onChanged: c.comdex.canControl && !c.busy
            ? (v) => action(() => c.command('speech', value: v ? 1 : 0))
            : null,
      ),
    ],
  );
  Widget menu(IconData icon, String label, VoidCallback onTap) => Card(
    child: ListTile(
      leading: Icon(icon),
      title: Text(label),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    ),
  );
  void devices({String kind = 'comdex'}) {
    page(
      kind == 'cdrm' ? 'Remote Mic' : t('select_your_device'),
      (_) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(t('pair_your_devices_1')),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: c.busy
                ? null
                : () => action(
                    c.scanning
                        ? () => ComdexController.invoke<void>('stopScan')
                        : () => c.scan(kind: kind),
                  ),
            icon: Icon(c.scanning ? Icons.stop : Icons.search),
            label: Text(c.scanning ? 'Остановить поиск' : 'Найти устройства'),
          ),
          if (c.scanning)
            const Padding(
              padding: EdgeInsets.all(12),
              child: LinearProgressIndicator(),
            ),
          if (c.devicesFor(kind).isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Нажмите поиск. Для Android 6–11 также включите геолокацию. Нажмите PUSH на COM-DEX, если он уснул.',
              ),
            ),
          for (final device in c.devicesFor(kind))
            Card(
              child: ListTile(
                title: Text('${device['name']}'),
                subtitle: Text(
                  device['remembered'] == true
                      ? 'Подключалось ранее'
                      : device['bonded'] == true
                      ? 'Сопряжено'
                      : 'Поблизости',
                ),
                trailing: const Icon(Icons.bluetooth),
                onTap: c.busy
                    ? null
                    : () => action(
                        () =>
                            c.connect(device['address'] as String, kind: kind),
                      ),
              ),
            ),
        ],
      ),
    );
  }

  void status() => page(
    t('status'),
    (_) => ListView(
      padding: const EdgeInsets.all(20),
      children: [
        for (final item in [
          ('COM-DEX', c.comdex, 'comdex'),
          ('Remote Mic', c.cdrm, 'cdrm'),
        ])
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item.$1, style: Theme.of(context).textTheme.titleLarge),
                  Text(connectionLabel(item.$2.status)),
                  if (item.$2.address != null) SelectableText(item.$2.address!),
                  Text('${t('battery_status')}: ${item.$2.battery ?? '—'}%'),
                  if (item.$3 == 'comdex') ...[
                    Text(c.profileLabel),
                    Text(
                      'Петля: ${item.$2.byte('0004') == 1
                          ? 'закрыта'
                          : item.$2.byte('0004') == 0
                          ? 'открыта'
                          : '—'}',
                    ),
                    Text(
                      'Зарядка: ${item.$2.byte('0007') == 1
                          ? 'да'
                          : item.$2.byte('0007') == 0
                          ? 'нет'
                          : '—'}',
                    ),
                  ],
                  SelectableText('S/N: ${item.$2.text(serialUuid)}'),
                  SelectableText('Firmware: ${item.$2.text(firmwareUuid)}'),
                  Wrap(
                    spacing: 8,
                    children: [
                      TextButton(
                        onPressed: () => devices(kind: item.$3),
                        child: Text(t('choose')),
                      ),
                      if (item.$2.status != 'disconnected')
                        TextButton(
                          onPressed: () =>
                              action(() => c.disconnect(kind: item.$3)),
                          child: Text(t('disconnect_device')),
                        ),
                      if (!c.demo && item.$2.address != null)
                        TextButton(
                          onPressed: () => action(
                            () => c.disconnect(kind: item.$3, forget: true),
                          ),
                          child: const Text('Забыть в приложении'),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        const Text(
          'Удаление системного сопряжения выполняется в настройках Bluetooth Android.',
        ),
        if (c.sessionActive)
          OutlinedButton(
            onPressed: () =>
                action(() => ComdexController.invoke<void>('stop')),
            child: const Text('Завершить Bluetooth-сеанс'),
          ),
      ],
    ),
  );
  Widget settings() => ListView(
    padding: const EdgeInsets.all(20),
    children: [
      Text(t('settings'), style: Theme.of(context).textTheme.headlineMedium),
      const SizedBox(height: 16),
      menu(Icons.bluetooth, t('select_your_device'), () => devices()),
      Card(
        child: ListTile(
          leading: const Icon(Icons.language_outlined),
          title: Text(c.locale == 'ru' ? 'Язык' : 'Language'),
          subtitle: Text(c.locale == 'ru' ? 'Русский' : 'English'),
          trailing: const Icon(Icons.chevron_right),
          onTap: chooseLanguage,
        ),
      ),
    ],
  );

  Future<void> chooseLanguage() async {
    final locale = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final option in [('ru', 'Русский'), ('en', 'English')])
                ListTile(
                  title: Text(option.$2),
                  selected: c.locale == option.$1,
                  trailing: c.locale == option.$1
                      ? const Icon(Icons.check_rounded)
                      : null,
                  onTap: () => Navigator.pop(sheetContext, option.$1),
                ),
            ],
          ),
        ),
      ),
    );
    if (!mounted || locale == null || locale == c.locale) return;
    action(() async {
      await c.loadLocale(locale);
      await c.save('locale', locale);
    });
  }
}
