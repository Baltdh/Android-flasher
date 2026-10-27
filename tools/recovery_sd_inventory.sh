#!/bin/sh
# Pocket Flasher — read-only block-device inventory
# Target: Android shell or compatible recovery environment.
# SAFETY: This script MUST NOT write to block devices, mount/unmount filesystems,
# change permissions, or invoke su/fastboot/dd. It only reads sysfs/proc metadata.

set -u

say() { printf '%s\n' "$*"; }
read_one() {
    if [ -r "$1" ]; then
        IFS= read -r value < "$1" || value=""
        printf '%s' "$value"
    else
        printf '%s' "(unavailable)"
    fi
}

say "Pocket Flasher — read-only storage inventory"
say "mode=READ_ONLY"
say "warning=This report does not identify a safe write target automatically."
say "warning=Never infer the microSD from mmcblk numbering alone."
say ""

say "=== Kernel block devices (/sys/block) ==="
found=0
for node in /sys/block/*; do
    [ -e "$node" ] || continue
    found=1
    name=${node##*/}
    say "-- $name"
    say "  sysfs_path=$node"
    say "  size_sectors=$(read_one "$node/size")"
    say "  removable=$(read_one "$node/removable")"
    say "  device_path=$(readlink -f "$node/device" 2>/dev/null || printf '(unavailable)')"
    if [ -r "$node/device/model" ]; then
        say "  model=$(read_one "$node/device/model")"
    fi
    if [ -r "$node/device/vendor" ]; then
        say "  vendor=$(read_one "$node/device/vendor")"
    fi
    if [ -r "$node/device/type" ]; then
        say "  device_type=$(read_one "$node/device/type")"
    fi
    if [ -r "$node/uevent" ]; then
        say "  uevent:"
        while IFS= read -r line; do
            case "$line" in
                DEVTYPE=*|PARTN=*|PARTNAME=*|MAJOR=*|MINOR=*|DEVNAME=*|MMC_TYPE=*|MMC_NAME=*)
                    say "    $line"
                    ;;
            esac
        done < "$node/uevent"
    fi
    for part in "$node"/*; do
        [ -r "$part/partition" ] || continue
        pname=${part##*/}
        say "  partition=$pname number=$(read_one "$part/partition") size_sectors=$(read_one "$part/size")"
    done
done
[ "$found" -eq 1 ] || say "(no readable block devices found under /sys/block)"
say ""

say "=== /proc/partitions (read-only) ==="
if [ -r /proc/partitions ]; then
    cat /proc/partitions
else
    say "(unavailable)"
fi
say ""

say "=== Current mounts (read-only view) ==="
if [ -r /proc/mounts ]; then
    while IFS= read -r line; do
        case "$line" in
            *mmcblk*|*sd[a-z]*|*vold*|*media_rw*|*/storage/*|*/mnt/*)
                say "$line"
                ;;
        esac
    done < /proc/mounts
else
    say "(unavailable)"
fi
say ""

say "=== Block-node aliases (read-only listing) ==="
for dir in /dev/block /dev/block/by-name /dev/block/bootdevice/by-name; do
    if [ -d "$dir" ]; then
        say "-- $dir"
        ls -l "$dir" 2>/dev/null || say "(listing unavailable)"
    fi
done
say ""

say "RESULT=INVENTORY_ONLY"
say "next_step=Manually correlate sysfs ancestry, capacity, card metadata and mounts."
say "safety_gate=If any signal conflicts or the SD cannot be distinguished from UFS, STOP."
say "not_implemented=No target selection, unmount, write, erase, or read-back operation."
