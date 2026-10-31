// Package main prints a greeting supplied by a sibling module.
package main

import (
	"fmt"
	"log"

	"example.com/workspace/greeting"
)

func main() {
	if _, err := fmt.Println(greeting.Message("workspace")); err != nil {
		log.Fatal(err)
	}
}
