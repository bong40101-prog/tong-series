from pathlib import Path
import base64, hashlib, io, zipfile, zlib
base = Path('TongSKY-1.0.40.apk').read_bytes()
assert hashlib.sha256(base).hexdigest() == 'e97e2e19954ebebe87790861819b5bb6f53c0a8571926b3a386b7364a0d08efe'
with zipfile.ZipFile(io.BytesIO(base)) as z:
    src = base + b''.join(z.read(n) for n in z.namelist() if not n.startswith('META-INF/'))
ops = zlib.decompress(base64.b64decode(Path('release-1041/apk.delta').read_bytes(), validate=True))
p = 0
out = bytearray()
def integer():
    global p
    val = 0
    for shift in range(0, 35, 7):
        b = ops[p]; p += 1; val |= (b & 127) << shift
        if b < 128: return val
    raise ValueError('Bad instruction')
while p < len(ops):
    code = integer(); n = code >> 1
    assert n > 0 and len(out) + n <= 3999275
    if code & 1:
        assert p+n <= len(ops); out.extend(ops[p:p+n]); p += n
    else:
        at = integer(); assert at+n <= len(src); out.extend(src[at:at+n])
assert len(out) == 3999275 and hashlib.sha256(out).hexdigest() == '18847120cb30a35f615b9a943a39a205ed8fd20700c376eb849337ab639ebdbc'
with zipfile.ZipFile(io.BytesIO(out)) as z: assert z.testzip() is None
Path('TongSKY-1.0.41.apk').write_bytes(out)
