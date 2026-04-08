package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var projectListWorkspace string

var projectListCmd = &cobra.Command{
	Use:   "list",
	Short: "List projects in a workspace",
	Long: `List all projects you have access to, optionally filtered by workspace.

Example:
  appbahn project list --workspace my-works-abc1234`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := client.ProjectsAPI.ListProjects(ctx)
		if projectListWorkspace != "" {
			req = req.WorkspaceSlug(projectListWorkspace)
		}

		paged, _, err := req.Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		projects := paged.GetContent()

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "WORKSPACE"}
		rows := make([][]string, 0, len(projects))
		for _, p := range projects {
			rows = append(rows, []string{
				p.GetName(),
				p.GetSlug(),
				p.GetWorkspaceSlug(),
			})
		}

		return output.Print(format, headers, rows, projects)
	},
}

func init() {
	projectListCmd.Flags().StringVar(&projectListWorkspace, "workspace", "",
		"Filter by workspace slug")
	projectCmd.AddCommand(projectListCmd)
}
