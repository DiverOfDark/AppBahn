package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceListEnv string
var resourceListPage int32
var resourceListSize int32

var resourceListCmd = &cobra.Command{
	Use:   "list",
	Short: "List resources in an environment",
	Long: `List all resources within an environment.

Example:
  appbahn resource list --env staging-abc1234
  appbahn resource list --env staging-abc1234 --page 1 --size 50`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := client.ResourcesAPI.ListResources(ctx).EnvironmentSlug(resourceListEnv)
		if cmd.Flags().Changed("page") {
			req = req.Page(resourceListPage)
		}
		if cmd.Flags().Changed("size") {
			req = req.Size(resourceListSize)
		}

		paged, _, err := req.Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		resources := paged.GetContent()

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "TYPE", "STATUS"}
		rows := make([][]string, 0, len(resources))
		for _, r := range resources {
			rows = append(rows, []string{
				r.GetName(),
				r.GetSlug(),
				r.GetType(),
				r.GetStatus(),
			})
		}

		return output.Print(format, headers, rows, resources)
	},
}

func init() {
	resourceListCmd.Flags().StringVar(&resourceListEnv, "env", "",
		"Environment slug (required)")
	resourceListCmd.MarkFlagRequired("env")
	resourceListCmd.Flags().Int32Var(&resourceListPage, "page", 0, "Page number (0-based)")
	resourceListCmd.Flags().Int32Var(&resourceListSize, "size", 20, "Page size")
	resourceCmd.AddCommand(resourceListCmd)
}
