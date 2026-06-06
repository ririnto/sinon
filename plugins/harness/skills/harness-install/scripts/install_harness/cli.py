from __future__ import annotations

import argparse
import os
from typing import NoReturn
from pathlib import Path

from .errors import fail
from .installer import HarnessInstaller
from .models import CI_HOSTS, MODES, InstallerConfig


class HarnessArgumentParser(argparse.ArgumentParser):
    """Emit harness-specific CLI parser errors.

    The installer keeps argparse output consistent with the rest of the harness.
    """

    def error(self, message: str) -> NoReturn:

        if message == "argument --mode is required":
            message = "--mode is required (gradle|maven|uv|bun|shell)."
        elif message == "argument --ci-host is required":
            message = "--ci-host is required (github|gitlab|both|none)."
        fail(message, exit_code=2)


def parse_args(argv: list[str]) -> InstallerConfig:
    """Parse CLI arguments into installer configuration.

    The returned config is the single input to the installer runtime.
    """

    parser = HarnessArgumentParser(
        prog="install-harness.py",
        description="Install target-owned repository harness assets.",
    )
    parser.add_argument(
        "--mode", choices=MODES, required=True, help="Target stack mode."
    )
    parser.add_argument(
        "--ci-host", choices=CI_HOSTS, required=True, help="CI host to install."
    )
    parser.add_argument(
        "--target",
        default=os.environ.get("HARNESS_TARGET_ROOT", "."),
        help="Target repository root.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Overwrite managed target files where supported.",
    )
    parser.add_argument(
        "--no-ci", action="store_true", help="Alias for --ci-host none."
    )
    action_group = parser.add_mutually_exclusive_group()
    action_group.add_argument(
        "--preview",
        action="store_true",
        help="Print selected install set and statuses without writing.",
    )
    action_group.add_argument(
        "--show",
        metavar="PATH",
        help="Print rendered content for one final target-relative file without writing.",
    )
    action_group.add_argument(
        "--only", metavar="PATH", help="Install exactly one final target-relative file."
    )
    args = parser.parse_args(argv)
    ci_host = "none" if args.no_ci else args.ci_host
    if args.no_ci and args.ci_host != "none":
        fail("--no-ci cannot be combined with --ci-host other than none", exit_code=2)
    action = "install"
    selected_path: str | None = None
    if args.preview:
        action = "preview"
    elif args.show is not None:
        action = "show"
        selected_path = args.show
    elif args.only is not None:
        action = "only"
        selected_path = args.only
    return InstallerConfig(
        mode=args.mode,
        ci_host=ci_host,
        target_root=Path(args.target),
        force=args.force,
        action=action,
        selected_path=selected_path,
    )


def main(argv: list[str]) -> int:
    """Run the harness installer CLI.

    Returns zero after the selected installer action completes.
    """

    config = parse_args(argv)
    HarnessInstaller(config).run()
    return 0
