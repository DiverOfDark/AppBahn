package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var deployRetryCmd = &cobra.Command{
	Use:   "retry <resource-slug> <deployment-id>",
	Short: "Re-deploy the same source as an existing deployment",
	Long: `Mint a new deployment row that re-uses the original source (the same commit hash
for git-backed resources, the same image digest for registry resources). The new
row goes through the standard build → rollout pipeline.

Retry always creates a new audit row even when the source deployment is still
active, so the audit trail has one row per user-initiated action.

Example:
  appbahn deploy retry my-app-abc1234 550e8400-e29b-41d4-a716-446655440000`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]
		deploymentId := args[1]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		fresh, _, err := client.ResourcesAPI.RetryDeployment(ctx, slug, deploymentId).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := deployFullHeaders
		rows := [][]string{deploymentToRow(*fresh)}
		return output.Print(format, headers, rows, fresh)
	},
}

func init() {
	deployCmd.AddCommand(deployRetryCmd)
}
