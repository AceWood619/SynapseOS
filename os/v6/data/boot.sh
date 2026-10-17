#!/system/bin/sh
# SynapseOS boot payload. Runs as root once per boot, ~10 s after boot_completed,
# from the v6 hook in /system/bin/phh-on-boot.sh. Lives in /data/adb/synapse/ (root-only, 0700).
# Keep this quick: long-running jobs are started in the background and moved out of the
# phh_on_boot service's cgroup (init kills that cgroup when the oneshot service exits).
D=/data/adb/synapse
exec >> $D/boot.log 2>&1
echo "=== boot $(date '+%F %T') uptime=$(cut -d' ' -f1 /proc/uptime)"
setprop sys.synapse.hook 1

# Trim logs (keep the last ~200 lines).
for f in $D/boot.log $D/chargectl.log; do
  [ -f "$f" ] && [ "$(wc -l < "$f")" -gt 400 ] && { tail -n 200 "$f" > "$f.tmp"; mv "$f.tmp" "$f"; }
done

# Move a PID out of this service's process group so init doesn't kill it.
escape_cgroup() {
  for base in /sys/fs/cgroup /acct; do
    if [ -d "$base/uid_0" ]; then
      mkdir -p "$base/uid_0/pid_synapse" 2>/dev/null
      echo "$1" > "$base/uid_0/pid_synapse/cgroup.procs" 2>/dev/null && echo "moved $1 to $base/uid_0/pid_synapse"
    fi
  done
}

# 1) ADB over Wi-Fi, WITH key authentication.
#    Only switched on when authorized keys exist, so this can never lock us out.
if [ -s $D/adb_keys ] && [ ! -f $D/disable_adb_wifi ]; then
  cp $D/adb_keys /data/misc/adb/adb_keys
  chown system:shell /data/misc/adb/adb_keys
  chmod 0640 /data/misc/adb/adb_keys
  restorecon /data/misc/adb/adb_keys 2>/dev/null
  resetprop_phh ro.adb.secure 1 && echo "adb auth required"
  setprop service.adb.tcp.port 5555
  setprop ctl.restart adbd
  echo "adb over wifi on :5555 (keys: $(wc -l < $D/adb_keys))"
fi

# 2) Charge limiter (keeps the battery between low/high % on 24/7 wall power).
if [ -f $D/chargectl.sh ] && [ ! -f $D/disable_chargectl ]; then
  if [ -f $D/chargectl.pid ] && kill -0 "$(cat $D/chargectl.pid)" 2>/dev/null; then
    echo "chargectl already running"
  else
    sh $D/chargectl.sh </dev/null >/dev/null 2>&1 &
    pid=$!
    echo $pid > $D/chargectl.pid
    escape_cgroup $pid
    echo "chargectl started pid=$pid"
  fi
fi

# 3) Optional site-specific extras (create $D/local.sh yourself; runs as root).
[ -f $D/local.sh ] && sh $D/local.sh

echo "=== boot payload done"
