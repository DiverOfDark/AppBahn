package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var deployTriggerSourceRef string

var deployTriggerCmd = &cobra.Command{
	Use:   "trigger <resource-slug>",
	Short: "Trigger a new deployment",
	Long: `Trigger a new deployment for a resource.

Example:
  appbahn deploy trigger my-app-abc1234
  appbahn deploy trigger my-app-abc1234 --source-ref main`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.TriggerDeploymentRequest{}
		if deployTriggerSourceRef != "" {
			req.SetSourceRef(deployTriggerSourceRef)
		}

		dep, _, err := client.ResourcesAPI.TriggerDeployment(ctx, slug).TriggerDeploymentRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"ID", "STATUS"}
		rows := [][]string{{
			dep.GetDeploymentId(),
			dep.GetStatus(),
		}}

		if err := output.Print(format, headers, rows, dep); err != nil {
			return err
		}

		if dep.GetStatus() == "DUPLICATE" {
			fmt.Fprintln(cmd.ErrOrStderr(), "Note: source unchanged — returning existing deployment.")
		}

		return nil
	},
}

func init() {
	deployTriggerCmd.Flags().StringVar(&deployTriggerSourceRef, "source-ref", "",
		"Source reference (branch, tag, or commit) to deploy")
	deployCmd.AddCommand(deployTriggerCmd)
}
