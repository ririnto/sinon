//! Look up example labels.

/// Return a label for a key.
///
/// Returns `None` when the key is absent. Keys use the same encoding as [`str`].
pub fn find_label(key: &str) -> Option<&str> {
    if key == "known" { Some("Known label") } else { None }
}

/// Helper retained to verify caller-supplied rustdoc flags.
fn private_helper() {}
