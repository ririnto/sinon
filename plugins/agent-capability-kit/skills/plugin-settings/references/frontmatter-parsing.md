---
name: frontmatter-parsing
description: |-
  Complete YAML frontmatter parsing patterns for `.claude/<plugin-name>.local.md` files, including all data types, edge cases, and validation.
---

# YAML Frontmatter Parsing: Complete Patterns and Edge Cases

This reference covers exhaustive YAML frontmatter parsing patterns for `.claude/<plugin-name>.local.md` files, including all data types, edge cases, malformed input handling, and validation.

## Frontmatter structure

Markdown file with YAML frontmatter between `---` delimiters:

```markdown
---
field1: value1
field2: 42
field3: true
field4:
  - item1
  - item2
field5: |-
  Multi-line
  string value
---

# Markdown content below frontmatter

This is body content.
```

## Extraction pattern (complete)

Extract raw frontmatter without parsing:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Extract frontmatter between --- delimiters.
#
# @param file_path Path to .local.md file.
# @return Outputs frontmatter lines; exits 1 if file absent or malformed.
extract_frontmatter() {
    file_path="$1"
    if [ ! -f "$file_path" ]; then
        return 1
    fi
    in_frontmatter=0
    line_num=0
    while IFS= read -r line; do
        line_num=$((line_num + 1))
        if [ "$line_num" -eq 1 ]; then
            if [ "$line" != "---" ]; then
                echo "Error: file does not start with ---" >&2
                return 1
            fi
            in_frontmatter=1
            continue
        fi
        if [ "$in_frontmatter" -eq 1 ] && [ "$line" = "---" ]; then
            break
        fi
        if [ "$in_frontmatter" -eq 1 ]; then
            echo "$line"
        fi
    done < "$file_path"
}
```

## Per-field type parsing

### Boolean fields

YAML boolean values: `true`, `false` (case-insensitive).

```bash

# Parse boolean field with strict type checking.
#
# @param frontmatter Frontmatter content as multi-line string.
# @param field_name Field name to extract.
# @return Outputs "true" or "false"; exits 1 if invalid.
parse_boolean() {
    frontmatter="$1"
    field_name="$2"
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//")
    if [ -z "$value" ]; then
        return 1
    fi
    case "$value" in
        true|True|TRUE|yes|Yes|YES|on|On|ON)
            echo "true"
            ;;
        false|False|FALSE|no|No|NO|off|Off|OFF)
            echo "false"
            ;;
        *)
            echo "Error: invalid boolean value for $field_name: $value" >&2
            return 1
            ;;
    esac
}
```

Usage:

```sh
# shellcheck disable=SC2034
FRONTMATTER=$(extract_frontmatter ".claude/plugin.local.md")
ENABLED=$(parse_boolean "$FRONTMATTER" "enabled")
if [ "$ENABLED" = "true" ]; then
    echo "Plugin enabled"
fi
```

### Numeric fields

Integer and floating-point numbers.

```bash

# Parse numeric field with range validation.
#
# @param frontmatter Frontmatter content.
# @param field_name Field name to extract.
# @param min_value Minimum allowed value.
# @param max_value Maximum allowed value.
# @return Outputs numeric value; exits 1 if invalid.
parse_numeric() {
    frontmatter="$1"
    field_name="$2"
    min_value="${3:-}"
    max_value="${4:-}"
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//")
    if [ -z "$value" ]; then
        return 1
    fi
    if ! printf '%s' "$value" | grep -qE '^-?[0-9]+(\.[0-9]+)?$'; then
        echo "Error: $field_name must be numeric, got: $value" >&2
        return 1
    fi
    if [ -n "$min_value" ]; then
        if echo "$value < $min_value" | bc -l | grep -qE '^1$'; then
            echo "Error: $field_name must be >= $min_value, got: $value" >&2
            return 1
        fi
    fi
    if [ -n "$max_value" ]; then
        if echo "$value > $max_value" | bc -l | grep -qE '^1$'; then
            echo "Error: $field_name must be <= $max_value, got: $value" >&2
            return 1
        fi
    fi
    echo "$value"
}
```

Usage:

```sh
# shellcheck disable=SC2034
RETRY_COUNT=$(parse_numeric "$FRONTMATTER" "max_retries" 1 100)
```

### String fields (unquoted)

Simple unquoted strings up to end of line.

```bash

# Parse unquoted string field.
#
# @param frontmatter Frontmatter content.
# @param field_name Field name to extract.
# @param default_value Default if field absent (optional).
# @return Outputs string value.
parse_string_unquoted() {
    frontmatter="$1"
    field_name="$2"
    default_value="${3:-}"
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//" | sed 's/ *$//')
    if [ -z "$value" ]; then
        if [ -n "$default_value" ]; then
            echo "$default_value"
        fi
        return 1
    fi
    echo "$value"
}
```

### String fields (quoted)

Quoted strings with escaped quotes.

```bash

# Parse quoted string field with quote unescaping.
#
# @param frontmatter Frontmatter content.
# @param field_name Field name to extract.
# @return Outputs unquoted string value.
parse_string_quoted() {
    frontmatter="$1"
    field_name="$2"
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//")
    if [ -z "$value" ]; then
        return 1
    fi
    if [ "${value#\"}" != "$value" ] && [ "${value%\"}" != "$value" ]; then
        value="${value%\"}"
        value="${value#\"}"
        value=$(echo "$value" | sed 's/\\"/"/g' | sed "s/\\\\'/'/g")
    fi
    echo "$value"
}
```

### Array fields (YAML list syntax)

YAML arrays in `[item1, item2]` syntax.

```bash

# Parse array field into bash array.
#
# @param frontmatter Frontmatter content.
# @param field_name Field name to extract.
# @return Outputs array items one per line.
parse_array() {
    frontmatter="$1"
    field_name="$2"
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//")
    if [ -z "$value" ]; then
        return 1
    fi
    if ! printf '%s' "$value" | grep -qE '^\[' || ! printf '%s' "$value" | grep -qE '\]$'; then
        echo "Error: $field_name must be array syntax [item1, item2]" >&2
        return 1
    fi
    value="${value%\]}"
    value="${value#\[}"
    echo "$value" | tr ',' '\n' | sed 's/^ *"//' | sed 's/"$ *//' | sed 's/^ *//' | sed 's/ *$//'
}
```

Usage:

```bash
readarray -t EXTENSIONS < <(parse_array "$FRONTMATTER" "allowed_extensions")
for ext in "${EXTENSIONS[@]}"; do
    echo "Allowed: $ext"
done
```

### Multi-line string fields

YAML literal (`|`) or folded (`>`) scalars spanning multiple lines.

```bash

# Extract multi-line string (literal or folded).
#
# @param frontmatter Frontmatter content.
# @param field_name Field name to extract.
# @return Outputs multi-line string.
parse_multiline() {
    frontmatter="$1"
    field_name="$2"
    in_multiline=0
    result=""
    while IFS= read -r line; do
        if printf '%s' "$line" | grep -qE "^${field_name}:\ *(\||>)" ; then
            in_multiline=1
            continue
        fi
        if [ "$in_multiline" -eq 1 ]; then
            if printf '%s' "$line" | grep -qE '^[a-zA-Z_]'; then
                break
            fi
            if [ -n "$line" ]; then
                result="$result$(echo "$line" | sed 's/^  //')"$'\n'
            fi
        fi
    done <<< "$frontmatter"
    printf '%s' "$result"
}
```

## Edge cases and malformed input

### Missing fields

Field not present in frontmatter:

```sh
# shellcheck disable=SC2034
OPTIONAL_FIELD=$(parse_string_unquoted "$FRONTMATTER" "optional_field" "default_value")
```

Provide default as third argument. If field absent, default is returned.

### Empty values

Field present but value is empty:

```yaml
---
empty_field:
---
```

Parsing:

```sh
VALUE=$(echo "$FRONTMATTER" | grep '^empty_field:' | sed 's/^empty_field: *//')
if [ -z "$VALUE" ]; then
    VALUE="default"
fi
```

### Malformed YAML (missing closing ---)

File:

```markdown
---
field1: value1
field2: value2

# No closing --- delimiter

Some content
```

Extraction will read until EOF instead of second `---`. Validate strictly:

```bash

# Validate frontmatter has proper delimiters.
#
# @param file_path Path to .local.md file.
# @return Exits 0 if valid; exits 1 if malformed.
validate_frontmatter() {
    file_path="$1"
    delimiter_count=$(grep -c "^---$" "$file_path" || true)
    if [ "$delimiter_count" -lt 2 ]; then
        echo "Error: frontmatter missing closing --- delimiter" >&2
        return 1
    fi
    return 0
}
```

### Duplicate fields

When same field appears twice:

```yaml
---
field: value1
field: value2
---
```

`grep` returns both lines. First match wins:

```sh
# shellcheck disable=SC2034
VALUE=$(echo "$FRONTMATTER" | grep "^field:" | head -1 | sed 's/^field: *//')
```

Use `head -1` to take first occurrence.

### Escaped quotes in strings

Quoted string with escaped quotes:

```yaml
---
message: "He said \"hello\""
---
```

Parsing:

```sh
# shellcheck disable=SC2034
MESSAGE=$(parse_string_quoted "$FRONTMATTER" "message")
# Result: He said "hello"
```

### Special characters in values

YAML requires quoting for special characters:

```yaml
---
path: "/home/user/my-folder"
regex: "^[a-z]+"
command: "ls -la"
---
```

Always quote values containing special characters. Parsing with `parse_string_quoted` handles escaping.

### Inconsistent indentation

YAML is whitespace-sensitive:

```yaml
---
field1: value1
  field2: value2
field3: value3
---
```

Indented field2 is treated as nested (incorrect for flat structure). Validate strict formatting:

```sh
grep "^[^ ]" "$FRONTMATTER"  # Lines starting with non-space (no indentation)
```

### Comments in frontmatter

YAML allows comments after `#`:

```yaml
---
enabled: true  # Master on/off switch
mode: strict   # Validation level
---
```

Parsing removes comments:

```sh
# shellcheck disable=SC2034
VALUE=$(echo "$FRONTMATTER" | grep "^mode:" | sed 's/ *#.*//' | sed 's/^mode: *//')
```

## Validation ranges and defaults

### Numeric range validation

```bash

# Parse and validate numeric field within range.
#
# @param frontmatter Frontmatter content.
# @param field_name Field name.
# @param min Minimum allowed value.
# @param max Maximum allowed value.
# @param default Default if absent or invalid.
# @return Outputs validated value.
parse_numeric_safe() {
    frontmatter="$1"
    field_name="$2"
    min="$3"
    max="$4"
    default="$5"
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//" || echo "")
    if [ -z "$value" ]; then
        echo "$default"
        return 0
    fi
    if ! printf '%s' "$value" | grep -qE '^[0-9]+$' || [ "$value" -lt "$min" ] || [ "$value" -gt "$max" ]; then
        echo "Warning: $field_name out of range [$min, $max], using default $default" >&2
        echo "$default"
        return 0
    fi
    echo "$value"
}
```

Usage:

```sh
# shellcheck disable=SC2034
RETRIES=$(parse_numeric_safe "$FRONTMATTER" "max_retries" 1 100 3)
```

### String enum validation

```bash

# Parse and validate string from allowed set.
#
# @param frontmatter Frontmatter content.
# @param field_name Field name.
# @param default Default value.
# @param allowed_values Space-separated list of allowed values.
# @return Outputs validated value or default.
parse_enum() {
    frontmatter="$1"
    field_name="$2"
    default="$3"
    shift 3
    allowed_values=("$@")
    value=$(echo "$frontmatter" | grep "^${field_name}:" | sed "s/^${field_name}: *//" || echo "")
    if [ -z "$value" ]; then
        echo "$default"
        return 0
    fi
    for allowed in "${allowed_values[@]}"; do
        if [ "$value" = "$allowed" ]; then
            echo "$value"
            return 0
        fi
    done
    echo "Warning: $field_name has invalid value '$value', using default '$default'" >&2
    echo "$default"
}
```

Usage:

```sh
# shellcheck disable=SC2034
LEVEL=$(parse_enum "$FRONTMATTER" "validation_level" "standard" "strict" "standard" "lenient")
```

### Default when file absent or invalid

```sh
# shellcheck disable=SC2034
if [ ! -f ".claude/plugin.local.md" ] || ! validate_frontmatter ".claude/plugin.local.md"; then
    ENABLED=true
    MODE="standard"
    MAX_RETRIES=3
else
    FRONTMATTER=$(extract_frontmatter ".claude/plugin.local.md")
    ENABLED=$(parse_boolean "$FRONTMATTER" "enabled")
    MODE=$(parse_string_unquoted "$FRONTMATTER" "mode" "standard")
    MAX_RETRIES=$(parse_numeric_safe "$FRONTMATTER" "max_retries" 1 100 3)
fi
```

## Complete example: full parsing workflow

Composite script using per-field parsing functions defined earlier:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

load_settings() {
    state_file="$1"
    if [ ! -f "$state_file" ]; then
        export ENABLED=true
        export MODE="standard"
        export MAX_RETRIES=3
        return 0
    fi
    frontmatter=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$state_file")
    export ENABLED
    ENABLED=$(parse_boolean "$frontmatter" "enabled" || echo "true")
    export MODE
    MODE=$(parse_enum "$frontmatter" "mode" "standard" "strict" "standard" "lenient")
    export MAX_RETRIES
    MAX_RETRIES=$(parse_numeric_safe "$frontmatter" "max_retries" 1 100 3)
}

load_settings ".claude/plugin.local.md"
echo "Enabled: $ENABLED, Mode: $MODE, Retries: $MAX_RETRIES"
```

Use parsing functions from Sections: Boolean, String enum validation, Numeric range validation.

## References

Refer to `SKILL.md` for common frontmatter keys and templates.

Refer to `references/multi-agent-coordination.md` for complex settings in multi-agent scenarios.
