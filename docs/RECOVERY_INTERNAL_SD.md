# Pocket Flasher: recovery-assisted internal microSD flashing

## Status

**Research/design only. Raw image writing is not implemented.** The Android app currently copies files and verifies hashes; it must not claim to flash a bootable card.

Target phone: Motorola Moto G52 XT2221-1, stock Android 13, no root. Device codename: rhode.

## Findings

- Ordinary Android apps cannot use public storage APIs to write arbitrary sectors to the internal microSD slot.
- The Moto G52 (rhode) has community TWRP builds. The TeamWin device tree documents Android 12 firmware, working microSD support, ADB and flashing. Community release notes include Android 12/13 builds. This is evidence of a possible recovery route, not a guarantee for every XT2221-1 firmware/build.
- Motorola bootloader unlock eligibility is device/account/region dependent. The unlock process normally factory-resets the phone. Do not unlock until the owner has read the current Motorola requirements, backed up all data and explicitly agreed.
- A custom recovery is privileged software. A wrong recovery image or wrong block-device target can make the phone unbootable or destroy the SD card contents.
- The Android 13 security model and verified boot prevent a normal app or local server from gaining raw block-device privileges merely by using mobile data, ADB, or HTTP. A network transport does not change the process's kernel permissions.

## Work added

A read-only inventory script has been added at tools/recovery_sd_inventory.sh.

- Reads /sys/block, device metadata, partition entries, /proc/partitions, relevant mount records and block-node aliases.
- Does not call su, dd, mount, umount, fastboot, or any write operation.
- Does not automatically choose a target. It deliberately reports metadata for manual correlation because device numbering and the removable flag alone are not safe identification methods.
- Local shell syntax validation passed with sh -n, and a run in a Linux container completed. This is **not** a test on the Moto G52 or in its recovery environment; Android/recovery access and output still need to be checked on the actual phone.

## Proposed route: two-stage, recovery-assisted writer

1. **Android preparation stage (non-destructive):**
   - Select the source image and calculate SHA-256.
   - Require the user to copy the image to phone internal shared storage (not the target microSD).
   - Confirm available space, file size, image format, and a second SHA-256 after copying.
   - Create a recovery package containing a reviewed writer script and a manifest with the expected image path, byte size and SHA-256.
   - Do not run su, request root, or attempt block writes from the stock Android app.

2. **Recovery inventory stage (read-only):**
   - Boot a verified, exact-device-compatible recovery.
   - Run the inventory script and correlate block-device sysfs ancestry, capacity, card metadata, partition children and current mounts.
   - Identify the microSD only when multiple independent signals agree. mmcblk numbering alone and removable=1 alone are not sufficient.
   - Refuse to proceed if the candidate is ambiguous, the expected capacity does not match, the source image is on the target card, or the target contains mounted partitions that cannot be safely unmounted.
   - Show the selected whole-disk device and capacity to the user before enabling any writer.

3. **Explicit destructive confirmation:**
   - Require a typed confirmation containing the exact target device and capacity, plus a warning that all contents on that card will be destroyed.
   - Never auto-select a device and never accept a target path from an untrusted manifest without resolving and validating it inside recovery.
   - Reject the phone's internal UFS storage and its partitions by checking device ancestry and sysfs topology, not just naming patterns.

4. **Writer implementation (not yet implemented):**
   - Unmount every target-card partition and verify unmount success.
   - Write the image to the whole-disk block device using a recovery-supported, bounded streaming writer; check every read/write result and byte count.
   - Flush/sync and request block-buffer flush where supported.
   - Read back the written range and compare its SHA-256 to the source image; do not report verification success if read-back is unavailable or fails.
   - On interruption or error, report that the card may be partially written and must not be used as bootable media until rewritten and verified.
   - Log device identity, capacity, source hash, bytes written and verification result without uploading personal data.

## Mandatory gates before coding the destructive stage

- User explicitly understands and accepts bootloader-unlock/data-wipe risk.
- Confirm exact model and current firmware/build fingerprint; confirm Motorola unlock eligibility using Motorola's official flow.
- Verify the specific recovery image's provenance, checksum, compatibility with Android 13 and XT2221-1, and whether it can be booted temporarily without installing it.
- Test the read-only inventory on the actual phone and distinguish internal UFS from the inserted microSD with capacity/topology evidence.
- Unit-test the target-selection and image-size logic with synthetic sysfs fixtures, including ambiguous devices and malicious paths.
- Test writing only on a disposable microSD with a separate known-good backup and independent read-back verification.
- No CI build, static analysis, or synthetic test proves the hardware write is safe; hardware validation is mandatory.

## Safer fallback

If the bootloader cannot be unlocked or the user does not accept a factory reset, keep Pocket Flasher in non-destructive copy/hash mode. Use a separate card reader/computer to write the image. Do not claim that an Android permission, developer options, ADB debugging, or a normal file copy bypasses raw-block access restrictions.
