#!/usr/bin/env python3
"""
NovaPiperTTS dependency setup.

Downloads the latest official sherpa-onnx Android AAR and the current
shared espeak-ng-data used by Piper. The exact AAR version is written to
sherpa-onnx.version so Gradle automatically uses the downloaded version.

Run once after cloning:

    python scripts/setup.py

Then open Android Studio and build normally.
"""

from pathlib import Path
import hashlib
import json
import shutil
import ssl
import tarfile
import urllib.error
import urllib.request

GITHUB_API = "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/latest"
ESPEAK_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"

def sha256_file(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def download(url, target):
    part = target.with_suffix(target.suffix + ".part")
    part.unlink(missing_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "NovaPiperTTS-setup/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=120) as response, part.open("wb") as out:
            total = response.headers.get("Content-Length")
            total = int(total) if total else None
            done = 0
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                out.write(chunk)
                done += len(chunk)
                if total:
                    print(f"\r  {done * 100 // total:3d}%", end="", flush=True)
        print()
        part.replace(target)
    except Exception:
        part.unlink(missing_ok=True)
        raise

def get_latest_release():
    req = urllib.request.Request(
        GITHUB_API,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "NovaPiperTTS-setup/1.0",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        release = json.load(response)

    tag = release["tag_name"]
    version = tag.lstrip("v")
    filename = f"sherpa-onnx-{version}.aar"

    for asset in release["assets"]:
        if asset["name"] == filename:
            return version, filename, asset["browser_download_url"], asset.get("digest")

    raise RuntimeError(f"Android AAR {filename} not found in latest release {tag}")

def install_sherpa(root):
    libs = root / "app" / "libs"
    libs.mkdir(parents=True, exist_ok=True)

    version, filename, url, digest = get_latest_release()
    target = libs / filename
    version_file = root / "sherpa-onnx.version"

    print(f"[1/2] sherpa-onnx {version}")

    # Remove older AARs so Gradle cannot accidentally select the wrong one.
    for old in libs.glob("sherpa-onnx-*.aar"):
        if old != target:
            old.unlink()

    if target.exists():
        valid = True
        if digest:
            valid = ("sha256:" + sha256_file(target)).lower() == digest.lower()
        if valid:
            print(f"  Already installed: {filename}")
        else:
            print("  Existing AAR checksum is invalid; re-downloading.")
            target.unlink()

    if not target.exists():
        print(f"  Downloading: {url}")
        download(url, target)

        if digest:
            actual = "sha256:" + sha256_file(target)
            if actual.lower() != digest.lower():
                target.unlink(missing_ok=True)
                raise RuntimeError(f"Checksum mismatch: {actual} != {digest}")

    version_file.write_text(version + "\n", encoding="utf-8")
    print(f"  Gradle version set to {version}")

def install_espeak(root):
    assets = root / "app" / "src" / "main" / "assets"
    target = assets / "espeak-ng-data"
    archive = root / "scripts" / ".espeak-ng-data.tar.bz2"
    extract = assets / ".espeak_extract"

    print("[2/2] espeak-ng-data")
    assets.mkdir(parents=True, exist_ok=True)

    if (target / "phontab").is_file() and (target / "phondata").is_file():
        print("  Already installed.")
        return

    shutil.rmtree(target, ignore_errors=True)
    shutil.rmtree(extract, ignore_errors=True)

    print("  Downloading current phoneme data...")
    download(ESPEAK_URL, archive)

    try:
        extract.mkdir()
        with tarfile.open(archive, "r:bz2") as tar:
            tar.extractall(extract)

        candidates = []
        direct = extract / "espeak-ng-data"
        if direct.is_dir():
            candidates.append(direct)
        candidates += [p.parent for p in extract.rglob("phontab")]

        if not candidates:
            raise RuntimeError("espeak-ng-data was not found in downloaded archive.")

        candidates[0].rename(target)
    finally:
        archive.unlink(missing_ok=True)
        shutil.rmtree(extract, ignore_errors=True)

    if not (target / "phontab").is_file():
        raise RuntimeError("espeak-ng-data installation failed.")

    print("  Installed.")

def main():
    root = Path(__file__).resolve().parent.parent

    print("NovaPiperTTS setup")
    print("==================")
    print("This will download the latest sherpa-onnx and phoneme data.")
    print()

    try:
        install_sherpa(root)
        print()
        install_espeak(root)
        print()
        print("Setup complete!")
        print()
        print("You can now open the project in Android Studio and build it.")
        return 0
    except urllib.error.HTTPError as e:
        print(f"ERROR: HTTP {e.code}: {e.reason}")
    except urllib.error.URLError as e:
        print(f"ERROR: network error: {e.reason}")
    except ssl.SSLError as e:
        print(f"ERROR: TLS/SSL error: {e}")
    except KeyboardInterrupt:
        print("\nCancelled.")
    except Exception as e:
        print(f"ERROR: {e}")
    return 1

if __name__ == "__main__":
    raise SystemExit(main())
