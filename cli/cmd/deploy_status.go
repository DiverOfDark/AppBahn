package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var deployStatusCmd = &cobra.Command{
	Use:   "status <resource-slug> <deployment-id>",
	Short: "Get deployment status",
	Long: `Get the status and details of a specific deployment.

Example:
  appbahn deploy status my-app-abc1234 550e8400-e29b-41d4-a716-446655440000`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]
		deploymentId := args[1]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		dep, _, err := client.ResourcesAPI.GetDeployment(ctx, slug, deploymentId).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := deployFullHeaders
		rows := [][]string{deploymentToRow(*dep)}

		return output.Print(format, headers, rows, dep)
	},
}

func init() {
	deployCmd.AddCommand(deployStatusCmd)
}
