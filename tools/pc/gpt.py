import struct,uuid
d=open(r'C:\gsiL\gpt.bin','rb').read()
for i in range(128):
    e=d[1024+i*128:1024+(i+1)*128]
    if e[:16]==b'\0'*16: continue
    u=uuid.UUID(bytes_le=e[16:32]); name=e[56:128].decode('utf-16le').rstrip('\0')
    if str(u).startswith('d26472f1') or name.startswith(('vbmeta','para','boot_para','seccfg','misc','proinfo')): print(i+1,name,u)
