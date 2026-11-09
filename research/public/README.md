# Public COM-DEX reference collection

The [manifest](manifest.json) records source URLs, SHA-256 hashes and download outcomes. Five WIDEX documents were collected locally: the FAQ, troubleshooting guide, two-phone guide, datasheet and user manual. The manual is hosted by the Danish assistive devices database.

Some PDFs render readable text as outlines, so plain-text extraction may miss pages. FCC listings identify a [BLE report](https://fccid.io/TTY-CMDEX/Test-Report/Test-report-for-COM-DEX-FCC-RF-bt4-0-2774798) and [internal photographs](https://fccid.io/TTY-CMDEX/Internal-Photos/Internal-Photos-2774834). The documents returned HTTP 403 during collection. They were not used as evidence for hardware or firmware details.

To reproduce the local collection, run `python scripts/download_public_references.py`. Install `pypdf` before running `python scripts/extract_public_references.py`.
