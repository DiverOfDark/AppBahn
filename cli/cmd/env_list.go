package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var envListProject string

var envListCmd = &cobra.Command{
	Use:   "list",
	Short: "List environments in a project",
	Long: `List all environments you have access to, optionally filtered by project.

Example:
  appbahn env list --project my-proje-abc1234`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := client.EnvironmentsAPI.ListEnvironments(ctx)
		if envListProject != "" {
			req = req.ProjectSlug(envListProject)
		}

		paged, _, err := req.Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		envs := paged.GetContent()

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "CLUSTER", "PROJECT"}
		rows := make([][]string, 0, len(envs))
		for _, e := range envs {
			rows = append(rows, []string{
				e.GetName(),
				e.GetSlug(),
				e.GetTargetCluster(),
				e.GetProjectSlug(),
			})
		}

		return output.Print(format, headers, rows, envs)
	},
}

func init() {
	envListCmd.Flags().StringVar(&envListProject, "project", "",
		"Filter by project slug")
	envCmd.AddCommand(envListCmd)
}
