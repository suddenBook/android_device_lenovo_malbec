# Device tree for Lenovo Idea Tab Pro Gen 2 (malbec)

The Lenovo Idea Tab Pro Gen 2 (marketed as *Lenovo Tab K13* in some regions) is a
tablet released in 2026. `malbec` is Lenovo's internal codename for the device;
`TB390FU` is the Wi-Fi-only model number.

## Specifications

| Feature | Specification |
|---|---|
| SoC | Qualcomm SM8735P (Snapdragon 8s Gen 4), silicon codename **Kera** |
| Platform target | `sun` (`ro.board.platform`, `TARGET_BOOTLOADER_BOARD_NAME`) |
| CPU | 1× Cortex-X4 + 3× Cortex-A720 + 4× Cortex-A720 |
| GPU | Adreno 825 |
| Architecture | `arm64-v8a` (64-bit only) |
| Kernel | GKI 2.0, `android15-6.6` (KMI generation 8), 4 KB pages |
| Display | 144 Hz DSI video mode; dual sourced — BOE `nt36536e` / CSOT `nt36536` |
| Touchscreen | Goodix Berlin (I²C `0x5d` / SPI), supports Qualcomm Trusted Touch |
| Stylus | Active stylus via the Goodix digitizer |
| Keyboard | Lenovo pogo-pin folio keyboard (`lenovo_kb` platform driver over UART) |
| Storage | UFS, dynamic partitions, Virtual A/B with compression |
| Connectivity | Wi-Fi + Bluetooth (no cellular on this model) |
| Shipped Android | 16 (ZUI 18), vendor frozen at API level 202404 |

## Device tree layout

```
malbec/
├── configs/          VINTF manifests, filesystem config, HAL configs
├── overlay/          Framework RRO overlays
├── rootdir/          init scripts and device-specific rc files
├── sepolicy/         SELinux policy
└── proprietary-files.txt
```

## Notes

The device ships an unmodified Google GKI kernel with all hardware support
provided as loadable modules in `vendor_dlkm`. The stock `boot`, `vendor_boot`,
`dtbo`, `vendor`, `vendor_dlkm` and `odm` images are retained; this tree builds
`system`, `system_ext` and `product` only.

Because panel selection is performed by the bootloader (`ro.boot.lcd_type`) and
resolved inside the retained `dtbo` and vendor display HAL, both panel suppliers
are supported without device tree changes. Do not hardcode panel-specific values
in this tree.

## Credits

Reference bringup based on the SM8735 platform work in
[`PixelOS-Devices/android_device_xiaomi_onyx`](https://github.com/PixelOS-Devices/android_device_xiaomi_onyx).
