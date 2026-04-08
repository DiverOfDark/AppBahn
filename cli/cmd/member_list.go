package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var memberListWorkspace string

var memberListCmd = &cobra.Command{
	Use:   "list",
	Short: "List workspace members",
	Long: `List all members of a workspace.

Example:
  appbahn member list --workspace my-works-abc1234`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if memberListWorkspace == "" {
			return fmt.Errorf("--workspace is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		members, _, err := client.WorkspacesAPI.ListWorkspaceMembers(ctx, memberListWorkspace).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"EMAIL", "ROLE"}
		rows := make([][]string, 0, len(members))
		for _, m := range members {
			rows = append(rows, []string{
				m.GetEmail(),
				m.GetRole(),
			})
		}

		return output.Print(format, headers, rows, members)
	},
}

func init() {
	memberListCmd.Flags().StringVar(&memberListWorkspace, "workspace", "",
		"Workspace slug")
	_ = memberListCmd.MarkFlagRequired("workspace")
	memberCmd.AddCommand(memberListCmd)
}
