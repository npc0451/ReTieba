"""Compare the same reader source's Debug and optimized APK; verify licenses/fonts survive shrinking."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def inspect(path):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, f"Invalid APK ZIP: {path}"
        entries = archive.infolist()
        licenses = {entry.filename: hashlib.sha256(archive.read(entry)).hexdigest()
                    for entry in entries if entry.filename.startswith("assets/licenses/") and not entry.is_dir()}
        fonts = sorted(hashlib.sha256(archive.read(entry)).hexdigest()
                       for entry in entries if entry.filename.endswith((".ttf", ".otf")))
        return {
            "file": str(path), "bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "compressed_dex_bytes": sum(entry.compress_size for entry in entries if entry.filename.endswith(".dex")),
            "licenses": licenses, "font_hashes": fonts,
            "font_entries": [entry.filename for entry in entries if entry.filename.endswith((".ttf", ".otf"))],
        }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("debug", type=Path)
    parser.add_argument("optimized", type=Path)
    args = parser.parse_args()
    debug, optimized = inspect(args.debug), inspect(args.optimized)
    assert debug["licenses"] and debug["licenses"] == optimized["licenses"], "License content changed"
    assert debug["font_hashes"] == optimized["font_hashes"], "Font content changed"
    font_dir = Path(__file__).resolve().parents[1] / "core/designsystem/src/main/res/font"
    for weight in ("regular", "medium"):
        digest = hashlib.sha256((font_dir / f"ibm_plex_sans_{weight}.ttf").read_bytes()).hexdigest()
        assert digest in debug["font_hashes"], f"Missing bundled Plex {weight}"
    assert optimized["bytes"] < debug["bytes"], "Optimized APK did not shrink"
    print(json.dumps({"debug": debug, "optimized": optimized,
                      "reduction_percent": round((1 - optimized["bytes"] / debug["bytes"]) * 100, 2)},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
