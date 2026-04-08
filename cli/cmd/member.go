package cmd

import (
	"github.com/spf13/cobra"
)

var memberCmd = &cobra.Command{
	Use:   "member",
	Short: "Manage workspace members",
	Long:  `Add, list, and manage workspace members.`,
}

func init() {
	rootCmd.AddCommand(memberCmd)
}
