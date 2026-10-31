"""Greeting functions shared by workspace applications."""


def greet(name: str) -> str:
    """
    Return a greeting for a name, using a default for blank input.
    """
    return f"Hello, {name.strip() or 'world'}!"
