#!/usr/bin/env python3
"""Turn a scan-evidence bundle's diagnostics.txt into Kotlin OcrDocument source.

The geometry is the device's; nothing here invents or adjusts a box.
"""
import argparse
import io
import pathlib
import re
import sys

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


def emit_object(object_name, fixtures):
    lines = [
        "package app.justthecarbs.ocr",
        "",
        "/** Generated from complete physical-session diagnostics by tools/derive-session-fixtures.py. */",
        f"internal object {object_name} {{",
        "    private fun element(",
        "        text: String, left: Int, top: Int, right: Int, bottom: Int,",
        "        blockId: Int, lineId: Int,",
        "    ) = OcrElement(text, OcrBox(left, top, right, bottom), blockId, lineId)",
        "",
    ]
    for name, path in fixtures:
        width, height, declared, elements = parse(path)
        sys.stderr.write(
            f"{path}: {width}x{height} declared={declared} parsed={len(elements)}\n"
        )
        if declared != len(elements):
            raise ValueError(f"element count mismatch in {path}: {declared} != {len(elements)}")
        lines.append(f"    /** `{path.parent.name}` */")
        lines.append(emit(name, width, height, elements))
        lines.append("")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", nargs="?")
    parser.add_argument("function", nargs="?")
    parser.add_argument("--object")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument(
        "--fixture",
        action="append",
        default=[],
        metavar="FUNCTION=DIAGNOSTICS",
    )
    args = parser.parse_args()

    if args.object or args.output or args.fixture:
        if not args.object or not args.output or not args.fixture:
            parser.error("--object, --output and at least one --fixture are required together")
        fixtures = []
        for item in args.fixture:
            name, separator, raw_path = item.partition("=")
            if not separator or not name or not raw_path:
                parser.error(f"invalid --fixture '{item}'")
            fixtures.append((name, pathlib.Path(raw_path)))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(emit_object(args.object, fixtures), encoding="utf-8")
        return

    if not args.source or not args.function:
        parser.error("SOURCE and FUNCTION are required in single-fixture mode")
    src = pathlib.Path(args.source)
    width, height, declared, elements = parse(src)
    sys.stderr.write(
        f"{src}: {width}x{height} declared={declared} parsed={len(elements)}\n"
    )
    if declared != len(elements):
        sys.stderr.write("  !! element count mismatch — check the parser\n")
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    print(emit(args.function, width, height, elements))


if __name__ == "__main__":
    main()
