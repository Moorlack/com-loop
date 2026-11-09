"""Compare shipped Kotlin bytes with the reference fixture; no device access."""
from pathlib import Path
import json, re

root = Path(__file__).resolve().parents[1]
fixture = json.loads((root / 'test/protocol_fixture.json').read_text())
text = (root / 'android/app/src/main/kotlin/org/comdex/community/comdex/Protocol.kt').read_text()
actual = {name: [int(v) & 255 for v in re.findall(r'(\d+)\.toByte\(\)', values)]
          for name, values in re.findall(r'val (\w+) = byteArrayOf\((.*?)\)\n', text)}
assert actual == fixture, 'Kotlin wire bytes differ from supplied APK fixture'
assert fixture['VOL_UP_CMD'] == [0xa2, 0, 2, 0x80, 0, 0x27, 0x76, 0x7f]
assert fixture['VOL_DW_CMD'] == [0xa2, 0, 2, 0x80, 0, 0x28, 0x76, 0x70]
assert len(actual) == 10
print('PASS: all 10 Kotlin PI packets match APK reference; volume hex anchors match')
