"""한글이 저장한 HWP 의 본문(Section0) 레코드만 직접 고친다. 나머지 레코드·스트림은 바이트 그대로 둔다.
hwplib 로 다시 쓰면 한글 최신판이 넣은 필드(구역 정의 끝, 그림 끝 바이트, 미리보기 스트림)가 빠지므로 이 방식을 쓴다.

  python3 hwp_raw_edit.py <in.hwp> <edits.json> <out_section0.bin>
  java -cp hwplib-1.1.11.jar ReplaceStream.java <in.hwp> BodyText/Section0 <out_section0.bin> <out.hwp>

edits.json: [{"old": 원문, "new": 새 문장, "prev": (선택) 바로 앞 문단 원문, "count": 바꿀 개수}
             {"after": 원문, "add": 덧붙일 문단(같은 모양), "count": 개수}]
조건: 대상 문단은 컨트롤 없는 일반 글자 문단, 글자 모양 1개. 'add' 대상은 칸의 마지막 문단.
"""
import sys, zlib, struct, json, olefile

PARA_HEADER, PARA_TEXT, PARA_CHAR_SHAPE, PARA_LINE_SEG, LIST_HEADER = 66, 67, 68, 69, 72


def recs(d):
    i, out = 0, []
    while i < len(d):
        h = struct.unpack_from('<I', d, i)[0]; i += 4
        tag, lvl, size = h & 0x3ff, (h >> 10) & 0x3ff, h >> 20
        if size == 0xfff: size = struct.unpack_from('<I', d, i)[0]; i += 4
        out.append([tag, lvl, bytearray(d[i:i + size])]); i += size
    return out


def pack(rs):
    b = bytearray()
    for tag, lvl, p in rs:
        if len(p) >= 0xfff: b += struct.pack('<II', tag | lvl << 10 | 0xfff << 20, len(p))
        else: b += struct.pack('<I', tag | lvl << 10 | len(p) << 20)
        b += p
    return bytes(b)


def paras(rs):
    """(시작 인덱스, 끝 인덱스(다음 레코드), 글자) — 글자는 PARA_TEXT 를 그대로 해석 (일반 글자 문단만 의미 있음)."""
    out = []
    for i, (tag, lvl, p) in enumerate(rs):
        if tag != PARA_HEADER: continue
        j = i + 1
        while j < len(rs) and rs[j][1] > lvl: j += 1
        txt = None
        if j > i + 1 and rs[i + 1][0] == PARA_TEXT:
            txt = bytes(rs[i + 1][2]).decode('utf-16le').rstrip('\r')
        out.append((i, j, txt))
    return out


def plain_para(rs, i, j, new):
    """문단 [i,j) 의 글자를 new 로 바꾼 레코드 묶음. 줄 배치(LINE_SEG)는 지워 한글이 다시 계산하게 한다."""
    tag, lvl, hdr = rs[i]
    kinds = [r[0] for r in rs[i + 1:j]]
    assert kinds in ([PARA_TEXT, PARA_CHAR_SHAPE, PARA_LINE_SEG], [PARA_TEXT, PARA_CHAR_SHAPE]), kinds
    t = rs[i + 1][2]
    assert all(c >= 32 or c == 13 for c in struct.unpack('<%dH' % (len(t) // 2), t)), '컨트롤 문자가 있는 문단'
    cs = rs[i + 2][2][:8]                                  # 첫 글자 모양만 (위치 0)
    assert struct.unpack_from('<I', cs, 0)[0] == 0
    h = bytearray(hdr)
    last = struct.unpack_from('<I', h, 0)[0] & 0x80000000
    text = (new + '\r').encode('utf-16le')
    struct.pack_into('<I', h, 0, last | (len(text) // 2))  # 글자 수
    struct.pack_into('<I', h, 4, 0)                        # control mask
    struct.pack_into('<H', h, 12, 1)                       # 글자 모양 수
    struct.pack_into('<H', h, 14, 0)                       # range tag 수
    struct.pack_into('<H', h, 16, 0)                       # line seg 수
    return [[PARA_HEADER, lvl, h], [PARA_TEXT, lvl + 1, bytearray(text)], [PARA_CHAR_SHAPE, lvl + 1, bytearray(cs)]]


def apply(rs, e):
    done = 0
    while True:
        ps = paras(rs)
        hit = None
        for k, (i, j, txt) in enumerate(ps):
            key = e.get('old', e.get('after'))
            if txt != key: continue
            if 'prev' in e and (k == 0 or ps[k - 1][2] != e['prev']): continue
            if e.get('_done_at') and i in e['_done_at']: continue
            hit = (k, i, j); break
        if hit is None: break
        k, i, j = hit
        if 'new' in e:
            rs[i:j] = plain_para(rs, i, j, e['new'])
            e.setdefault('_done_at', []).append(i)
        else:
            lvl = rs[i][1]
            assert struct.unpack_from('<I', rs[i][2], 0)[0] & 0x80000000, '칸의 마지막 문단이 아님'
            new = plain_para(rs, i, j, e['add'])
            old = plain_para(rs, i, j, e['after'])[:0]     # 검사만
            struct.pack_into('<I', rs[i][2], 0, struct.unpack_from('<I', rs[i][2], 0)[0] & 0x7fffffff)
            rs[j:j] = new
            # 이 칸의 LIST_HEADER 문단 수 +1
            h = i - 1
            while not (rs[h][0] == LIST_HEADER and rs[h][1] == lvl): h -= 1
            n = struct.unpack_from('<h', rs[h][2], 0)[0]
            struct.pack_into('<h', rs[h][2], 0, n + 1)
            e.setdefault('_done_at', []).append(j)          # 새로 넣은 문단은 다시 잡히지 않음(글자가 다름)
            e.setdefault('_done_at', []).append(i)
        done += 1
        if done == e.get('count', 1): break
    assert done == e.get('count', 1), (e.get('old', e.get('after')), done)
    print(done, 'x', (e.get('new') or '+ ' + e['add']))


def block_of(rs, t, lvl):
    """레코드 t 를 품은, 레벨 lvl 의 문단 블록 [i, j)."""
    i = t
    while not (rs[i][0] == PARA_HEADER and rs[i][1] == lvl): i -= 1
    j = i + 1
    while j < len(rs) and rs[j][1] > lvl: j += 1
    return i, j


def find_text(rs, text, start=0):
    for t in range(start, len(rs)):
        if rs[t][0] == PARA_TEXT and bytes(rs[t][2]).decode('utf-16le').rstrip('\r') == text: return t
    return None


def box(rs, e):
    """e['anchor'] 글자를 품은 칸-문단 블록 뒤에, e['template'] 글자를 품은 (표 하나짜리) 블록을 복제해 넣고
    복제본 안쪽 문단 글자를 e['text'] 로 바꾼다. 같은 칸의 LIST_HEADER 문단 수 +1. anchor 가 나올 때마다 반복."""
    lvl, done, pos = e['level'], 0, 0
    while True:
        a = find_text(rs, e['anchor'], pos)
        if a is None: break
        ai, aj = block_of(rs, a, lvl)
        tt = find_text(rs, e['template'], aj)
        ti, tj = block_of(rs, tt, lvl)
        blk = [[x[0], x[1], bytearray(x[2])] for x in rs[ti:tj]]
        hi = tt - 1 - ti                                     # 복제본 안에서 안쪽 문단 머리
        assert blk[hi][0] == PARA_HEADER
        k = hi + 1
        while k < len(blk) and blk[k][1] > blk[hi][1]: k += 1
        inner = plain_para(blk, hi, k, e['text'])
        blk[hi:k] = inner
        assert not struct.unpack_from('<I', rs[ai][2], 0)[0] & 0x80000000
        assert not struct.unpack_from('<I', blk[0][2], 0)[0] & 0x80000000
        rs[aj:aj] = blk
        h = ai - 1
        while not (rs[h][0] == LIST_HEADER and rs[h][1] == lvl): h -= 1
        struct.pack_into('<h', rs[h][2], 0, struct.unpack_from('<h', rs[h][2], 0)[0] + 1)
        done += 1; pos = aj + len(blk)
    assert done == e.get('count', 1), ('box', done)
    print(done, 'x box', e['text'])


def main(src, edits, out):
    o = olefile.OleFileIO(src)
    d = zlib.decompress(o.openstream('BodyText/Section0').read(), -15)
    rs = recs(d)
    assert pack(rs) == d
    for e in json.load(open(edits, encoding='utf-8')): box(rs, e) if 'anchor' in e else apply(rs, e)
    open(out, 'wb').write(pack(rs))


if __name__ == '__main__':
    main(*sys.argv[1:4])
