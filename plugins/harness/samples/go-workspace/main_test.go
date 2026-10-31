package main

import (
	"testing"

	"example.com/workspace/greeting"
)

// TestGreeting checks the sibling module's response.
func TestGreeting(t *testing.T) {
	if got, want := greeting.Message("workspace"), "Hello, workspace!"; got != want {
		t.Errorf("Message() = %q, want %q", got, want)
	}
}
