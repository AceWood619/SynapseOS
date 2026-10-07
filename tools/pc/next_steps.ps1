# SynapseOS: pick up where the PC chat (HANDS) stopped. Run on Mason's PC in PowerShell:
#   cd C:\SynapseOS; git pull origin main; powershell -ExecutionPolicy Bypass -File tools\pc\next_steps.ps1
# Safe to re-run. Saves a full log to C:\SynapseOS\results\ (no secrets in it) and tries to push it to GitHub
# so the research chat (BRAIN) can read the results.
param(
  [string]$Repo   = 'C:\SynapseOS',
  [string]$Adb    = 'C:\platform-tools\adb.exe',
  [string]$Serial = '10.0.0.166:5555',
  [string]$TokenFile = 'C:\secure\ha_token.txt',
  [string]$NodeId = 'livingroom-01',
  [string]$Room   = 'Living Room'
)
$ErrorActionPreference = 'Continue'
Set-Location $Repo
New-Item -ItemType Directory -Force "$Repo\results" | Out-Null
$stamp = Get-Date -f 'yyyyMMdd_HHmmss'
$log = "$Repo\results\run_$stamp.txt"
Start-Transcript -Path $log | Out-Null
function Step($t) { Write-Host "`n==== $t ====" -ForegroundColor Cyan }
function AdbSh($c) { & $Adb -s $Serial shell $c 2>&1 }

Step '0. Update repo, force Unix line endings for phone scripts'
git pull origin main
# Re-check-out only the phone-side scripts so .gitattributes gives them LF (doesn't touch other local work).
git ls-files '*.sh' '*.rc' | ForEach-Object { Remove-Item -Force $_ -ErrorAction SilentlyContinue }
git checkout -- .
git ls-files --eol os/v6/data/boot.sh

Step '1. HA token file'
if (-not (Test-Path $TokenFile)) {
  New-Item -ItemType Directory -Force (Split-Path $TokenFile) | Out-Null
  $sec = Read-Host -AsSecureString 'Paste the HA long-lived token (hidden)'
  $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
  Set-Content -NoNewline -Path $TokenFile -Value $plain.Trim()
  Remove-Variable plain
}
$token = (Get-Content $TokenFile -Raw).Trim()
Write-Host "token file OK ($($token.Length) chars)"

Step '2. Find Home Assistant (as seen from this PC)'
$ha = $null
foreach ($u in 'http://homeassistant.local:8123', 'http://10.0.0.7:8123') {
  try {
    $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 6 -Uri "$u/api/" -Headers @{ Authorization = "Bearer $token" }
    if ($r.StatusCode -eq 200) { $ha = $u; break }
  } catch { Write-Host "  $u -> $($_.Exception.Message)" }
}
if (-not $ha) { Write-Host 'FAIL: HA not reachable with this token. Check the token / HA address.' -ForegroundColor Red; Stop-Transcript; exit 1 }
Write-Host "HA OK at $ha (token accepted)"

Step '3. Phone over ADB (root)'
& $Adb connect $Serial
& $Adb -s $Serial root; Start-Sleep 4; & $Adb connect $Serial; Start-Sleep 2
$who = (AdbSh 'id -u') -join ''
Write-Host "adb uid = $who  model = $((AdbSh 'getprop ro.product.vendor.model') -join '')"

Step '4. R-006 charge limiter: quick check (3 samples, 1 min apart)'
python os\v6\install_payload.py --adb $Adb --serial $Serial --run-now
1..3 | ForEach-Object {
  $s = (AdbSh 'echo st=$(cat /sys/class/power_supply/battery/status) cur=$(cat /sys/class/power_supply/battery/current_now) cap=$(cat /sys/class/power_supply/battery/capacity) cmd=$(cat /proc/mtk_battery_cmd/current_cmd) lim=$(getprop sys.synapse.charge)') -join ''
  Write-Host "$(Get-Date -f HH:mm:ss) $s"
  if ($_ -lt 3) { Start-Sleep 60 }
}
Write-Host 'recovery test: limiter off -> must return to charging'
AdbSh 'setprop persist.synapse.charge_limit 0' | Out-Null; Start-Sleep 70
Write-Host ((AdbSh 'echo cmd=$(cat /proc/mtk_battery_cmd/current_cmd) st=$(cat /sys/class/power_supply/battery/status) lim=$(getprop sys.synapse.charge)') -join '')
AdbSh 'setprop persist.synapse.charge_limit 1' | Out-Null
AdbSh 'tail -n 8 /data/adb/synapse/chargectl.log'

Step '5. R-005 install + provision Synapse Core'
Get-Content builds\synapse-core-latest.json
# The phone resolves .local fine (verified earlier); fall back to the IP if the PC needed it.
$haPhone = $ha
python tools\provision\provision.py --adb $Adb --serial $Serial --node-id $NodeId --room $Room `
  --ha-url $haPhone --token-file $TokenFile --device-owner

Step '6. Smoke test (screen dim/wake, presence, HA entities, speech)'
$env:SYNAPSE_HA_TOKEN = $token
python tools\provision\smoke_test.py --node-id $NodeId --ip ($Serial.Split(':')[0]) --ha-url $ha --speak
Remove-Item Env:\SYNAPSE_HA_TOKEN

Step '7. App logs'
& $Adb -s $Serial logcat -d -s Synapse AndroidRuntime | Select-Object -Last 60

Step 'HUMAN CHECK (look at the phone)'
Write-Host '1) Dashboard visible, already logged in (no HA login page)?'
Write-Host '2) Did you hear "Synapse node online. This is a speaker test."?'
Write-Host '3) Wait 2 min without touching: does it dim to a clock? Then touch it / wave a hand over the top: does it wake?'
Stop-Transcript | Out-Null

# Hand the log to the research chat (no secrets in it).
if ((Select-String -Path $log -Pattern ([regex]::Escape($token)) -Quiet)) {
  Write-Host 'Log contains the token; NOT pushing it.' -ForegroundColor Red
} else {
  git add results\run_$stamp.txt
  git commit -q -m "PC run results $stamp"
  git push origin main
  if ($LASTEXITCODE -eq 0) { Write-Host "Results pushed. Tell the research chat: check the board." -ForegroundColor Green }
  else { Write-Host "Push failed. Paste $log into the research chat instead." -ForegroundColor Yellow }
}
