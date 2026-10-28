# Pocket Flasher — Shizuku integration study and implementation plan

Date: 2026-10-27

## Executive decision

Integrate the official Shizuku API into Pocket Flasher for connection status, explicit user authorization, privilege-level reporting, and carefully scoped diagnostics. Keep the existing `rish` workflow as a manual troubleshooting aid only. Do not build the app around parsing arbitrary shell output when a documented API is available.

This is an integration plan and research note; it does not claim that Shizuku is currently working on the Moto G52 or that raw SD writing is possible.

## Source-derived facts

Based on the official Shizuku documentation and Shizuku-API README:

- Shizuku starts a server process using root or ADB/shell privileges, depending on how the user starts it.
- Apps communicate with Shizuku through Binder IPC. Shizuku can forward Binder requests to Android system services.
- The API exposes binder lifecycle listeners, permission checks/requests, and the server UID. UID 2000 indicates ADB/shell; UID 0 indicates root. This reports the server identity, not permission to perform every operation.
- The API offers two principal approaches:
  1. Remote Binder wrappers for suitable system service interfaces.
  2. UserService for Java/native code running in the Shizuku server identity. A UserService is not a normal Android app process; many app Context APIs do not work there.
- ADB/shell permissions differ from root, Android version, Linux UID/capabilities and SELinux policy. Shizuku does not make ADB equivalent to root.
- On non-root devices, Shizuku must be started again after reboot; Android 11+ supports starting it with Wireless Debugging without a computer.
- The documented API includes an app-side provider and permission flow. The app should use the official API rather than assume the manual rish script is a stable app integration.

Official references:
- https://github.com/RikkaApps/Shizuku
- https://github.com/RikkaApps/Shizuku-API
- https://shizuku.rikka.app/introduction/
- https://shizuku.rikka.app/guide/setup/

## Field evidence from Moto G52 (Android 13)

- The user has a `rish` shell script and matching `rish_shizuku.dex` in shared Documents.
- Both were copied to Termux private home successfully.
- The script originally had generic `RISH_APPLICATION_ID="PKG"`; the test explicitly set `RISH_APPLICATION_ID=com.termux`.
- Running `RISH_APPLICATION_ID=com.termux sh "$HOME/rish" -c 'id'` returned a Shizuku connection timeout and suggested disabling battery optimization for Termux and Shizuku.
- Therefore, the rish connection remains unverified. The next manual check is to verify the Shizuku service is running, remove battery restrictions for both apps, check the official Termux/rish setup, and repeat only the harmless `id` check.
- A prior diagnostic showed `dumpsys package com.pocketflasher.android` denied due to missing `android.permission.DUMP`. This is a privilege limitation, not proof the app is absent.
- No raw block device visibility or write permission has been demonstrated by the provided logs.

## Recommended application architecture

### 1. ShizukuConnectionManager (app process)
- Add the official `dev.rikka.shizuku:api` and `dev.rikka.shizuku:provider` dependencies using a verified current stable version.
- Register binder-received and binder-dead listeners.
- On app resume, check whether the binder is available.
- When unavailable, show a setup state with a link to the official setup guide; do not repeatedly prompt or silently start services.
- Request Shizuku permission only after a user action, then re-check permission in the callback.
- Display distinct states: not installed/unavailable, service stopped, permission needed, connected as ADB/shell, connected as root, disconnected.
- Query the server UID through the documented API and report it; do not infer raw write capability from UID alone.
- Handle RemoteException, runtime exceptions and binder death; never block the UI thread.

### 2. Capability diagnostics (read-only)
- First use public Android APIs such as StorageManager and Storage Access Framework for logical volumes and file URIs.
- Use Shizuku only for additional diagnostic queries that are necessary and permitted.
- If a UserService is justified, give it a narrow API and allow-listed read-only operations. Avoid a general-purpose arbitrary shell executor in the production UI.
- Report every capability as one of: supported/observed, denied, not exposed, or not tested. Do not equate “device visible” with “device writable”.
- Do not request `android.permission.DUMP` as a workaround for an ordinary app.
- Export a privacy-conscious diagnostic report without unnecessary personal identifiers.

### 3. Raw-write gate (must remain disabled)
Do not implement or expose raw writing until all of these are independently satisfied:
- A supported interface exists to address the removable card as a whole device.
- The target is unambiguously distinguished from internal UFS storage.
- The required write permissions are documented and confirmed, not guessed from file mode bits.
- Capacity, image size, target identity, cancellation, sync, and read-back verification are designed and tested on disposable media.
- The verification reads written sectors from the device; hashing a copied IMG file does not prove a bootable write.

No destructive probes are permitted as a way to discover permissions. Do not run dd, mkfs, wipefs, partitioning tools, or mount/unmount commands during this investigation.

## Implementation sequence

1. Add Shizuku API/provider dependencies and the manifest provider according to the current official API docs.
2. Implement a small Shizuku status panel with binder lifecycle, explicit permission request and UID display.
3. Add unit-testable state mapping for disconnected / running-no-permission / authorized-ADB / authorized-root.
4. Add an integration checklist for Moto G52 Android 13 and test the non-destructive `id` equivalent through the app's Shizuku integration.
5. Add only read-only diagnostics after the connection flow is stable.
6. Reassess raw writing separately; current evidence does not establish a supported route for internal-slot raw writing without root.

## Acceptance criteria

- The app clearly distinguishes Shizuku availability from authorization.
- Binder death returns the UI to a disconnected state without crashing.
- UID and permission state are shown separately from storage capability.
- No diagnostic claims raw-write support merely because Shizuku is connected.
- All current file selection, SHA-256 and non-bootable file-copy behavior remain intact.
- No destructive write code is added in this phase.
