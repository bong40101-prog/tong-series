# Offline app guides — release verification

Published on 2026-11-05.

## Scope

- TongHome 0.3.16 / 28, Tongstagram 1.1.7 / 12, TongSKY 1.0.51 / 10155, TongGlow 1.0.5 / 6.
- Add an in-app offline setup/usage guide and a visible 사용방법 button; fixed close control.
- TongCatch excluded from this implementation. Its concurrently published 0.1.10 listing and APK were preserved.
- Removed the requested security-warning sentence from three visible website locations.

## Verified

- Existing signing certificates and Android permission sets unchanged in all four APKs.
- Android 15 emulator: old-to-new signed installation, original install timestamp, offline guide opening, scrolling, close/back, retained application settings and sentinel data; no AndroidRuntime fatal exception.
- Device run: https://github.com/bong40101-prog/tong-series/actions/runs/41979044975
- Original run issued a rotation request but screenshots remained portrait. Rotation is therefore excluded from that run’s verified scope. Follow-up 41980052669 verifies actual 1920×1080 landscape behavior for Home, Photo and SKY; all three pass. Glow follow-up 41981683026 passes opening, scrolling, actual landscape rotation, close/back and settings preservation. Intermediate checks exposed transient ADB/accessibility initialization failures; the test now waits for a fresh WebView accessibility hierarchy and never reuses a stale dump. No APK changes were needed.
- TongGlow: 38 available core tests passed. The separate 40 MB personal export fixture was unavailable and was excluded. No parser/map logic changed. Local browser launch was blocked by the execution environment; native WebView screenshots were inspected instead.
- TongSKY: all APK assets match 1.0.50 exactly. Collection, shared storage and diagnostics bytecode matches except version literals.
- Public website and all five current APK URLs downloaded successfully. SHA256 values match downloads.json; all downloaded APK signatures verify. Glow install ZIP contains the identical signed APK.
- Public page inspected in browser: four new versions, latest TongCatch 0.1.10, requested warning absent.

## Publication

- Website commit: a622834d62a0e939d6c742a6c2d43d82e1bd6a9c
- APK staging commit: 137d2b4c8ddd326657aabedb000493a2c6afe29f
- Private Home/Photo source: homereturn-lab feat/offline-app-guides.
- Glow existing APK/source/install archive updated in place. Signing secrets excluded.

## APK hashes

- `TongHome-0.3.16.apk`: `a613ae0cdaa0bac2f6536fcbf2caec448bc7e3fb3167fd5eff5c882e3592ec66`
- `Tongstagram-1.1.7.apk`: `26f0586276d683700c531ec0f70f3f46df241f13ae76683b7164936882c6b1c8`
- `TongSKY-1.0.51.apk`: `b65713dd644d12eb312d51715ed4567bfd428bf2f892c12b90c995c630ad7c7c`
- `TongGlow-1.0.5.apk`: `d7f376b8f1a544c5d0fbc3bdd2f77290b2b9e759ff85a5f9b05db19a3f7dd62a`

Final Glow device verification: https://github.com/bong40101-prog/tong-series/actions/runs/41981683026 — success.
