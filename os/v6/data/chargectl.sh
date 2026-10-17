#!/system/bin/sh
# Synapse charge limiter for MediaTek (Stratus C8). Root.
# Holds the battery between LOW% and HIGH% while on wall power, so a phone living
# on a charger 24/7 doesn't sit at 100% and swell. Settings (adb shell setprop …):
#   persist.synapse.charge_limit  1 = on (default), 0 = always charge normally
#   persist.synapse.charge_high   stop charging at this % (default 80)
#   persist.synapse.charge_low    start charging again at this % (default 40)
# Safety: always charges below 20 %. Stops charging at >= 45.0 °C battery temp.
# If this script dies or the phone reboots, the kernel default (charging ON) returns.
CMD=/proc/mtk_battery_cmd/current_cmd
CAP=/sys/class/power_supply/battery/capacity
TEMP=/sys/class/power_supply/battery/temp
LOG=/data/adb/synapse/chargectl.log
log() { echo "$(date '+%F %T') $*" >> $LOG; }

[ -e $CMD ] || { log "no $CMD on this kernel, exiting"; exit 0; }
state=unknown
log "start"
while true; do
  on=$(getprop persist.synapse.charge_limit); [ -z "$on" ] && on=1
  high=$(getprop persist.synapse.charge_high); [ -z "$high" ] && high=80
  low=$(getprop persist.synapse.charge_low);  [ -z "$low" ] && low=40
  cap=$(cat $CAP 2>/dev/null)
  t=$(cat $TEMP 2>/dev/null)
  case "$cap" in ''|*[!0-9]*) sleep 60; continue ;; esac
  case "$t" in ''|*[!0-9-]*) t=0 ;; esac

  want=$state
  if [ "$on" != 1 ] || [ "$cap" -le 20 ]; then want=charge
  elif [ "$t" -ge 450 ]; then want=hold
  elif [ "$cap" -ge "$high" ]; then want=hold
  elif [ "$cap" -le "$low" ]; then want=charge
  elif [ "$state" = unknown ]; then want=charge
  fi

  if [ "$want" != "$state" ]; then
    if [ "$want" = hold ]; then echo "0 1" > $CMD; else echo "0 0" > $CMD; fi
    log "cap=${cap}% temp=${t} -> $want (cmd now: $(cat $CMD))"
    state=$want
  fi
  setprop sys.synapse.charge "$state"
  sleep 60
done
