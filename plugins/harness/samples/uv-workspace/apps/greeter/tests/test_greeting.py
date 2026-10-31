"""Tests for the shared greeting package."""

import unittest

from greeting import greet


class GreetingTest(unittest.TestCase):
    """Check names as consumed by the application."""

    def test_trimmed_name(self) -> None:
        """Whitespace around a name is ignored."""
        self.assertEqual(greet("  Ada  "), "Hello, Ada!")

    def test_blank_name(self) -> None:
        """A blank name receives the default greeting."""
        self.assertEqual(greet("  "), "Hello, world!")
