package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceGetCmd = &cobra.Command{
	Use:   "get <slug>",
	Short: "Get resource details",
	Long: `Get details of a resource by its slug.

Example:
  appbahn resource get my-app-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		res, _, err := client.ResourcesAPI.GetResource(ctx, slug).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "TYPE", "STATUS", "ENVIRONMENT"}
		rows := [][]string{{
			res.GetName(),
			res.GetSlug(),
			res.GetType(),
			res.GetStatus(),
			res.GetEnvironmentSlug(),
		}}

		return output.Print(format, headers, rows, res)
	},
}

func init() {
	resourceCmd.AddCommand(resourceGetCmd)
}
