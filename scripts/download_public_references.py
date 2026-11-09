"""Archive public manufacturer/FCC documents with URLs and SHA-256 provenance."""
from pathlib import Path
import datetime, hashlib, json, urllib.request

root = Path(__file__).resolve().parents[1] / 'research/public'
sources = root / 'sources'
sources.mkdir(parents=True, exist_ok=True)
documents = [
 ('widex-troubleshooting-2016.pdf', 'WIDEX COM-DEX troubleshooting guide, September 2016', 'https://www.widexpro.com/-/media/widex/global/files/accessories/com-dex-trouble-shooting-week-46.pdf'),
 ('widex-com-dex-manual.pdf', 'WIDEX user instructions, mirrored manufacturer document', 'https://hmi-basen.dk/en/blobs/brugsvejl/47034.pdf'),
 ('widex-com-dex-faq.pdf', 'WIDEX COM-DEX Questions and Answers', 'https://www.widexpro.com/-/media/widex/global/files/accessories/com-dex-questions-and-answers.pdf'),
 ('widex-two-phones-2016.pdf', 'WIDEX connecting two phones, September 2016', 'https://www.widex.com/-/media/widex/global/files/accessories/com-dex-connecting-two-phones-guide-september-2016.pdf?hash=711D7DF4D849FA4C3E0647559C878B68&rev=-1'),
 ('widex-com-dex-datasheet.pdf', 'WIDEX technical data sheet (Spanish)', 'https://www.widex.com.uy/descargas/accesorios/dex/ficha-tecnica-com-dex.pdf'),
 ('fcc-com-dex-ble-report.pdf', 'WIDEX / TUV FCC BLE radio test report, 2774798', 'https://fcc.report/FCC-ID/TTY-CMDEX/2774798.pdf'),
 ('fcc-com-dex-internal-photos.pdf', 'WIDEX FCC internal photos, 2774834', 'https://fcc.report/FCC-ID/TTY-CMDEX/2774834.pdf'),
 ('fcc-com-dex-confidentiality.pdf', 'WIDEX FCC confidentiality request, 2774807', 'https://fcc.report/FCC-ID/TTY-CMDEX/2774807.pdf'),
 ('fcc-com-dex-manual-part1.pdf', 'WIDEX FCC user instructions part 1, 2774832', 'https://fcc.report/FCC-ID/TTY-CMDEX/2774832.pdf'),
 ('fcc-com-dex-manual-part2.pdf', 'WIDEX FCC user instructions part 2, 2774833', 'https://fcc.report/FCC-ID/TTY-CMDEX/2774833.pdf'),
 ('widex-remote-mic-manual-2020.pdf', 'WIDEX Remote Mic instructions 2020-01, mirrored manufacturer PDF', 'https://device.report/m/48a0cc1aab533fe378c03701a1a9b74224d61e0054f307aeaa834e8542b09710.pdf'),
]
manifest = []
for name, title, url in documents:
    record = dict(file='sources/'+name, title=title, url=url, retrieved_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    try:
        if (sources/name).exists():
            data = (sources/name).read_bytes()
            record.update(status='downloaded', bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), cached=True)
            manifest.append(record)
            continue
        req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0 (COM-DEX documentation research)'})
        with urllib.request.urlopen(req, timeout=45) as response:
            data = response.read(30 * 1024 * 1024)
            record['resolved_url'] = response.url
        if not data.startswith(b'%PDF-'): raise ValueError('Response is not a PDF')
        (sources/name).write_bytes(data)
        record.update(status='downloaded', bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        print(f'OK {name}: {len(data)} bytes', flush=True)
    except Exception as error:
        record.update(status='failed', error=str(error))
        print(f'FAILED {name}: {error}', flush=True)
    manifest.append(record)
    (root/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
