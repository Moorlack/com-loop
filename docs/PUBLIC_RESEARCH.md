# WIDEX COM-DEX public references

The [source manifest](../research/public/manifest.json) lists document URLs, retrieval status and SHA-256 hashes. The downloaded PDFs are kept locally.

The WIDEX troubleshooting guide documents Blue and Green modes. The original app maps those modes to Android's LE and BR/EDR GATT transports respectively. Mode selection in COM-Loop chooses a transport; the accessory's own mode is set on the hardware.

The FAQ documents Remote Mic restrictions during calls and battery warning levels. The datasheet distinguishes Bluetooth Classic audio from the WidexLink radio connection to the hearing aids. The two-phone guide calls for firmware 1.2.0 or later and recommends running the controller on one phone at a time.

The search did not find a public SDK, firmware source or specification for the PI commands. See [protocol notes](PROTOCOL.md) for what was established through static analysis and [reference collection notes](../research/public/README.md) for the source inventory.
