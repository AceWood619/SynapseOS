import sys, time
from adb_shell.adb_device import AdbDeviceUsb
d = AdbDeviceUsb(serial='C8V250424000844'); d.connect(rsa_keys=None, auth_timeout_s=5)
try: d.root()
except Exception as e: print('root:', e)
d.close(); time.sleep(6)
d = AdbDeviceUsb(serial='C8V250424000844'); d.connect(rsa_keys=None, auth_timeout_s=5)
print(d.shell('id -u; setprop service.adb.tcp.port 5555; getprop service.adb.tcp.port', timeout_s=20))
try: d.root()
except Exception as e: print('restart:', e)
