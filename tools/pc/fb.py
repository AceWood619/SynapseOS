import sys, usb.core, usb.util, libusb, usb.backend.libusb1 as b
be=b.get_backend(find_library=lambda x: libusb.dll._name)
d=usb.core.find(idVendor=0x0e8d, idProduct=0x201c, backend=be)
d.set_configuration()
intf=d.get_active_configuration()[(0,0)]
ep_out=usb.util.find_descriptor(intf, custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress)==usb.util.ENDPOINT_OUT)
ep_in=usb.util.find_descriptor(intf, custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress)==usb.util.ENDPOINT_IN)
def cmd(c, timeout=30000):
    ep_out.write(c.encode()); out=[]
    while True:
        r=bytes(ep_in.read(512, timeout)).decode(errors='replace')
        if r.startswith('INFO'): out.append(r); continue
        out.append(r); return out
def flash(part, path):
    data=open(path,'rb').read()
    r=cmd('download:%08x'%len(data)); print(r)
    if not r[-1].startswith('DATA'): return
    for i in range(0,len(data),1<<20): ep_out.write(data[i:i+(1<<20)], 60000)
    while True:
        r=bytes(ep_in.read(512,60000)).decode(errors='replace'); print(r)
        if not r.startswith('INFO'): break
    print(cmd('flash:'+part, 60000))
a=sys.argv[1:]
if a[0]=='getvar': print(cmd('getvar:'+a[1]))
elif a[0]=='flash': flash(a[1],a[2])
elif a[0]=='reboot': print(cmd('reboot'))
