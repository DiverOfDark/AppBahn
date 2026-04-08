package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	envCreateName    string
	envCreateProject string
)

var envCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new environment",
	Long: `Create a new environment within a project.

Example:
  appbahn env create --name "staging" --project my-proje-abc1234`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if envCreateName == "" {
			return fmt.Errorf("--name is required")
		}
		if envCreateProject == "" {
			return fmt.Errorf("--project is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.CreateEnvironmentRequest{
			Name:        envCreateName,
			ProjectSlug: envCreateProject,
		}
		env, _, err := client.EnvironmentsAPI.CreateEnvironment(ctx).CreateEnvironmentRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "CLUSTER", "PROJECT"}
		rows := [][]string{{env.GetName(), env.GetSlug(), env.GetTargetCluster(), env.GetProjectSlug()}}

		return output.Print(format, headers, rows, env)
	},
}

func init() {
	envCreateCmd.Flags().StringVar(&envCreateName, "name", "",
		"Name of the environment to create")
	envCreateCmd.Flags().StringVar(&envCreateProject, "project", "",
		"Project slug to create the environment in")
	envCmd.AddCommand(envCreateCmd)
}
