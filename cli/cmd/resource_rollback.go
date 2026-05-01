package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceRollbackTo string

var resourceRollbackCmd = &cobra.Command{
	Use:   "rollback <slug>",
	Short: "Roll back the resource to a previous deployment's image",
	Long: `Pins Resource.spec.pinnedRelease to the snapshot of the deployment audit row
identified by --to (or, when --to is omitted, the previous successful
deployment). The operator stops following the bound ImageSource and rolls the
resource onto the historical artifact — no rebuild runs. A new deployment
audit row is minted with triggeredBy=rollback.

Works for every ImageSource type, including type=git: rollback is a Resource-
level concern, not an ImageSource-level concern. To clear the pin and resume
following the ImageSource, use 'appbahn resource unpin'.

Examples:
  appbahn resource rollback my-app-abc1234
  appbahn resource rollback my-app-abc1234 --to 0193b7a5-1234-7000-8000-abcdef123456`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.RollbackRequest{}
		if resourceRollbackTo != "" {
			req.DeploymentId = &resourceRollbackTo
		}
		_, err = client.ResourcesAPI.RollbackResource(ctx, slug).RollbackRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "rollback-requested"}}
		result := map[string]string{"slug": slug, "status": "rollback-requested"}
		return output.Print(format, headers, rows, result)
	},
}

func init() {
	resourceRollbackCmd.Flags().StringVar(&resourceRollbackTo, "to", "", "deployment id to roll back to (UUID)")
	resourceCmd.AddCommand(resourceRollbackCmd)
}
