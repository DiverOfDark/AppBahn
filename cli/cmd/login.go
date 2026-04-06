package cmd

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/diverofdark/appbahn/cli/internal/auth"
	"github.com/spf13/cobra"
)

var loginServer string

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Authenticate with an AppBahn server via OIDC",
	Long: `Opens your browser for OIDC authentication against the specified
AppBahn server. On success the access token, refresh token, and server
URL are stored in ~/.appbahn/config.json.

Example:
  appbahn login --server https://appbahn.acme.org`,
	RunE: func(cmd *cobra.Command, args []string) error {
		server := loginServer
		if server == "" {
			server = os.Getenv("APPBAHN_SERVER")
		}
		if server == "" {
			return fmt.Errorf("--server flag or APPBAHN_SERVER environment variable is required")
		}

		// Normalise: strip trailing slash.
		server = strings.TrimRight(server, "/")

		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()

		fmt.Printf("Authenticating with %s ...\n", server)

		result, err := auth.Login(ctx, server)
		if err != nil {
			return fmt.Errorf("login failed: %w", err)
		}

		cfg := &auth.Config{
			Server:       server,
			AccessToken:  result.AccessToken,
			RefreshToken: result.RefreshToken,
			ExpiresAt:    result.Expiry,
		}
		if err := auth.SaveConfig(cfg); err != nil {
			return fmt.Errorf("failed to save config: %w", err)
		}

		fmt.Println("Login successful! Credentials saved to ~/.appbahn/config.json")
		return nil
	},
}

func init() {
	loginCmd.Flags().StringVar(&loginServer, "server", "",
		"AppBahn server URL (e.g. https://appbahn.acme.org)")
	rootCmd.AddCommand(loginCmd)
}
