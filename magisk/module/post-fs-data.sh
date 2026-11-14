#!/system/bin/sh
# Boot guard. service.sh clears the counter once Android has finished booting. If the counter reaches 3,
# the previous boots never completed: stop mounting the overlays (this boot) and disable the module (next boots).
MODDIR=${0%/*}
COUNT=$MODDIR/.boot_attempts
n=$(cat "$COUNT" 2>/dev/null || echo 0)
n=$((n + 1))
echo "$n" > "$COUNT"
if [ "$n" -ge 3 ]; then
  [ -d "$MODDIR/system" ] && mv "$MODDIR/system" "$MODDIR/system.off"
  touch "$MODDIR/disable"
  rm -f "$COUNT"
fi
