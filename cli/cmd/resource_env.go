package cmd

import (
	"fmt"
	"strings"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceEnvCmd = &cobra.Command{
	Use:   "env",
	Short: "Manage environment variables on a resource",
	Long:  `Set or unset env vars. Mutating env triggers a re-roll without rebuilding.`,
}

var resourceEnvSetCmd = &cobra.Command{
	Use:   "set <slug> KEY=VALUE [KEY=VALUE ...]",
	Short: "Set environment variables (re-rolls pods, no rebuild)",
	Long: `Patches spec.config.env with the provided KEY=VALUE pairs. The bound ImageSource
is untouched — the operator re-rolls the pod template with the new env, no rebuild
runs. A deployment audit row is minted with triggeredBy=env-change.

Example:
  appbahn resource env set my-app-abc1234 LOG_LEVEL=debug DATABASE_URL=postgres://...`,
	Args: cobra.MinimumNArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]
		newEnv := map[string]string{}
		for _, kv := range args[1:] {
			idx := strings.Index(kv, "=")
			if idx < 1 {
				return fmt.Errorf("invalid KEY=VALUE pair: %q", kv)
			}
			newEnv[kv[:idx]] = kv[idx+1:]
		}
		return patchEnv(cmd, slug, newEnv, nil)
	},
}

var resourceEnvUnsetCmd = &cobra.Command{
	Use:   "unset <slug> KEY [KEY ...]",
	Short: "Remove environment variables (re-rolls pods, no rebuild)",
	Long: `Removes the specified keys from spec.config.env. Triggers a re-roll without
rebuilding. A deployment audit row is minted with triggeredBy=env-change.

Example:
  appbahn resource env unset my-app-abc1234 LOG_LEVEL`,
	Args: cobra.MinimumNArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]
		keys := args[1:]
		return patchEnv(cmd, slug, nil, keys)
	},
}

// patchEnv merges the existing env with the requested set/unset and PATCHes the resource.
func patchEnv(cmd *cobra.Command, slug string, set map[string]string, unset []string) error {
	client, ctx, err := api.NewClient()
	if err != nil {
		return err
	}

	current, _, err := client.ResourcesAPI.GetResource(ctx, slug).Execute()
	if err != nil {
		return api.FormatAPIError(err)
	}
	merged := map[string]string{}
	if current.Config != nil && current.Config.Env != nil {
		for k, v := range *current.Config.Env {
			merged[k] = v
		}
	}
	for k, v := range set {
		merged[k] = v
	}
	for _, k := range unset {
		delete(merged, k)
	}

	req := api.NewUpdateResourceRequest()
	config := api.ResourceConfig{Env: &merged}
	req.SetConfig(config)

	res, _, err := client.ResourcesAPI.UpdateResource(ctx, slug).UpdateResourceRequest(*req).Execute()
	if err != nil {
		return api.FormatAPIError(err)
	}

	format, err := output.ParseFormat(OutputFormat())
	if err != nil {
		return err
	}
	headers := []string{"NAME", "SLUG", "STATUS", "ENV_KEYS"}
	keys := make([]string, 0, len(merged))
	for k := range merged {
		keys = append(keys, k)
	}
	rows := [][]string{{
		res.GetName(),
		res.GetSlug(),
		res.GetStatus(),
		strings.Join(keys, ","),
	}}
	return output.Print(format, headers, rows, res)
}

func init() {
	resourceEnvCmd.AddCommand(resourceEnvSetCmd)
	resourceEnvCmd.AddCommand(resourceEnvUnsetCmd)
	resourceCmd.AddCommand(resourceEnvCmd)
}
