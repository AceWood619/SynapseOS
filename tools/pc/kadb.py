"""ADB over USB via libusb, signed with this PC's existing ADB key (~/.android/adbkey).

Why: the C8's USB entry (0E8D:201C) is bound to WinUSB (Zadig), so adb.exe can't see it over USB,
and adbd now requires auth, so uadb.py's rsa_keys=None fails with DeviceAuthError.
Needs: pip install adb-shell[usb] libusb1 libusb rsa

  python kadb.py shell "<cmd>" ["<cmd>" ...]
  python kadb.py push <local> <remote>
  python kadb.py pull <remote> <local>
  python kadb.py tcpip            # root adbd + listen on 5555 (so adb.exe can use Wi-Fi)
"""
import os, sys, time
from adb_shell.adb_device import AdbDeviceUsb
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

SERIAL = 'C8V250424000844'
KEY = os.path.join(os.path.expanduser('~'), '.android', 'adbkey')


def dev():
    with open(KEY) as f:
        priv = f.read()
    with open(KEY + '.pub') as f:
        pub = f.read()
    d = AdbDeviceUsb(serial=SERIAL, default_transport_timeout_s=30)
    d.connect(rsa_keys=[PythonRSASigner(pub, priv)], auth_timeout_s=30)
    return d


def main():
    a = sys.argv[1:]
    if not a:
        print(__doc__); return
    d = dev()
    try:
        if a[0] == 'shell':
            for c in a[1:]:
                print(d.shell(c, timeout_s=120, read_timeout_s=120))
        elif a[0] == 'push':
            d.push(a[1], a[2], read_timeout_s=300)
            print('pushed', os.path.getsize(a[1]), 'bytes')
        elif a[0] == 'pull':
            d.pull(a[1], a[2], read_timeout_s=300)
            print('pulled', os.path.getsize(a[2]), 'bytes')
        elif a[0] == 'tcpip':
            try:
                d.root()
            except Exception as e:
                print('root:', e)
            d.close(); time.sleep(6)
            d = dev()
            print(d.shell('id -u; setprop service.adb.tcp.port 5555; setprop ctl.restart adbd; echo restarting', timeout_s=20))
    finally:
        try: d.close()
        except Exception: pass


if __name__ == '__main__':
    main()
