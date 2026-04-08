package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	memberAddWorkspace string
	memberAddEmail     string
	memberAddRole      string
)

var memberAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add a member to a workspace",
	Long: `Add a member to a workspace by email and role.

Example:
  appbahn member add --workspace my-works-abc1234 --email user@example.com --role EDITOR`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if memberAddWorkspace == "" {
			return fmt.Errorf("--workspace is required")
		}
		if memberAddEmail == "" {
			return fmt.Errorf("--email is required")
		}
		if memberAddRole == "" {
			return fmt.Errorf("--role is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.AddMemberRequest{
			Email: memberAddEmail,
			Role:  memberAddRole,
		}
		resp, _, err := client.WorkspacesAPI.AddWorkspaceMember(ctx, memberAddWorkspace).
			AddMemberRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"EMAIL", "ROLE", "STATUS"}
		rows := [][]string{{memberAddEmail, memberAddRole, resp.GetStatus()}}

		return output.Print(format, headers, rows, resp)
	},
}

func init() {
	memberAddCmd.Flags().StringVar(&memberAddWorkspace, "workspace", "",
		"Workspace slug")
	memberAddCmd.Flags().StringVar(&memberAddEmail, "email", "",
		"Email of the member to add")
	memberAddCmd.Flags().StringVar(&memberAddRole, "role", "",
		"Role for the member (OWNER, ADMIN, EDITOR, or VIEWER)")
	_ = memberAddCmd.MarkFlagRequired("workspace")
	_ = memberAddCmd.MarkFlagRequired("email")
	_ = memberAddCmd.MarkFlagRequired("role")
	memberCmd.AddCommand(memberAddCmd)
}
