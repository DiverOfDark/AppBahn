package cmd

import (
	"fmt"
	"regexp"
	"strings"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var nameRegex = regexp.MustCompile(`^[a-z][a-z0-9-]*$`)

var (
	resourceCreateName     string
	resourceCreateType     string
	resourceCreateEnv      string
	resourceCreateImage    string
	resourceCreatePort     int32
	resourceCreateCPU      string
	resourceCreateMemory   string
	resourceCreateReplicas int32
	resourceCreateExpose   string
	resourceCreateDomain   string
)

var resourceCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new resource",
	Long: `Create a new resource within an environment.

Example:
  appbahn resource create --name my-app --env staging-abc1234 --image nginx:latest --port 8080`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if !nameRegex.MatchString(resourceCreateName) {
			return fmt.Errorf("invalid --name %q: must start with a lowercase letter and contain only lowercase letters, digits, and hyphens", resourceCreateName)
		}

		if resourceCreateExpose != "" {
			validExposeValues := map[string]bool{"ingress": true, "tcp": true, "none": true}
			if !validExposeValues[resourceCreateExpose] {
				return fmt.Errorf("invalid --expose %q: must be one of ingress, tcp, none", resourceCreateExpose)
			}
		}

		if resourceCreateDomain != "" && resourceCreateExpose != "ingress" {
			return fmt.Errorf("custom domain requires --expose ingress")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		config := api.NewResourceConfig()

		if resourceCreateType == "deployment" {
			if resourceCreateImage == "" {
				return fmt.Errorf("--image is required for deployment resources")
			}

			if resourceCreatePort < 1 || resourceCreatePort > 65535 {
				return fmt.Errorf("port must be between 1 and 65535, got %d", resourceCreatePort)
			}

			hosting := api.NewHostingConfig()
			if resourceCreateCPU != "" {
				hosting.Cpu = &resourceCreateCPU
			}
			if resourceCreateMemory != "" {
				hosting.Memory = &resourceCreateMemory
			}
			if resourceCreateReplicas > 0 {
				hosting.MinReplicas = &resourceCreateReplicas
			}

			portConfig := api.NewPortConfig()
			portConfig.Port = &resourceCreatePort
			if resourceCreateExpose != "" {
				portConfig.Expose = &resourceCreateExpose
			}
			if resourceCreateDomain != "" {
				portConfig.Domain = &resourceCreateDomain
			}
			networking := api.NewNetworkingConfig()
			networking.Ports = []api.PortConfig{*portConfig}

			imageName := resourceCreateImage
			imageTag := "latest"
			if idx := strings.LastIndex(resourceCreateImage, ":"); idx != -1 {
				imageName = resourceCreateImage[:idx]
				imageTag = resourceCreateImage[idx+1:]
			}

			dockerSource := api.NewDockerSource("docker")
			dockerSource.Image = &imageName
			dockerSource.Tag = &imageTag
			source := api.DockerSourceAsSourceConfig(dockerSource)

			runMode := "CONTINUOUS"
			config.Source = &source
			config.Hosting = hosting
			config.Networking = networking
			config.RunMode = &runMode
		}

		req := api.CreateResourceRequest{
			Name:            resourceCreateName,
			Type:            resourceCreateType,
			EnvironmentSlug: resourceCreateEnv,
			Config:          *config,
		}

		res, _, err := client.ResourcesAPI.CreateResource(ctx).CreateResourceRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "ENVIRONMENT"}
		rows := [][]string{{res.GetSlug(), res.GetEnvironmentSlug()}}

		return output.Print(format, headers, rows, res)
	},
}

func init() {
	resourceCreateCmd.Flags().StringVar(&resourceCreateName, "name", "",
		"Name of the resource to create")
	resourceCreateCmd.Flags().StringVar(&resourceCreateType, "type", "deployment",
		"Resource type (default: deployment)")
	resourceCreateCmd.Flags().StringVar(&resourceCreateEnv, "env", "",
		"Environment slug to create the resource in")
	resourceCreateCmd.Flags().StringVar(&resourceCreateImage, "image", "",
		"Container image reference")
	resourceCreateCmd.Flags().Int32Var(&resourceCreatePort, "port", 80,
		"Container port (default: 80)")
	resourceCreateCmd.Flags().StringVar(&resourceCreateCPU, "cpu", "",
		"CPU limit in Kubernetes format (e.g. 250m, 1)")
	resourceCreateCmd.Flags().StringVar(&resourceCreateMemory, "memory", "",
		"Memory limit in Kubernetes format (e.g. 256Mi, 1Gi)")
	resourceCreateCmd.Flags().Int32Var(&resourceCreateReplicas, "replicas", 0,
		"Number of replicas")
	resourceCreateCmd.Flags().StringVar(&resourceCreateExpose, "expose", "",
		"Expose mode: ingress, tcp, or none")
	resourceCreateCmd.Flags().StringVar(&resourceCreateDomain, "domain", "",
		"Custom domain (requires --expose ingress)")
	resourceCreateCmd.MarkFlagRequired("name")
	resourceCreateCmd.MarkFlagRequired("env")
	resourceCmd.AddCommand(resourceCreateCmd)
}
