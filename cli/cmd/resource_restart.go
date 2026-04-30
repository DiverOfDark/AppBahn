package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceRestartCmd = &cobra.Command{
	Use:   "restart <slug>",
	Short: "Restart a resource (re-roll pods, no rebuild)",
	Long: `Bumps spec.restartGeneration so the operator forces a pod re-roll without
rebuilding the underlying image. A new deployment audit row is minted with
triggeredBy=manual-restart.

Example:
  appbahn resource restart my-app-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.ResourcesAPI.RestartResource(ctx, slug).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "restart-requested"}}
		result := map[string]string{"slug": slug, "status": "restart-requested"}
		return output.Print(format, headers, rows, result)
	},
}

func init() {
	resourceCmd.AddCommand(resourceRestartCmd)
}
