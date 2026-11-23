#!/usr/bin/env python3
"""Extrae las notas de una versión desde CHANGELOG.md.

Busca, en este orden: la versión exacta (p. ej. 2.4.0-beta.1), su versión base
(2.4.0) y la sección [Unreleased]. Devuelve el cuerpo de la sección sin el título.

Uso:
  python3 scripts/release_notes.py 2.4.0-beta.1
"""
import re
import sys


def extract(version: str) -> str:
    with open("CHANGELOG.md", encoding="utf-8") as fh:
        text = fh.read()

    base = version.split("-", 1)[0]
    for candidate in (version, base, "Unreleased"):
        heading = re.compile(r"^##\s*\[?" + re.escape(candidate) + r"\]?.*$", re.MULTILINE)
        match = heading.search(text)
        if not match:
            continue
        rest = text[match.end():]
        next_heading = re.search(r"^##\s", rest, re.MULTILINE)
        body = rest[: next_heading.start()] if next_heading else rest
        body = body.strip()
        if body:
            return body
    return f"Medusa {version}"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: release_notes.py <versionName>")
    print(extract(sys.argv[1].removeprefix("v")))
