package cmd

import (
	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var resourceUnpinCmd = &cobra.Command{
	Use:   "unpin <slug>",
	Short: "Clear a resource's pinned release so it follows the ImageSource again",
	Long: `Clears Resource.spec.pinnedRelease. The resource immediately resumes following
the bound ImageSource's current latestArtifact — which may be newer than the
pin if builds ran while the resource was pinned. A new deployment audit row is
minted with triggeredBy=unpin.

Example:
  appbahn resource unpin my-app-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.ResourcesAPI.UnpinResource(ctx, slug).Execute()
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "unpin-requested"}}
		result := map[string]string{"slug": slug, "status": "unpin-requested"}
		return output.Print(format, headers, rows, result)
	},
}

func init() {
	resourceCmd.AddCommand(resourceUnpinCmd)
}
