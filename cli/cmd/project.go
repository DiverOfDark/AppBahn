package cmd

import (
	"github.com/spf13/cobra"
)

var projectCmd = &cobra.Command{
	Use:     "project",
	Aliases: []string{"proj"},
	Short:   "Manage projects",
	Long:    `Create, list, and manage AppBahn projects within workspaces.`,
}

func init() {
	rootCmd.AddCommand(projectCmd)
}
