#!/usr/bin/env python3
"""Actualiza docs/api/version.json con los datos del build recién compilado.

Solo sobrescribe los campos que dependen del build (versionCode, versionName, apkUrl,
apkSha256). Los campos de CONTROL del responsable — minSupportedVersionCode, forceUpdate,
killSwitch, releaseNotes (si no se pasa OTA_RELEASE_NOTES) y signatures — se PRESERVAN.

Variables de entorno:
  VERSION_JSON       (def.) docs/api/version.json
  VERSION_CODE       (req.)
  VERSION_NAME       (req.)
  APK_URL            (req.)
  APK_SHA            (req.)
  OTA_RELEASE_NOTES  (opc.) si se pasa, sobreescribe releaseNotes
"""
import json
import os
import sys

path = os.environ.get("VERSION_JSON", "docs/api/version.json")

required = ("VERSION_CODE", "VERSION_NAME", "APK_URL", "APK_SHA")
missing = [k for k in required if not os.environ.get(k)]
if missing:
    sys.exit(f"Faltan variables: {', '.join(missing)}")

with open(path, encoding="utf-8") as fh:
    data = json.load(fh)

data["versionCode"] = int(os.environ["VERSION_CODE"])
data["versionName"] = os.environ["VERSION_NAME"]
data["apkUrl"] = os.environ["APK_URL"]
data["apkSha256"] = os.environ["APK_SHA"]

notes = os.environ.get("OTA_RELEASE_NOTES", "").strip()
if notes:
    data["releaseNotes"] = notes

# Campos de control: garantizar presencia sin pisarlos.
data.setdefault("minSupportedVersionCode", 0)
data.setdefault("forceUpdate", False)
data.setdefault("killSwitch", False)
data.setdefault("signatures", [])

with open(path, "w", encoding="utf-8") as fh:
    json.dump(data, fh, ensure_ascii=False, indent=2)
    fh.write("\n")

print(
    f"version.json → {data['versionName']} ({data['versionCode']}) | "
    f"killSwitch={data['killSwitch']} forceUpdate={data['forceUpdate']} "
    f"minSupported={data['minSupportedVersionCode']}"
)
