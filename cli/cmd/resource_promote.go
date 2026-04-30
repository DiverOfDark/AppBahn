package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourcePromoteDigest string

var resourcePromoteCmd = &cobra.Command{
	Use:   "promote <slug>",
	Short: "Promote the resource's bound ImageSource to a digest",
	Long: `Pins the bound ImageSource's pinnedDigest to either the supplied digest or
(when --digest is omitted) the upstream's current latestArtifact. The operator
applies the pin and rolls the resource onto the new digest.

Promotion only applies to type=imageSource and type=image ImageSources. For
type=git, revert your source commit instead.

Examples:
  appbahn resource promote my-app-abc1234
  appbahn resource promote my-app-abc1234 --digest sha256:cafebabe...`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		req := api.PromoteRequest{}
		if resourcePromoteDigest != "" {
			req.Digest = &resourcePromoteDigest
		}
		_, err = client.ResourcesAPI.PromoteResource(ctx, slug).PromoteRequest(req).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "promote-requested"}}
		result := map[string]string{"slug": slug, "status": "promote-requested"}
		return output.Print(format, headers, rows, result)
	},
}

func init() {
	resourcePromoteCmd.Flags().StringVar(&resourcePromoteDigest, "digest", "", "specific digest to pin (e.g. sha256:...)")
	resourceCmd.AddCommand(resourcePromoteCmd)
}
