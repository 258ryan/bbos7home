#!/usr/bin/env python3
"""Turn the placeholder attribute android:required in an overlay's compiled manifest into android:isStatic.

The public Android SDK jar has no android:isStatic, so aapt2 refuses to compile it. The framework's attribute ID for
isStatic is fixed (0x0101055a, see core/res/res/values/public.xml), and resource IDs are all Android looks at when it
reads a compiled manifest. So the overlay is compiled with <overlay android:required="true" .../> (0x0101028e) and this
script swaps that one ID in the manifest's resource map. 0x0101028e and 0x0101055a both sort after priority (0x0101001c)
and targetPackage (0x01010021), so the attributes stay in the ascending-ID order Android requires.

usage: patch_isstatic.py in.apk out.apk
"""
import struct
import sys
import zipfile

PLACEHOLDER = 0x0101028E   # android:required
IS_STATIC = 0x0101055A     # android:isStatic
RES_XML_RESOURCE_MAP_TYPE = 0x0180


def patch_manifest(data: bytes) -> bytes:
    buf = bytearray(data)
    if struct.unpack_from("<H", buf, 0)[0] != 0x0003:
        sys.exit("AndroidManifest.xml is not binary XML (was the APK linked with --proto-format?)")
    pos = struct.unpack_from("<H", buf, 2)[0]          # skip the file header
    patched = 0
    while pos + 8 <= len(buf):
        ctype, header, size = struct.unpack_from("<HHI", buf, pos)
        if size == 0:
            break
        if ctype == RES_XML_RESOURCE_MAP_TYPE:
            for off in range(pos + header, pos + size, 4):
                if struct.unpack_from("<I", buf, off)[0] == PLACEHOLDER:
                    struct.pack_into("<I", buf, off, IS_STATIC)
                    patched += 1
        pos += size
    if patched != 1:
        sys.exit(f"expected to patch exactly one attribute id, patched {patched}")
    return bytes(buf)


def main(src: str, dst: str) -> None:
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "AndroidManifest.xml":
                data = patch_manifest(data)
            zout.writestr(item, data, compress_type=item.compress_type)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
