package cmd

import (
	"github.com/spf13/cobra"
)

var resourceCmd = &cobra.Command{
	Use:     "resource",
	Aliases: []string{"res"},
	Short:   "Manage resources",
	Long:    `Create, list, get, delete, and manage AppBahn resources within environments.`,
}

func init() {
	rootCmd.AddCommand(resourceCmd)
}
