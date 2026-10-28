# Raw image writing — implementation plan

## Current status

Pocket Flasher does not perform raw writes. `RawWritePreflight` is a pure validation gate and must not be treated as a writer or as proof that a device is writable.

## Backend order

1. **USB OTG mass-storage reader (preferred first implementation):** Android USB Host API can enumerate a USB device and request permission, but it does not expose a generic raw-block file descriptor. A writer would need a tested USB Mass Storage Bulk-Only Transport implementation and SCSI commands (INQUIRY, READ CAPACITY, READ/WRITE, SYNCHRONIZE CACHE where supported). Device quirks and endpoint stalls must be handled explicitly.
2. **Internal microSD slot:** investigate only after read-only diagnostics establish whether the card is exposed as a separate removable MMC block device and which privilege boundary can access it. Android's Storage Access Framework grants file access, not raw-sector access. Shizuku/ADB shell identity is not root by itself. Do not assume `/dev/block/mmcblk*` paths or infer a target from device numbering.

## Mandatory writer invariants

- No target selected by array position, guessed path, display name, or removable flag alone.
- Require explicit user selection and show stable device identity, capacity, and backend type.
- Require source length and SHA-256; compare to an official hash when available.
- Reject unknown sizes, invalid hashes, capacity mismatch, non-removable or unverified targets, and any target whose write capability has not been confirmed by the backend.
- Re-read target identity/capacity immediately before the first write; abort if either changed.
- Never auto-format, repartition, or mount the destination for writing.
- Use bounded chunks; report progress and cancellation state. Cancellation must stop future writes but must clearly warn that a partially written card may be unusable.
- Flush/synchronize where supported, then read back and verify the written range by SHA-256. A successful USB transfer alone is not verification.
- Do not claim the card is bootable merely because bytes were written; bootability depends on the image and target hardware.
- No production write mode until unit tests, protocol tests with a simulated device, and a physical-device test plan pass.

## Safe development milestones

1. Pure validation tests (implemented in `RawWritePreflight.kt` and `RawWritePreflightTest.kt`).
2. Read-only USB Mass Storage inquiry/capacity discovery, with protocol unit tests.
3. A dry-run UI showing exact target identity, image size/hash, and all warnings; no write commands.
4. Writer behind a disabled feature flag, then controlled destructive tests on a spare card only.
5. Enable writing only after review and physical validation.

## Current restriction

Do not wire the preflight result directly to a destructive operation. The preflight is necessary but insufficient: a concrete backend must independently verify its device handle, identity, capacity, and write permission at execution time.
