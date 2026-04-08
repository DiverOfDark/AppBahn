package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var workspaceListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all workspaces",
	Long:  `List all workspaces you have access to.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		paged, _, err := client.WorkspacesAPI.ListWorkspaces(ctx).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		workspaces := paged.GetContent()

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "CREATED"}
		rows := make([][]string, 0, len(workspaces))
		for _, ws := range workspaces {
			created := ""
			if ws.HasCreatedAt() {
				created = ws.GetCreatedAt().Format("2006-01-02 15:04:05")
			}
			rows = append(rows, []string{
				ws.GetName(),
				ws.GetSlug(),
				created,
			})
		}

		return output.Print(format, headers, rows, workspaces)
	},
}

func init() {
	workspaceCmd.AddCommand(workspaceListCmd)
}
