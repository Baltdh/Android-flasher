#!/system/bin/sh
# Pocket Flasher — read-only ADB/Shizuku storage probe
# Safe for execution as Android shell UID. Never writes to block devices.
set +e

section() { printf '\n===== %s =====\n' "$1"; }

section "IDENTITY"
id
printf 'SDK='
getprop ro.build.version.sdk
printf 'BUILD='
getprop ro.build.fingerprint

section "SELINUX"
getenforce 2>&1

section "VOLD_DISKS"
sm list-disks 2>&1

section "DISK_SERVICE"
dumpsys disk 2>&1 | head -n 160

section "MMC_BLOCK_NODE_METADATA"
for p in /dev/block/mmcblk*; do
  [ -e "$p" ] || continue
  ls -l "$p" 2>&1
done

section "SYSFS_MMC_METADATA"
for p in /sys/block/mmcblk*; do
  [ -e "$p" ] || continue
  printf 'DEVICE=%s\n' "${p##*/}"
  printf 'REMOVABLE='
  cat "$p/removable" 2>/dev/null
  printf 'SECTORS_512B='
  cat "$p/size" 2>/dev/null
  printf 'SYSFS_DEVICE='
  readlink -f "$p/device" 2>/dev/null
  printf 'PARTITIONS='
  ls "$p" 2>/dev/null | grep -E "^${p##*/}p?[0-9]+$" | tr '\n' ' '
  printf '\n'
done

section "BLOCK_ALIAS_METADATA"
for p in /dev/block/by-name /dev/block/platform; do
  [ -e "$p" ] || continue
  ls -l "$p" 2>&1 | head -n 100
done

section "MOUNTS"
cat /proc/mounts 2>/dev/null | grep -E 'mmcblk|/storage/|/mnt/media_rw' | head -n 120

section "SAFETY_SUMMARY"
echo "RESULT=READ_ONLY_PROBE"
echo "No block device was opened for writing."
echo "Shell visibility or file mode bits do not prove raw-write permission."
echo "Do not run dd, mkfs, fdisk, parted, blockdev --set*, chmod, chown or mount commands."
