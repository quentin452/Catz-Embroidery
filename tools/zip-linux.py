#!/usr/bin/env python3
"""Zip the packaged Linux binaries with the Unix executable bit set.

Windows Compress-Archive (and most Windows zip tools) drops the +x bit, so a
release zip extracted on Linux yields "permission denied (os error 13)" when
the launcher spawns a sibling exe. This zips the contents of a directory flat
(members at the zip root, no wrapping folder) and sets mode 0o755 on every
non-README entry (the ELF binaries), 0o644 on the rest — the shape the
launcher expects.

Usage:  python tools/zip-linux.py <src_dir> <out.zip>
        python tools/zip-linux.py --check <out.zip>   # verify the exec bit

--check exits non-zero when a non-README member lacks the 0o755 bit, so a
release pipeline can gate on it before publishing.
"""
import sys
import zipfile
from pathlib import Path


def check(out: Path) -> int:
    if not out.is_file():
        print(f"error: {out} is not a file", file=sys.stderr)
        return 2
    bad = []
    with zipfile.ZipFile(out) as zf:
        for info in zf.infolist():
            name = Path(info.filename).name
            if name == "README.txt":
                continue
            mode = (info.external_attr >> 16) & 0xFFFF
            if mode & 0o111 == 0:
                bad.append(f"{info.filename} mode {oct(mode)}")
    if bad:
        print("error: non-executable members:", ", ".join(bad), file=sys.stderr)
        return 1
    print(f"ok: {out} — all members executable")
    return 0


def main() -> None:
    if len(sys.argv) == 3 and sys.argv[1] == "--check":
        sys.exit(check(Path(sys.argv[2])))
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    src = Path(sys.argv[1])
    out = Path(sys.argv[2])
    if not src.is_dir():
        print(f"error: {src} is not a directory", file=sys.stderr)
        sys.exit(1)

    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(src.iterdir()):
            if not path.is_file():
                continue
            is_exe = path.name != "README.txt"
            mode = 0o755 if is_exe else 0o644
            info = zipfile.ZipInfo.from_file(path, path.name)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (mode & 0xFFFF) << 16
            with path.open("rb") as fh:
                zf.writestr(info, fh.read())
    print(f"wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
