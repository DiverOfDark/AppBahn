package cmd

import (
	"github.com/spf13/cobra"
)

var deployFullHeaders = []string{"ID", "STATUS", "IMAGE", "TRIGGERED_BY", "PRIMARY", "CREATED"}

var deployCmd = &cobra.Command{
	Use:     "deploy",
	Aliases: []string{"deployment"},
	Short:   "Manage deployments",
	Long:    `Trigger, list, and inspect deployments for AppBahn resources.`,
}

func init() {
	rootCmd.AddCommand(deployCmd)
}
