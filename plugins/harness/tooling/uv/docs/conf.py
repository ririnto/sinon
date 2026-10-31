"""Configure public Python API documentation."""

from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:
    import tomli as tomllib

project = tomllib.loads((Path(__file__).resolve().parent.parent / "pyproject.toml").read_text(encoding="utf-8"))["project"]["name"]
extensions = ["sphinx.ext.autodoc", "sphinx.ext.napoleon"]
nitpicky = True
