package cmd

import (
	"github.com/spf13/cobra"
)

var workspaceCmd = &cobra.Command{
	Use:     "workspace",
	Aliases: []string{"ws"},
	Short:   "Manage workspaces",
	Long:    `Create, list, and manage AppBahn workspaces.`,
}

func init() {
	rootCmd.AddCommand(workspaceCmd)
}
