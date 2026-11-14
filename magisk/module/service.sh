#!/system/bin/sh
# After boot: make sure the overlay is enabled (the choice persists, so this only does work the first time),
# then reset the boot-failure counter.
MODDIR=${0%/*}
LOG=$MODDIR/log.txt
log() { echo "$(date '+%F %T') $*" >> "$LOG"; }
[ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 20000 ] && : > "$LOG"

while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 3; done
until cmd overlay list >/dev/null 2>&1; do sleep 2; done

for p in com.slate.skin.android; do
  if ! cmd overlay list --user 0 2>/dev/null | grep -q "$p"; then
    log "$p is not registered (is it in the product / system overlay folder?)"
    continue
  fi
  if cmd overlay list --user 0 2>/dev/null | grep -q "^\[x\] $p"; then
    log "$p already enabled"
  else
    cmd overlay enable --user 0 "$p" >> "$LOG" 2>&1 && log "enabled $p"
    cmd overlay set-priority --user 0 "$p" highest >> "$LOG" 2>&1
  fi
done

sleep 30
rm -f "$MODDIR/.boot_attempts"
log "boot guard cleared"
