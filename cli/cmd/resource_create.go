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
	resourceCreatePort     int32
	resourceCreateCPU      string
	resourceCreateMemory   string
	resourceCreateReplicas int32
	resourceCreateExpose   string
	resourceCreateDomain   string

	// ImageSource flags.
	resourceCreateImageSourceType string
	resourceCreateImageRef        string
	resourceCreateGitRepo         string
	resourceCreateGitBranch       string
	resourceCreateGitCredentials  string
	resourceCreateBuildMode       string
)

var resourceCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new resource",
	Long: `Create a new resource within an environment.

Examples:
  # Pin to a manually-specified image:
  appbahn resource create --name my-app --env staging-abc1234 \
    --image-source-type=image --image-ref=ghcr.io/acme/app:v1 --port 8080

  # Build from a git repo:
  appbahn resource create --name my-app --env staging-abc1234 \
    --image-source-type=git --git-repo=github.com/acme/backend --git-branch=main \
    --git-credentials-secret=github-creds --build-mode=peelbox --port 8080`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if !nameRegex.MatchString(resourceCreateName) {
			return fmt.Errorf("invalid --name %q: must start with a lowercase letter and contain only lowercase letters, digits, and hyphens", resourceCreateName)
		}

		if resourceCreateExpose != "" {
			normalized, ok := normalizeExpose(resourceCreateExpose)
			if !ok {
				return fmt.Errorf("invalid --expose %q: must be one of ingress, tcp, none", resourceCreateExpose)
			}
			resourceCreateExpose = normalized
		}

		if resourceCreateDomain != "" && resourceCreateExpose != "Ingress" {
			return fmt.Errorf("custom domain requires --expose ingress")
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		config := api.NewResourceConfig()

		if resourceCreateType == "deployment" {
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

			runMode := "Continuous"
			config.Hosting = hosting
			config.Networking = networking
			config.RunMode = &runMode
		}

		imageSource, err := buildImageSourceSpec()
		if err != nil {
			return err
		}

		req := api.CreateResourceRequest{
			Name:            resourceCreateName,
			Type:            resourceCreateType,
			EnvironmentSlug: resourceCreateEnv,
			Config:          *config,
			ImageSource:     *imageSource,
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

// PascalCase wire values match the API's @JsonProperty convention. CLI accepts any case
// from the user and normalizes here; lookups are case-insensitive against this set.
var imageSourceTypeWireValues = map[string]string{"image": "Image", "git": "Git"}
var buildModeWireValues = map[string]string{
	"dockerfile": "Dockerfile",
	"peelbox":    "Peelbox",
	"buildpack":  "Buildpack",
	"nixpacks":   "Nixpacks",
	"railpack":   "Railpack",
}
var exposeWireValues = map[string]string{"ingress": "Ingress", "tcp": "Tcp", "none": "None"}

func normalizeExpose(input string) (string, bool) {
	v, ok := exposeWireValues[strings.ToLower(strings.TrimSpace(input))]
	return v, ok
}

func buildImageSourceSpec() (*api.ImageSourceSpec, error) {
	rawType := strings.ToLower(strings.TrimSpace(resourceCreateImageSourceType))
	if rawType == "" {
		return nil, fmt.Errorf("--image-source-type is required (one of: git, image)")
	}
	spec := api.NewImageSourceSpec()
	switch rawType {
	case "image":
		if resourceCreateImageRef == "" {
			return nil, fmt.Errorf("--image-ref is required for --image-source-type=image")
		}
		typ := imageSourceTypeWireValues["image"]
		spec.Type = &typ
		image := api.NewImageSpec()
		image.Ref = &resourceCreateImageRef
		spec.Image = image
	case "git":
		if resourceCreateGitRepo == "" {
			return nil, fmt.Errorf("--git-repo is required for --image-source-type=git")
		}
		if resourceCreateGitBranch == "" {
			return nil, fmt.Errorf("--git-branch is required for --image-source-type=git")
		}
		typ := imageSourceTypeWireValues["git"]
		spec.Type = &typ
		git := api.NewImageSourceGitSpec()
		git.Repo = &resourceCreateGitRepo
		git.Branch = &resourceCreateGitBranch
		if resourceCreateGitCredentials != "" {
			git.CredentialsSecretRef = &resourceCreateGitCredentials
		}
		spec.Git = git
		modeIn := strings.ToLower(strings.TrimSpace(resourceCreateBuildMode))
		if modeIn == "" {
			modeIn = "peelbox"
		}
		mode, ok := buildModeWireValues[modeIn]
		if !ok {
			return nil, fmt.Errorf("invalid --build-mode %q (one of: dockerfile, peelbox, buildpack, nixpacks, railpack)", modeIn)
		}
		buildSpec := api.NewImageSourceBuildSpec()
		buildSpec.Mode = &mode
		spec.Build = buildSpec
	default:
		return nil, fmt.Errorf("invalid --image-source-type %q (one of: git, image)", rawType)
	}
	return spec, nil
}

func init() {
	resourceCreateCmd.Flags().StringVar(&resourceCreateName, "name", "",
		"Name of the resource to create")
	resourceCreateCmd.Flags().StringVar(&resourceCreateType, "type", "deployment",
		"Resource type (default: deployment)")
	resourceCreateCmd.Flags().StringVar(&resourceCreateEnv, "env", "",
		"Environment slug to create the resource in")
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

	resourceCreateCmd.Flags().StringVar(&resourceCreateImageSourceType, "image-source-type", "",
		"ImageSource type: git or image")
	resourceCreateCmd.Flags().StringVar(&resourceCreateImageRef, "image-ref", "",
		"Image reference for --image-source-type=image (e.g. ghcr.io/acme/app:v1)")
	resourceCreateCmd.Flags().StringVar(&resourceCreateGitRepo, "git-repo", "",
		"Git repository URL for --image-source-type=git")
	resourceCreateCmd.Flags().StringVar(&resourceCreateGitBranch, "git-branch", "",
		"Git branch for --image-source-type=git")
	resourceCreateCmd.Flags().StringVar(&resourceCreateGitCredentials, "git-credentials-secret", "",
		"K8s Secret name (kubernetes.io/basic-auth) holding git credentials")
	resourceCreateCmd.Flags().StringVar(&resourceCreateBuildMode, "build-mode", "peelbox",
		"Build mode for --image-source-type=git: dockerfile, peelbox, buildpack, nixpacks, railpack")

	_ = resourceCreateCmd.MarkFlagRequired("name")
	_ = resourceCreateCmd.MarkFlagRequired("env")
	_ = resourceCreateCmd.MarkFlagRequired("image-source-type")
	resourceCmd.AddCommand(resourceCreateCmd)
}
