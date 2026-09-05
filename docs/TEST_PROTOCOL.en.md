# MobiMark test conditions and interpretation

[Home](../README.en.md) · [繁體中文](TEST_PROTOCOL.zh-TW.md)

Applies to v0.3.1. This is a project-specific operating protocol, **not an international certification, PCMark/MobileMark specification or laboratory power-measurement standard**.

## 1. App-enforced behavior

- Start above 80%, unplugged, with camera permission, rear 4K30 capability and at least 3 GiB available storage.
- Online web requires at least one valid URL; local video requires a complete, verified file. A valid URL alone does not establish service availability.
- Automatic DND defaults on and requires access. Resolve pending recovery records first.
- Preconditioning is unscored. The first sample ≤80% begins official timing and restarts at 3D gaming. Battery reports can skip levels; actual endpoints are used rather than inventing an exact 80% start.
- 3D/web/video/camera/office each last 180 seconds, totaling 900 seconds. The workload identifier remains `Ahuimark Workload 4.0 · Five workloads · Godot 4.7.2 · Office UX`, independent of product branding.
- Quick mode ends at four official hours or ≤20%, whichever comes first; full mode ends at ≤20%.
- Charging, leaving the foreground, battery temperature ≥48°C or invalid required DND state may abort. These safeguards are not a hardware-safety certification; provide ventilation and supervision for abnormal behavior.

## 2. Recommended operator-controlled conditions

| Condition | Recommendation / information to record |
| --- | --- |
| Brightness | Measure 200 nits on the white screen using an external meter; adaptive brightness off. The app neither sets nor verifies luminance. |
| Environment | Recommended 23±2°C; record room/starting battery temperatures, case and cooling conditions. |
| Display | Record resolution, refresh mode, HDR/color and power-saving settings. The high-refresh request does not guarantee actual delivery. |
| Battery | Record health/cycle count when available, capacity and start charge. Aging differences are not processor-only differences. |
| Network | Same Wi-Fi/AP and signal conditions; record SIM, Bluetooth and airplane mode. Do not keep USB charging active. |
| Content | Fix URL order, online/offline modes and video URL/hash. Online pages, ads and adaptive streaming can change. |
| System | Record model, OS/security updates, app/workload versions, background apps and performance mode. |
| Repetition | Cool to comparable conditions, repeat at least three times and report individual values, median and range. Not an automated app procedure. |

For more repeatability, use both offline web and identical local video. Online testing models network use but is not a closed fixed-content workload. Offline web alone does not make the entire run offline.

## 3. Calculation and measurement quality

With official recorded duration `T` and consumed battery percentage points `D`:

```text
T100 = T × 100 / D
T60  = T × 60 / D
```

Exactly 60 points consumed gives T100=T/0.6. A quick run consuming 30 points must use 30, not 60. Example: four official hours / 30 points gives T60=8h and T100=13h20m.

Android percentage reporting has limited resolution. Valid charge-counter-derived consumption may be preferred when consistent with percentage movement, or when the percentage has not changed; otherwise percentage differences are used. Charge counter, current and temperature depend on the device and are not external power-meter measurements.

Recovery estimates require at least 60 seconds of valid official records and positive consumption. Zero consumption cannot produce a valid estimate. Smaller discharge increases sensitivity to error. HIGH/MEDIUM/LOW estimate labels use consumption thresholds (≥20/≥10/fewer points), a **heuristic quality grade, not a statistical confidence interval**. Full mode's MEASURED label refers to its observed interval, not a directly measured 100% result.

Per-workload consumption accumulates/estimates whole-device battery data during each stage. It includes display, OS and network activity and cannot isolate CPU/GPU/camera power. Residual heat and asynchronous work can carry into the next stage.

## 4. Recovery and reports

Telemetry is recorded each second, with official checkpoints every 30 seconds. After a crash, reopening can estimate from the latest valid checkpoint; unwritten trailing activity and downtime are excluded. This is not resume support or a guarantee against disk corruption or cleared app data.

Completed runs and accepted recovery estimates can produce persistent results with multi-record history, deletion and ZIP exports containing PDF, JSON, CSV and event data. Manual stops and safety aborts differ from crash recovery. Review device details, custom URLs and camera-related information before sharing reports.

## 5. Limitations and validation levels

- Extrapolation assumes broadly stable consumption; low-battery throttling, heat and nonlinear battery behavior violate this assumption.
- Group full, quick and recovered results separately, and do not silently mix versions, settings or content sources.
- YouTube/WebView behavior, ads and autoplay depend on the service/device. Confirm visible moving video, not only audio. A failed workload is not a valid normal score.
- Advertised camera capability does not establish stable 4K30 output on every firmware. Verify recordings; do not label a 1080p fallback as 4K testing.
- AI interpolation, fixed 60 fps and commercial-game visual quality are not verified promises. Godot performance/appearance depend on hardware.
- This repository does not provide an iOS app. A future port requires workload/platform calibration before comparison.
- v0.3.1 build/unit-test/lint records do not establish long-duration or multi-device validation. Screenshot clocks are demonstration values, not benchmark results.
