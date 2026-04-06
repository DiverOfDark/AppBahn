package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var outputFormat string

var rootCmd = &cobra.Command{
	Use:   "appbahn",
	Short: "AppBahn CLI — manage your PaaS from the terminal",
	Long: `AppBahn CLI lets you interact with your AppBahn platform instance.

Configure the server with 'appbahn login --server <url>' or by setting
the APPBAHN_SERVER and APPBAHN_TOKEN environment variables.`,
	SilenceUsage:  true,
	SilenceErrors: true,
}

func init() {
	rootCmd.PersistentFlags().StringVarP(&outputFormat, "output", "o", "table",
		`Output format: table, json, yaml`)
}

// Execute runs the root command.
func Execute() error {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return err
	}
	return nil
}

// OutputFormat returns the current output format flag value.
func OutputFormat() string {
	return outputFormat
}

// GetServerURL returns the effective server URL, checking the environment
// variable first, then falling back to the stored config.
func GetServerURL() string {
	if v := os.Getenv("APPBAHN_SERVER"); v != "" {
		return v
	}
	return ""
}

// GetToken returns the effective token, checking the environment variable
// first, then falling back to the stored config.
func GetToken() string {
	if v := os.Getenv("APPBAHN_TOKEN"); v != "" {
		return v
	}
	return ""
}
