package main

import (
	"os"

	"github.com/diverofdark/appbahn/cli/cmd"
)

// version and commit are injected via ldflags at build time.
var (
	version = "dev"
	commit  = "none"
)

func main() {
	cmd.SetVersionInfo(version, commit)
	if err := cmd.Execute(); err != nil {
		os.Exit(1)
	}
}
