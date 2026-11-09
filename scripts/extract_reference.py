"""Rebuild local protocol fixtures from JADX output."""
from argparse import ArgumentParser
from pathlib import Path
import hashlib, json, re

parser = ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path, help='Path to the original COM-DEX APK')
apk = parser.parse_args().apk
apk_sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()

root = Path(__file__).resolve().parents[1]
original = root / 'research/original'
source = original / 'sources/com/widex/comdex'
text = (source / 'gatt/comdex/ComDexValues.java').read_text()
commands = {name: [int(x.strip()) & 255 for x in values.split(',')]
            for name, values in re.findall(r'byte\[\] (\w+) = \{([^}]+)\}', text)}
(root / 'test/protocol_fixture.json').write_text(json.dumps(commands, indent=2))
kotlin = 'package org.comdex.community.comdex\n\n// Byte-for-byte from supplied APK; see docs/PROTOCOL.md.\nobject Protocol {\n'
for name, values in commands.items():
    kotlin += f'    val {name} = byteArrayOf(' + ', '.join(f'{v}.toByte()' for v in values) + ')\n'
kotlin += '    fun uuid(tail: String) = "95986f2d-d705-4421-9890-2d32bff7$tail"\n}\n'
(root / 'android/app/src/main/kotlin/org/comdex/community/comdex/Protocol.kt').write_text(kotlin)
(root / 'research/apk.json').write_text(json.dumps({'file': apk.name, 'sha256': apk_sha256, 'bytes': apk.stat().st_size, 'package': 'com.widex.comdex', 'versionName': '1.3.0', 'versionCode': 118, 'minSdk': 19, 'targetSdk': 25}, indent=2))
print(f'Extracted {len(commands)} commands')
