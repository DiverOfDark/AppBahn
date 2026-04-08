package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/spf13/cobra"
)

var envDeleteCmd = &cobra.Command{
	Use:   "delete <slug>",
	Short: "Delete an environment",
	Long: `Delete an environment by its slug.

Example:
  appbahn env delete staging-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.EnvironmentsAPI.DeleteEnvironment(ctx, slug).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		fmt.Printf("Environment %q deleted.\n", slug)
		return nil
	},
}

func init() {
	envCmd.AddCommand(envDeleteCmd)
}
