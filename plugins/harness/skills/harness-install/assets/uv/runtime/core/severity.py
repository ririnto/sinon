#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Severity tier definitions.
"""

import sys


from typing import Literal

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

Severity = Literal["ERROR", "WARN", "INFO"]
