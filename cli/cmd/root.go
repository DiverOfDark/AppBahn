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
