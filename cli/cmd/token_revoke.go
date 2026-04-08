package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/spf13/cobra"
)

var (
	tokenRevokeEnv     string
	tokenRevokeTokenId string
)

var tokenRevokeCmd = &cobra.Command{
	Use:   "revoke",
	Short: "Revoke an environment token",
	Long: `Revoke (delete) an environment access token by its ID.

Example:
  appbahn token revoke --env staging-abc1234 --token-id 01234567-89ab-cdef-0123-456789abcdef`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if tokenRevokeEnv == "" {
			return fmt.Errorf("--env is required")
		}
		if tokenRevokeTokenId == "" {
			return fmt.Errorf("--token-id is required")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.EnvironmentsAPI.DeleteEnvironmentToken(ctx, tokenRevokeEnv, tokenRevokeTokenId).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		fmt.Printf("Token %q revoked.\n", tokenRevokeTokenId)
		return nil
	},
}

func init() {
	tokenRevokeCmd.Flags().StringVar(&tokenRevokeEnv, "env", "",
		"Environment slug")
	tokenRevokeCmd.Flags().StringVar(&tokenRevokeTokenId, "token-id", "",
		"ID of the token to revoke")
	_ = tokenRevokeCmd.MarkFlagRequired("env")
	_ = tokenRevokeCmd.MarkFlagRequired("token-id")
	tokenCmd.AddCommand(tokenRevokeCmd)
}
