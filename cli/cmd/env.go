package cmd

import (
	"github.com/spf13/cobra"
)

var envCmd = &cobra.Command{
	Use:     "env",
	Aliases: []string{"environment"},
	Short:   "Manage environments",
	Long:    `Create, list, delete, and manage AppBahn environments within projects.`,
}

func init() {
	rootCmd.AddCommand(envCmd)
}
