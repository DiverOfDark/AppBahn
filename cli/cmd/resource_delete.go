package cmd

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

// formatDeleteError extracts the structured 409 ConflictException body and prints a
// human-readable message listing the dependent Resources. Falls back to FormatAPIError for
// any other error.
func formatDeleteError(err error) error {
	openAPIErr, ok := err.(*api.GenericOpenAPIError)
	if !ok {
		return api.FormatAPIError(err)
	}
	var body struct {
		Status  int      `json:"status"`
		Error   string   `json:"error"`
		Message string   `json:"message"`
		Details []string `json:"details"`
	}
	if jsonErr := json.Unmarshal(openAPIErr.Body(), &body); jsonErr != nil {
		return api.FormatAPIError(err)
	}
	if body.Status != 409 || (body.Error != "resource_has_dependents" && body.Error != "resource_has_downstream_image_sources") {
		return api.FormatAPIError(err)
	}
	var sb strings.Builder
	if body.Error == "resource_has_downstream_image_sources" {
		fmt.Fprintf(&sb, "Cannot delete; %d downstream Resource(s) depend on this:\n", len(body.Details))
	} else {
		fmt.Fprintf(&sb, "Cannot delete; %d dependent Resource(s) link to this:\n", len(body.Details))
	}
	for _, d := range body.Details {
		fmt.Fprintf(&sb, "  - %s\n", d)
	}
	sb.WriteString("Delete those first.")
	return fmt.Errorf("%s", sb.String())
}

var resourceDeleteYes bool

var resourceDeleteCmd = &cobra.Command{
	Use:   "delete <slug>",
	Short: "Delete a resource",
	Long: `Delete a resource by its slug.

Example:
  appbahn resource delete my-app-abc1234`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		if !resourceDeleteYes {
			stat, _ := os.Stdin.Stat()
			if (stat.Mode() & os.ModeCharDevice) == 0 {
				return fmt.Errorf("cannot confirm deletion in non-interactive mode; use --yes flag to skip confirmation")
			}
			fmt.Printf("Are you sure you want to delete resource %s? [y/N] ", slug)
			reader := bufio.NewReader(os.Stdin)
			answer, _ := reader.ReadString('\n')
			answer = strings.TrimSpace(answer)
			if answer != "y" && answer != "Y" {
				fmt.Println("Aborted.")
				return nil
			}
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		_, err = client.ResourcesAPI.DeleteResource(ctx, slug).Execute()
		if err != nil {
			return formatDeleteError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"SLUG", "STATUS"}
		rows := [][]string{{slug, "deleted"}}
		result := map[string]string{"slug": slug, "status": "deleted"}

		return output.Print(format, headers, rows, result)
	},
}

func init() {
	resourceDeleteCmd.Flags().BoolVarP(&resourceDeleteYes, "yes", "y", false, "Skip confirmation prompt")
	resourceCmd.AddCommand(resourceDeleteCmd)
}
