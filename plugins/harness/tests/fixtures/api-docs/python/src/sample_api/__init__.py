"""Look up example labels."""


def find_label(key: str) -> str | None:
    """Return the label for a known key.

    :param key: The key to look up.
    :returns: The matching label, or None when the key is absent.
    """
    return "Known label" if key == "known" else None
