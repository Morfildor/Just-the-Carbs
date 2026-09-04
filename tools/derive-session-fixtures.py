#!/usr/bin/env python3
"""Turn a scan-evidence bundle's diagnostics.txt into Kotlin OcrDocument source.

The geometry is the device's; nothing here invents or adjusts a box.
"""
import re
import sys
import pathlib

ELEMENT = re.compile(r"^\s*'(.*)' @ b(\d+)/l(\d+) \[(-?\d+),(-?\d+),(-?\d+),(-?\d+)\]\s*$")
IMAGE = re.compile(r"^image:\s*(\d+)x(\d+)\s+elements=(\d+)")


def parse(path):
    width = height = declared = None
    elements = []
    in_elements = False
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = IMAGE.match(raw)
        if m:
            width, height, declared = int(m.group(1)), int(m.group(2)), int(m.group(3))
            continue
        if raw.startswith("--- elements"):
            in_elements = True
            continue
        if in_elements:
            if raw.startswith("---") or raw.startswith("==="):
                in_elements = False
                continue
            m = ELEMENT.match(raw)
            if m:
                elements.append(
                    (
                        m.group(1),
                        int(m.group(2)),
                        int(m.group(3)),
                        int(m.group(4)),
                        int(m.group(5)),
                        int(m.group(6)),
                        int(m.group(7)),
                    )
                )
    return width, height, declared, elements


def kotlin_string(text):
    out = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + out + '"'


def emit(name, width, height, elements):
    lines = [f"    fun {name}(): OcrDocument = OcrDocument("]
    lines.append(f"        width = {width},")
    lines.append(f"        height = {height},")
    lines.append("        elements = listOf(")
    for text, block, line, l, t, r, b in elements:
        lines.append(
            f"            element({kotlin_string(text)}, {l}, {t}, {r}, {b}, {block}, {line}),"
        )
    lines.append("        ),")
    lines.append("    )")
    return "\n".join(lines)


if __name__ == "__main__":
    src = pathlib.Path(sys.argv[1])
    fn = sys.argv[2]
    width, height, declared, elements = parse(src)
    sys.stderr.write(
        f"{src}: {width}x{height} declared={declared} parsed={len(elements)}\n"
    )
    if declared != len(elements):
        sys.stderr.write("  !! element count mismatch — check the parser\n")
    # Written through a UTF-8 writer rather than `print`. Real labels are multilingual — Turkish
    # `yoğurt`, Latvian `ēdiens`, Lithuanian `ī`, Vietnamese-shaped OCR debris — and on Windows the
    # default stdout encoding is cp1252, which cannot represent any of them. Five of the thirteenth
    # session's nineteen bundles died on that, so the tool silently could not derive fixtures for
    # exactly the multilingual labels this parser most needs covered.
    sys.stdout.reconfigure(encoding="utf-8")
    print(emit(fn, width, height, elements))
