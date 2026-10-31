/// Builds a greeting, using a default name when the input is blank.
pub fn greet(name: &str) -> String {
    let name = name.trim();
    if name.is_empty() {
        "Hello, world!".to_owned()
    } else {
        format!("Hello, {name}!")
    }
}

#[cfg(test)]
mod tests {
    use super::greet;

    #[test]
    fn greets_a_trimmed_name() {
        assert_eq!(greet("  Ada  "), "Hello, Ada!");
    }

    #[test]
    fn uses_a_default_for_blank_names() {
        assert_eq!(greet("  "), "Hello, world!");
    }
}
