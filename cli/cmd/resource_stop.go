package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceStopCmd = &cobra.Command{
	Use:   "stop <slug>",
	Short: "Stop a resource (drains pods, retains config)",
	Long: `Sets spec.stopped=true on the Resource — the operator drains the underlying
Deployment but keeps Service/Ingress/ConfigMap so subsequent start is fast.
No deployment audit row is minted.

Example:
  appbahn resource stop my-app-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.ResourcesAPI.StopResource(ctx, slug).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "stop-requested"}}
		result := map[string]string{"slug": slug, "status": "stop-requested"}
		return output.Print(format, headers, rows, result)
	},
}

var resourceStartCmd = &cobra.Command{
	Use:   "start <slug>",
	Short: "Start a stopped resource",
	Long: `Clears spec.stopped on the Resource — the operator recreates the
Deployment with the configured replica count. No deployment audit row is minted.

Example:
  appbahn resource start my-app-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.ResourcesAPI.StartResource(ctx, slug).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "start-requested"}}
		result := map[string]string{"slug": slug, "status": "start-requested"}
		return output.Print(format, headers, rows, result)
	},
}

func init() {
	resourceCmd.AddCommand(resourceStopCmd)
	resourceCmd.AddCommand(resourceStartCmd)
}
