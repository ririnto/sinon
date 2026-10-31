"""Command-line entry point for the workspace greeting application."""

import sys

from greeting import greet


def main() -> None:
    """
    Print a greeting for the optional first command-line argument.
    """
    print(greet(sys.argv[1] if len(sys.argv) > 1 else ""))
