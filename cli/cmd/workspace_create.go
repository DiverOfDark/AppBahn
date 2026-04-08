package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var workspaceCreateName string

var workspaceCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new workspace",
	Long: `Create a new workspace with the given name.

Example:
  appbahn workspace create --name "My Workspace"`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if workspaceCreateName == "" {
			return fmt.Errorf("--name is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.CreateWorkspaceRequest{Name: workspaceCreateName}
		ws, _, err := client.WorkspacesAPI.CreateWorkspace(ctx).CreateWorkspaceRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		created := ""
		if ws.HasCreatedAt() {
			created = ws.GetCreatedAt().Format("2006-01-02 15:04:05")
		}

		headers := []string{"NAME", "SLUG", "CREATED"}
		rows := [][]string{{ws.GetName(), ws.GetSlug(), created}}

		return output.Print(format, headers, rows, ws)
	},
}

func init() {
	workspaceCreateCmd.Flags().StringVar(&workspaceCreateName, "name", "",
		"Name of the workspace to create")
	workspaceCmd.AddCommand(workspaceCreateCmd)
}
