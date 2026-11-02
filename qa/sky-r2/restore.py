"""Transfer an existing signed APK, not a signing tool. Run from the checkout root."""
from pathlib import Path
import base64, hashlib, io, json, struct, zipfile, zlib
BASE = 'e97e2e19954ebebe87790861819b5bb6f53c0a8571926b3a386b7364a0d08efe'
TARGET = '049b66eadb0776bd67c269f60b5bef8b66e60b745f108627303996104cb9e601'
raw = Path('TongSKY-1.0.40.apk').read_bytes()
assert hashlib.sha256(raw).hexdigest() == BASE
ops = json.loads(zlib.decompress(base64.b64decode(Path(__file__).with_name('transfer.b64').read_text())))
assert set(ops) == {'AndroidManifest.xml', 'classes.dex', 'assets/asiana.js', 'assets/repeat-marker-catalog.json', '@'}
def patch(data, instructions):
    return b''.join(base64.b64decode(op, validate=True) if isinstance(op, str) else data[op[0]:op[0]+op[1]] for op in instructions)
def offsets(archive):
    end = archive.rindex(b'PK\x05\x06')
    return end, struct.unpack_from('<I', archive, end+16)[0]
_, cd = offsets(raw)
assert raw[cd-16:cd] == b'APK Sig Block 42'
size = struct.unpack_from('<Q', raw, cd-24)[0]
sig = patch(raw[cd-size-8:cd], ops['@'])
buffer = io.BytesIO()
with zipfile.ZipFile(io.BytesIO(raw)) as original, zipfile.ZipFile(buffer, 'w') as out:
    for name in original.namelist():
        if name.startswith('META-INF/'):
            continue
        data = original.read(name)
        if name in ops:
            data = patch(data, ops[name])
        info = zipfile.ZipInfo(name, (2026,11,2,0,0,0))
        info.create_system = 0
        info.external_attr = 32
        info.compress_type = zipfile.ZIP_STORED
        pad = (-(out.fp.tell()+30+len(name.encode()))) % 4
        if pad:
            info.extra = struct.pack('<HH', 0xcafe, pad) + bytes(pad)
        out.writestr(info, data)
unsigned = buffer.getvalue()
end, cd = offsets(unsigned)
result = unsigned[:cd] + sig + unsigned[cd:end+16] + struct.pack('<I', cd+len(sig)) + unsigned[end+20:]
assert len(result) == 3998587 and hashlib.sha256(result).hexdigest() == TARGET
Path('TongSKY-1.0.40-r2.apk').write_bytes(result)
print(TARGET, 'TongSKY-1.0.40-r2.apk')
