package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
)

var (
	cliVersion = "dev"
	cliCommit  = "none"
)

// SetVersionInfo is called from main to inject build-time values.
func SetVersionInfo(version, commit string) {
	cliVersion = version
	cliCommit = commit
}

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print the CLI version",
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Printf("appbahn version %s (commit %s)\n", cliVersion, cliCommit)
	},
}

func init() {
	rootCmd.AddCommand(versionCmd)
}
