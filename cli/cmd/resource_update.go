package cmd

import (
	"encoding/json"
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	resourceUpdateName                 string
	resourceUpdatePort                 int32
	resourceUpdateCPU                  string
	resourceUpdateMemory               string
	resourceUpdateReplicas             int32
	resourceUpdateExpose               string
	resourceUpdateDomain               string
	resourceUpdateCommand              []string
	resourceUpdateArgs                 []string
	resourceUpdateClearCommandOverride bool
)

var resourceUpdateCmd = &cobra.Command{
	Use:   "update <slug>",
	Short: "Update a resource (JSON merge patch)",
	Long: `Update a resource's name or config fields. Only specified flags are sent.

To rewire the bound ImageSource (image ref / git repo / build mode), use the dedicated
ImageSource commands (deferred). For now this command edits the Resource side only.

Example:
  appbahn resource update my-app-abc1234 --name new-name
  appbahn resource update my-app-abc1234 --replicas 3 --memory 512Mi`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		req := api.NewUpdateResourceRequest()

		if cmd.Flags().Changed("name") {
			req.SetName(resourceUpdateName)
		}

		var config api.ResourceConfig
		hasConfig := false

		var hosting api.HostingConfig
		hasHosting := false
		if cmd.Flags().Changed("cpu") {
			hosting.Cpu = &resourceUpdateCPU
			hasHosting = true
		}
		if cmd.Flags().Changed("memory") {
			hosting.Memory = &resourceUpdateMemory
			hasHosting = true
		}
		if cmd.Flags().Changed("replicas") {
			hosting.MinReplicas = &resourceUpdateReplicas
			hasHosting = true
		}
		if hasHosting {
			config.Hosting = &hosting
			hasConfig = true
		}

		if cmd.Flags().Changed("port") || cmd.Flags().Changed("expose") || cmd.Flags().Changed("domain") {
			portCfg := api.PortConfig{}
			if cmd.Flags().Changed("port") {
				portCfg.Port = &resourceUpdatePort
			}
			if cmd.Flags().Changed("expose") {
				normalized, ok := normalizeExpose(resourceUpdateExpose)
				if !ok {
					return fmt.Errorf("invalid --expose %q: must be one of ingress, tcp, none", resourceUpdateExpose)
				}
				resourceUpdateExpose = normalized
				portCfg.Expose = &resourceUpdateExpose
			}
			if cmd.Flags().Changed("domain") {
				portCfg.Domain = &resourceUpdateDomain
			}
			networking := api.NetworkingConfig{Ports: []api.PortConfig{portCfg}}
			config.Networking = &networking
			hasConfig = true
		}

		if hasConfig {
			req.SetConfig(config)
		}

		commandFlagSet := cmd.Flags().Changed("command")
		argsFlagSet := cmd.Flags().Changed("args")
		clearOverrideFlagSet := cmd.Flags().Changed("clear-command-override")
		if clearOverrideFlagSet && (commandFlagSet || argsFlagSet) {
			return fmt.Errorf("--clear-command-override is mutually exclusive with --command/--args")
		}
		if commandFlagSet || argsFlagSet {
			override := api.NewCommandOverride()
			if commandFlagSet {
				override.SetCommand(resourceUpdateCommand)
			}
			if argsFlagSet {
				override.SetArgs(resourceUpdateArgs)
			}
			req.SetCommandOverride(*override)
		}
		if clearOverrideFlagSet && resourceUpdateClearCommandOverride {
			req.SetClearCommandOverride(true)
		}

		// Verify at least one field is being updated
		reqJSON, _ := json.Marshal(req)
		if string(reqJSON) == "{}" {
			return fmt.Errorf("no update flags specified; use --name, --port, --cpu, --memory, --replicas, --expose, --domain, --command, --args, or --clear-command-override")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		res, _, err := client.ResourcesAPI.UpdateResource(ctx, slug).UpdateResourceRequest(*req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"NAME", "SLUG", "TYPE", "STATUS", "ENVIRONMENT"}
		rows := [][]string{{
			res.GetName(),
			res.GetSlug(),
			res.GetType(),
			res.GetStatus(),
			res.GetEnvironmentSlug(),
		}}

		return output.Print(format, headers, rows, res)
	},
}

func init() {
	resourceUpdateCmd.Flags().StringVar(&resourceUpdateName, "name", "",
		"New name for the resource")
	resourceUpdateCmd.Flags().Int32Var(&resourceUpdatePort, "port", 0,
		"Container port")
	resourceUpdateCmd.Flags().StringVar(&resourceUpdateCPU, "cpu", "",
		"CPU limit in Kubernetes format (e.g. 250m, 1)")
	resourceUpdateCmd.Flags().StringVar(&resourceUpdateMemory, "memory", "",
		"Memory limit in Kubernetes format (e.g. 256Mi, 1Gi)")
	resourceUpdateCmd.Flags().Int32Var(&resourceUpdateReplicas, "replicas", 0,
		"Number of replicas")
	resourceUpdateCmd.Flags().StringVar(&resourceUpdateExpose, "expose", "",
		"Expose mode: ingress, tcp, or none")
	resourceUpdateCmd.Flags().StringVar(&resourceUpdateDomain, "domain", "",
		"Custom domain (requires expose=ingress)")
	resourceUpdateCmd.Flags().StringSliceVar(&resourceUpdateCommand, "command", nil,
		"Override the container's ENTRYPOINT (comma-separated, e.g. /bin/sh,-c)")
	resourceUpdateCmd.Flags().StringSliceVar(&resourceUpdateArgs, "args", nil,
		"Override the container's CMD args (comma-separated)")
	resourceUpdateCmd.Flags().BoolVar(&resourceUpdateClearCommandOverride, "clear-command-override", false,
		"Clear any existing command/args override and run the image's default ENTRYPOINT/CMD")
	resourceCmd.AddCommand(resourceUpdateCmd)
}
