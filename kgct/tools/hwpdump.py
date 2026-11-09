import sys, zlib, struct, olefile
def recs(data):
    i=0
    while i+4<=len(data):
        h=struct.unpack_from('<I',data,i)[0]; i+=4
        tag=h&0x3ff; lvl=(h>>10)&0x3ff; size=h>>20
        if size==0xfff: size=struct.unpack_from('<I',data,i)[0]; i+=4
        yield tag,lvl,data[i:i+size]; i+=size
def ptext(b):
    out=[];i=0
    while i+1<len(b):
        c=struct.unpack_from('<H',b,i)[0]
        if c<32:
            if c in (0,10,13,24,25,26,27,28,29,30,31): 
                if c==10: out.append('\n')
                if c==30: out.append('-')
                if c==31: out.append(' ')
                i+=2
            elif c==9: out.append('\t'); i+=16
            else: i+=16
        else: out.append(chr(c)); i+=2
    return ''.join(out)
ole=olefile.OleFileIO(sys.argv[1])
hdr=ole.openstream('FileHeader').read(); comp=hdr[36]&1
secs=sorted([e for e in ole.listdir() if e[0]=='BodyText'],key=lambda e:int(e[1][7:]))
for s in secs:
    d=ole.openstream(s).read()
    if comp: d=zlib.decompress(d,-15)
    for tag,lvl,b in recs(d):
        if tag==71 and len(b)>=4:
            cid=b[:4][::-1].decode('latin1')
            if cid=='tbl ': print('  '*lvl+'[TABLE]')
        elif tag==72: print('  '*lvl+'[CELL]')
        elif tag==67:
            t=ptext(b).strip()
            if t: print('  '*lvl+t)
