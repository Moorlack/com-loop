# Feature comparison

| Feature | COM-Loop behavior | Verification |
|---|---|---|
| Device picker | BLE scan and supported bonded devices; saved connection history | Flutter and Kotlin rules |
| Blue / Green | Automatic LE or BR/EDR GATT selection | Kotlin rules; radio behavior unverified |
| Connection and status | Foreground service; up to three reconnect attempts with 2/4/8-second delays | Build and controller tests |
| Hearing aid volume and mute | PI commands in idle; mute sends 22 down steps | Protocol fixture |
| Media and call volume | Android audio stream selected by the current profile | Build |
| Surrounding sounds | GATT characteristic 0003 in idle and during streaming | Flutter routing test |
| Remote Mic | Stream, gain, LED and speech controls when connected | Protocol notes and build |
| Battery and device status | GATT reads and notifications | Flutter state tests |
| Home-screen widgets | Horizontal and vertical | Android provider registration |
| Programs, Free Focus and Boost Media | Protocol support retained without public controls | Protocol fixture and Kotlin rules |
| Analytics, push and crash reporting | Omitted | Source inspection |

For hardware validation see the [device checklist](DEVICE_CHECKLIST.md).
