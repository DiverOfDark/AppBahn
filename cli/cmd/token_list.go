package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var tokenListEnv string

var tokenListCmd = &cobra.Command{
	Use:   "list",
	Short: "List environment tokens",
	Long: `List all access tokens for an environment.

Example:
  appbahn token list --env staging-abc1234`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if tokenListEnv == "" {
			return fmt.Errorf("--env is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		tokens, _, err := client.EnvironmentsAPI.ListEnvironmentTokens(ctx, tokenListEnv).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "ROLE", "EXPIRES AT", "LAST USED", "CREATED AT"}
		rows := make([][]string, 0, len(tokens))
		for _, t := range tokens {
			expiresAt := ""
			if t.HasExpiresAt() {
				expiresAt = t.GetExpiresAt().Format("2006-01-02 15:04:05")
			}
			lastUsed := ""
			if t.HasLastUsedAt() {
				lastUsed = t.GetLastUsedAt().Format("2006-01-02 15:04:05")
			}
			createdAt := ""
			if t.HasCreatedAt() {
				createdAt = t.GetCreatedAt().Format("2006-01-02 15:04:05")
			}
			rows = append(rows, []string{
				t.GetName(),
				t.GetRole(),
				expiresAt,
				lastUsed,
				createdAt,
			})
		}

		return output.Print(format, headers, rows, tokens)
	},
}

func init() {
	tokenListCmd.Flags().StringVar(&tokenListEnv, "env", "",
		"Environment slug")
	_ = tokenListCmd.MarkFlagRequired("env")
	tokenCmd.AddCommand(tokenListCmd)
}
