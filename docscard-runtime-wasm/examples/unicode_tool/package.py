"""Compatibility entry point for the Unicode card's documented build command."""
from pathlib import Path
import runpy

if __name__ == "__main__":
    root = Path(__file__).resolve().parent
    runpy.run_path(str(root.parent / "package_card.py"))["build"](root)
