package greeting

import "testing"

// TestMessage checks the greeting text.
func TestMessage(t *testing.T) {
	if got, want := Message("Go"), "Hello, Go!"; got != want {
		t.Errorf("Message() = %q, want %q", got, want)
	}
}
