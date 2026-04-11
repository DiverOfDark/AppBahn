package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var deployListPage int32
var deployListSize int32

var deployListCmd = &cobra.Command{
	Use:   "list <resource-slug>",
	Short: "List deployments for a resource",
	Long: `List all deployments for a given resource.

Example:
  appbahn deploy list my-app-abc1234
  appbahn deploy list my-app-abc1234 --page 1 --size 50`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := client.ResourcesAPI.ListDeployments(ctx, slug)
		if cmd.Flags().Changed("page") {
			req = req.Page(deployListPage)
		}
		if cmd.Flags().Changed("size") {
			req = req.Size(deployListSize)
		}

		paged, _, err := req.Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		deployments := paged.GetContent()

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := deployFullHeaders
		rows := make([][]string, 0, len(deployments))
		for _, d := range deployments {
			rows = append(rows, deploymentToRow(d))
		}

		return output.Print(format, headers, rows, deployments)
	},
}

func init() {
	deployListCmd.Flags().Int32Var(&deployListPage, "page", 0, "Page number (0-based)")
	deployListCmd.Flags().Int32Var(&deployListSize, "size", 20, "Page size")
	deployCmd.AddCommand(deployListCmd)
}
