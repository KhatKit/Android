"""Build the standalone card, not an APK asset. Requires Rust (rustup) and Python 3."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def notices():
    metadata = json.loads(subprocess.check_output(
        ["cargo", "metadata", "--format-version", "1", "--locked", "--filter-platform", "wasm32-unknown-unknown"],
        cwd=ROOT, encoding="utf-8",
    ))
    entries = []
    for pkg in sorted(metadata["packages"], key=lambda x: (x["name"], x["version"])):
        if not pkg["source"]:
            continue
        base = Path(pkg["manifest_path"]).parent
        files = sorted(p for p in [*base.glob("LICENSE*"), *base.glob("COPYING*"), *base.glob("data/LICENSE*")] if p.is_file())
        entries.append(f'{pkg["name"]} {pkg["version"]} ({pkg["license"]})')
        entries.extend(p.read_text(encoding="utf-8") for p in files)
    return "\n\n".join(entries).encode()


def main():
    result = subprocess.run(
        ["cargo", "build", "--release", "--locked", "--target", "wasm32-unknown-unknown", "--message-format=json"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    )
    artifacts = (json.loads(line) for line in result.stdout.splitlines())
    wasm = next(Path(p) for a in artifacts if a.get("reason") == "compiler-artifact"
                and a["target"]["name"] == "unicode_tool" for p in a["filenames"] if p.endswith(".wasm"))
    manifest = (ROOT / "manifest.json").read_text(encoding="utf-8").encode()
    path = ROOT / "unicode_tool.kkcard"
    with zipfile.ZipFile(path, "w") as package:
        for name, data in [("manifest.json", manifest), ("card.wasm", wasm.read_bytes()),
                           ("assets/THIRD-PARTY-NOTICES.txt", notices())]:
            entry = zipfile.ZipInfo(name)  # fixed timestamp and permissions, independent of checkout
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            package.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    print(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")


if __name__ == "__main__":
    main()
