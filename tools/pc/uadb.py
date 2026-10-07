import sys, libusb, usb1
from adb_shell.adb_device import AdbDeviceUsb
d = AdbDeviceUsb(serial='C8V250424000844')
d.connect(rsa_keys=None, auth_timeout_s=5)
for c in sys.argv[1:]:
    print(d.shell(c, timeout_s=30))
d.close()
