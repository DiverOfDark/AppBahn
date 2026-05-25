package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var deployCancelCmd = &cobra.Command{
	Use:   "cancel <resource-slug> <deployment-id>",
	Short: "Cancel a queued or building deployment",
	Long: `Cancel an in-flight deployment. Only deployments in Queued or Building lifecycle
can be cancelled; past that point the rollout owns the row and you should use
rollback to revert to a previous release.

Example:
  appbahn deploy cancel my-app-abc1234 550e8400-e29b-41d4-a716-446655440000`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]
		deploymentId := args[1]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.ResourcesAPI.CancelDeployment(ctx, slug, deploymentId).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"DEPLOYMENT_ID", "STATUS"}
		rows := [][]string{{deploymentId, "cancel-requested"}}
		result := map[string]string{"deploymentId": deploymentId, "status": "cancel-requested"}
		return output.Print(format, headers, rows, result)
	},
}

func init() {
	deployCmd.AddCommand(deployCancelCmd)
}
