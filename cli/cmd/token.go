package cmd

import (
	"github.com/spf13/cobra"
)

var tokenCmd = &cobra.Command{
	Use:   "token",
	Short: "Manage environment tokens",
	Long:  `Create, list, and revoke environment access tokens.`,
}

func init() {
	rootCmd.AddCommand(tokenCmd)
}
