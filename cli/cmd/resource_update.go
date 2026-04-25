package cmd

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	resourceUpdateName     string
	resourceUpdateImage    string
	resourceUpdatePort     int32
	resourceUpdateCPU      string
	resourceUpdateMemory   string
	resourceUpdateReplicas int32
	resourceUpdateExpose   string
	resourceUpdateDomain   string
)

var resourceUpdateCmd = &cobra.Command{
	Use:   "update <slug>",
	Short: "Update a resource (JSON merge patch)",
	Long: `Update a resource's name or config fields. Only specified flags are sent.

Example:
  appbahn resource update my-app-abc1234 --name new-name
  appbahn resource update my-app-abc1234 --image nginx:1.28 --replicas 3`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		req := api.NewUpdateResourceRequest()

		if cmd.Flags().Changed("name") {
			req.SetName(resourceUpdateName)
		}

		var config api.ResourceConfig
		hasConfig := false

		if cmd.Flags().Changed("image") {
			imageName := resourceUpdateImage
			imageTag := "latest"
			if idx := strings.LastIndex(resourceUpdateImage, ":"); idx != -1 {
				imageName = resourceUpdateImage[:idx]
				imageTag = resourceUpdateImage[idx+1:]
			}
			source := api.DockerSource{
				Type:  "docker",
				Image: &imageName,
				Tag:   &imageTag,
			}
			resourceSource := api.SourceConfig{DockerSource: &source}
			config.Source = &resourceSource
			hasConfig = true
		}

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
				validExposeValues := map[string]bool{"ingress": true, "tcp": true, "none": true}
				if !validExposeValues[resourceUpdateExpose] {
					return fmt.Errorf("invalid --expose %q: must be one of ingress, tcp, none", resourceUpdateExpose)
				}
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

		// Verify at least one field is being updated
		reqJSON, _ := json.Marshal(req)
		if string(reqJSON) == "{}" {
			return fmt.Errorf("no update flags specified; use --name, --image, --port, --cpu, --memory, --replicas, --expose, or --domain")
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
	resourceUpdateCmd.Flags().StringVar(&resourceUpdateImage, "image", "",
		"Container image reference")
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
	resourceCmd.AddCommand(resourceUpdateCmd)
}
