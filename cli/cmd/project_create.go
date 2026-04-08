package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	projectCreateName      string
	projectCreateWorkspace string
)

var projectCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new project",
	Long: `Create a new project within a workspace.

Example:
  appbahn project create --name "My Project" --workspace my-works-abc1234`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if projectCreateName == "" {
			return fmt.Errorf("--name is required")
		}
		if projectCreateWorkspace == "" {
			return fmt.Errorf("--workspace is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.CreateProjectRequest{
			Name:          projectCreateName,
			WorkspaceSlug: projectCreateWorkspace,
		}
		proj, _, err := client.ProjectsAPI.CreateProject(ctx).CreateProjectRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "WORKSPACE"}
		rows := [][]string{{proj.GetName(), proj.GetSlug(), proj.GetWorkspaceSlug()}}

		return output.Print(format, headers, rows, proj)
	},
}

func init() {
	projectCreateCmd.Flags().StringVar(&projectCreateName, "name", "",
		"Name of the project to create")
	projectCreateCmd.Flags().StringVar(&projectCreateWorkspace, "workspace", "",
		"Workspace slug to create the project in")
	projectCmd.AddCommand(projectCreateCmd)
}
