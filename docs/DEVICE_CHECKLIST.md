# Hardware checklist

1. Pair COM-DEX in Android settings. Close its neckloop and disconnect the charger. Press PUSH if the accessory is asleep.
2. Close the original controller's Bluetooth session before testing COM-Loop. Grant nearby device access. On Android 7–11, BLE scanning also requires location access and enabled location services.
3. Check that the device picker shows compatible accessories and remembers a previously connected unit.
4. Connect and check the battery, neckloop, charging state, audio profile, firmware and serial number. Verify that opening the neckloop blocks control and closing it restores control.
5. Check volume down and up in idle. Test Room Off in idle and during A2DP playback. Check that media and phone call volume use the corresponding Android audio streams.
6. If available, connect Remote Mic and test stream, gain, LED and speech enhancement. Starting a Remote Mic stream should be unavailable during a call.
7. Test screen lock, Bluetooth off, loss of range and reconnect. Previous control commands should never replay on reconnect.
8. Add both widgets and check the disconnected state, battery, button feedback, volume and Room Off.

Record phone and Android version, COM-DEX firmware, hearing aid models, steps and observed result. A GATT acknowledgment and an audible effect are separate observations. Remove Bluetooth addresses from any shared logs.

WIDEX specifies firmware 1.2.0 or later for two-phone operation and recommends running the controller on only one phone. Battery status refers to COM-DEX or Remote Mic.
