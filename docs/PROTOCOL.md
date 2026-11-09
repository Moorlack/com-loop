# COM-DEX protocol notes

The implementation was checked against COM-DEX 1.3.0 (118). The reference APK fingerprint is in [apk.json](../research/apk.json). The observations below come from static analysis and have not all been verified over the air.

COM-DEX control uses GATT over LE in Blue mode or BR/EDR in Green mode. Audio uses Android's Bluetooth Classic A2DP and HFP. The vendor UUID prefix is `95986f2d-d705-4421-9890-2d32bff7`, followed by the four-character suffix below.

| Suffix | Function | Values or notes |
|---|---|---|
| 0000 | COM-DEX service | |
| 0001 | Activate app | Subscribe; no magic handshake write in the reference |
| 0002 | Select program | Declared; program controls use PI |
| 0003 | Microphone control | 0 off, 1 on |
| 0004 | Neckloop status | 0 open, 1 closed |
| 0005 | Audio profile | 0 disconnected, 1 idle, 2 HFP, 3 A2DP, 4 Remote Mic streaming |
| 0006 | Usage counters | Declared |
| 0007 | Charging | 0 idle, 1 charging |
| 0100 | PI command | See table below |
| 0101 | PVT override | Declared |
| 0200 | Connected Remote Mic name | Text |
| 0201 | Connected Remote Mic MAC | Six bytes in order; empty or zero indicates disconnect |
| 0202 | Boost media | 0 off, 2 on |
| 0203 | Enhance speech | 0 off, 1 on |
| 1000 | Remote Mic service | |
| 1001 | Remote Mic stream | 1 off, 2 on |
| 1002 | Remote Mic gain | 0 low, 1 medium, 2 high |
| 1003 | Remote Mic charging | 0/1 |
| 1004 | Remote Mic LED | Write 10 dimmed, 50 normal; read 0–20 indicates dimmed |

Standard characteristics include battery level (2A19), serial number (2A25) and firmware revision (2A28). Short states are unsigned bytes at offset zero. Profile 5 is a locally derived Remote Mic idle state in the original app.

## PI packets

| Command | Bytes |
|---|---|
| Volume up | A2 00 02 80 00 27 76 7F |
| Volume down | A2 00 02 80 00 28 76 70 |
| Next program | A2 00 02 80 00 29 76 71 |
| Normal / Zen+ | A2 00 02 80 00 2A 76 72 |
| Room on | A2 01 40 A8 20 11 00 0A 08 00 04 00 16 64 8D |
| Focus omni | A2 01 40 A8 20 11 00 10 00 00 00 00 13 64 86 |
| Focus front | A2 01 40 A8 20 11 00 10 00 00 0C 10 13 1B 43 |
| Focus rear | A2 01 40 A8 20 11 00 10 00 00 0D 10 13 A1 EE |
| Focus left | A2 01 40 A8 20 11 00 10 00 00 0E 10 13 D4 B4 |
| Focus right | A2 01 40 A8 20 11 00 10 00 00 0F 10 13 6E 19 |

Free Focus sends Room On before the direction packet. Room Off uses characteristic 0003. Idle volume uses relative PI commands. Idle mute sends 22 down steps with a five-second repeat guard. A subsequent up step does not restore the previous level.

In A2DP, volume uses Android stream 3 (media). In HFP it uses stream 6 (Bluetooth SCO). Holding volume down sets the active system stream to zero.

The accessory does not expose absolute hearing aid volume or the program number. A write with response confirms the GATT operation. A write without response only confirms handling by the Android stack. Neither establishes the acoustic result.

GATT operations are serialized. A timeout closes the session without retrying an ambiguous write. COM-DEX control requires a closed neckloop and a reported non-charging state.

Reference points include `ComDexValues.java` for PI packets, the GATT attribute definitions for UUIDs, and the remote and program screens for routing. The original APK and decompiled output are kept locally.
