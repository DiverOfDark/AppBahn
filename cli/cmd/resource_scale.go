package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceScaleReplicas int32

var resourceScaleCmd = &cobra.Command{
	Use:   "scale <slug>",
	Short: "Scale a resource to N replicas (no audit row, no re-roll)",
	Long: `Patches spec.config.hosting.minReplicas. K8s scales the existing ReplicaSet
up or down — no pod re-roll, no deployment audit row.

Example:
  appbahn resource scale my-app-abc1234 --replicas 3`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]
		if !cmd.Flags().Changed("replicas") {
			return fmt.Errorf("--replicas is required")
		}
		if resourceScaleReplicas < 0 {
			return fmt.Errorf("--replicas must be >= 0")
		}

		req := api.NewUpdateResourceRequest()
		hosting := api.HostingConfig{}
		hosting.MinReplicas = &resourceScaleReplicas
		config := api.ResourceConfig{Hosting: &hosting}
		req.SetConfig(config)

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		res, _, err := client.ResourcesAPI.UpdateResource(ctx, slug).UpdateResourceRequest(*req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "TYPE", "STATUS", "REPLICAS"}
		rows := [][]string{{
			res.GetName(),
			res.GetSlug(),
			res.GetType(),
			res.GetStatus(),
			fmt.Sprintf("%d", resourceScaleReplicas),
		}}
		return output.Print(format, headers, rows, res)
	},
}

func init() {
	resourceScaleCmd.Flags().Int32Var(&resourceScaleReplicas, "replicas", 0, "Target replica count (>= 0)")
	resourceScaleCmd.MarkFlagRequired("replicas")
	resourceCmd.AddCommand(resourceScaleCmd)
}
