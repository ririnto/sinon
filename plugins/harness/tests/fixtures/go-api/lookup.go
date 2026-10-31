// Package lookup maps a nonempty identifier to a display label.
package lookup

import "errors"

// ErrEmptyID indicates that a lookup cannot use an empty identifier.
var ErrEmptyID = errors.New("empty identifier")

const DefaultLabel = "label"

// Label returns the display label for id.
// It returns [ErrEmptyID] when id is empty.
func Label(id string) (string, error) {
	if id == "" {
		return "", ErrEmptyID
	}
	return "Label: " + id, nil
}
