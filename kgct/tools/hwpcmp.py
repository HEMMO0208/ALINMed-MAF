"""두 HWP 파일의 OLE 스트림 목록과 레코드(태그, 레벨, 크기, 내용)를 비교한다."""
import sys, zlib, struct, olefile, collections

TAGS = {16: "DOC_PROPS", 17: "ID_MAPPINGS", 18: "BIN_DATA", 19: "FACE_NAME", 20: "BORDER_FILL", 21: "CHAR_SHAPE",
        22: "TAB_DEF", 23: "NUMBERING", 24: "BULLET", 25: "PARA_SHAPE", 26: "STYLE", 66: "PARA_HEADER",
        67: "PARA_TEXT", 68: "PARA_CHAR_SHAPE", 69: "PARA_LINE_SEG", 70: "PARA_RANGE_TAG", 71: "CTRL_HEADER",
        72: "LIST_HEADER", 73: "PAGE_DEF", 74: "FOOTNOTE_SHAPE", 75: "PAGE_BORDER_FILL", 76: "SHAPE_COMPONENT",
        77: "TABLE", 85: "SHAPE_COMPONENT_PICTURE", 87: "CTRL_DATA", 88: "EQEDIT", 91: "FORM_OBJECT"}


def recs(d):
    i, out = 0, []
    while i + 4 <= len(d):
        h = struct.unpack_from('<I', d, i)[0]; i += 4
        tag, lvl, size = h & 0x3ff, (h >> 10) & 0x3ff, h >> 20
        if size == 0xfff: size = struct.unpack_from('<I', d, i)[0]; i += 4
        out.append((tag, lvl, d[i:i + size])); i += size
    return out


def load(path):
    o = olefile.OleFileIO(path)
    comp = o.openstream('FileHeader').read()[36] & 1
    streams = {}
    for e in o.listdir():
        name = '/'.join(e)
        raw = o.openstream(e).read()
        if comp and (e[0] in ('BodyText', 'DocInfo') or name == 'DocInfo'):
            try: raw = zlib.decompress(raw, -15)
            except Exception: pass
        streams[name] = raw
    return streams


a, b = load(sys.argv[1]), load(sys.argv[2])
print("streams only in A:", sorted(set(a) - set(b)))
print("streams only in B:", sorted(set(b) - set(a)))
for name in sorted(set(a) & set(b)):
    if name not in ('DocInfo',) and not name.startswith('BodyText'):
        same = a[name] == b[name]
        if not same: print(f"[{name}] bytes differ: {len(a[name])} vs {len(b[name])}")
        continue
    ra, rb = recs(a[name]), recs(b[name])
    ca = collections.Counter(t for t, _, _ in ra); cb = collections.Counter(t for t, _, _ in rb)
    print(f"[{name}] records {len(ra)} vs {len(rb)}")
    for t in sorted(set(ca) | set(cb)):
        if ca[t] != cb[t]: print(f"   count {TAGS.get(t, t)}: {ca[t]} vs {cb[t]}")
    nd = 0
    for k, (x, y) in enumerate(zip(ra, rb)):
        if x != y:
            nd += 1
            if nd <= int(sys.argv[3]) if len(sys.argv) > 3 else nd <= 8:
                print(f"   #{k} {TAGS.get(x[0], x[0])} lvl{x[1]} len{len(x[2])}  vs  {TAGS.get(y[0], y[0])} lvl{y[1]} len{len(y[2])}")
                if x[0] == y[0] and len(x[2]) == len(y[2]):
                    diffs = [i for i in range(len(x[2])) if x[2][i] != y[2][i]]
                    print(f"      byte diffs at {diffs[:12]}")
    print(f"   differing records: {nd}")
