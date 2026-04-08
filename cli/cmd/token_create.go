package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	tokenCreateEnv           string
	tokenCreateName          string
	tokenCreateRole          string
	tokenCreateExpiresInDays int32
)

var tokenCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new environment token",
	Long: `Create a new access token for an environment.

The generated token is shown only once — save it immediately.

Example:
  appbahn token create --env staging-abc1234 --name "CI token" --role EDITOR --expires-in-days 90`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if tokenCreateEnv == "" {
			return fmt.Errorf("--env is required")
		}
		if tokenCreateName == "" {
			return fmt.Errorf("--name is required")
		}
		if tokenCreateRole == "" {
			return fmt.Errorf("--role is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.CreateEnvironmentTokenRequest{
			Name: tokenCreateName,
			Role: tokenCreateRole,
		}
		if cmd.Flags().Changed("expires-in-days") {
			req.SetExpiresInDays(tokenCreateExpiresInDays)
		}

		resp, _, err := client.EnvironmentsAPI.CreateEnvironmentToken(ctx, tokenCreateEnv).
			CreateEnvironmentTokenRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		expiresAt := ""
		if resp.HasExpiresAt() {
			expiresAt = resp.GetExpiresAt().Format("2006-01-02 15:04:05")
		}

		headers := []string{"ID", "NAME", "TOKEN", "EXPIRES AT"}
		rows := [][]string{{resp.GetId(), resp.GetName(), resp.GetToken(), expiresAt}}

		return output.Print(format, headers, rows, resp)
	},
}

func init() {
	tokenCreateCmd.Flags().StringVar(&tokenCreateEnv, "env", "",
		"Environment slug")
	tokenCreateCmd.Flags().StringVar(&tokenCreateName, "name", "",
		"Name of the token")
	tokenCreateCmd.Flags().StringVar(&tokenCreateRole, "role", "",
		"Role for the token (EDITOR or VIEWER)")
	tokenCreateCmd.Flags().Int32Var(&tokenCreateExpiresInDays, "expires-in-days", 0,
		"Number of days until the token expires")
	_ = tokenCreateCmd.MarkFlagRequired("env")
	_ = tokenCreateCmd.MarkFlagRequired("name")
	_ = tokenCreateCmd.MarkFlagRequired("role")
	tokenCmd.AddCommand(tokenCreateCmd)
}
