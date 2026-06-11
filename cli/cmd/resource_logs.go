package cmd

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/auth"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

var (
	resourceLogsSince        string
	resourceLogsDeploymentID string
	resourceLogsPod          string
	resourceLogsContainer    string
	resourceLogsLines        int32
	resourceLogsFollow       bool
)

var resourceLogsCmd = &cobra.Command{
	Use:   "logs <slug>",
	Short: "Show logs for a resource",
	Long: `Print a snapshot of recent log lines for a resource, or follow the live
stream with --follow.

The snapshot honours --since, --deployment-id, --pod, --container and --lines.
--follow streams new lines as they arrive (Ctrl-C to stop); it is incompatible
with --deployment-id and --lines.

Examples:
  appbahn resource logs my-app-abc1234
  appbahn resource logs my-app-abc1234 --since 1h --lines 200
  appbahn resource logs my-app-abc1234 --deployment-id 0190f2c1-...
  appbahn resource logs my-app-abc1234 --follow`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		var since *time.Time
		if resourceLogsSince != "" {
			t, err := parseSince(resourceLogsSince)
			if err != nil {
				return err
			}
			since = &t
		}

		if resourceLogsFollow {
			if resourceLogsDeploymentID != "" {
				return fmt.Errorf("--follow cannot be combined with --deployment-id")
			}
			return followLogs(cmd.Context(), slug, since)
		}

		return snapshotLogs(slug, since)
	},
}

func snapshotLogs(slug string, since *time.Time) error {
	client, ctx, err := api.NewClient()
	if err != nil {
		return err
	}

	req := client.ResourcesAPI.GetResourceLogs(ctx, slug)
	if since != nil {
		req = req.Since(*since)
	}
	if resourceLogsDeploymentID != "" {
		req = req.DeploymentId(resourceLogsDeploymentID)
	}
	if resourceLogsPod != "" {
		req = req.Pod(resourceLogsPod)
	}
	if resourceLogsContainer != "" {
		req = req.Container(resourceLogsContainer)
	}
	if resourceLogsLines > 0 {
		req = req.Lines(resourceLogsLines)
	}

	res, _, err := req.Execute()
	if err != nil {
		return api.FormatAPIError(err)
	}

	format, err := output.ParseFormat(OutputFormat())
	if err != nil {
		return err
	}

	lines := res.GetLines()
	headers := []string{"TIMESTAMP", "POD", "CONTAINER", "MESSAGE"}
	rows := make([][]string, 0, len(lines))
	for _, l := range lines {
		rows = append(rows, []string{
			formatLogTimestamp(l.Timestamp),
			l.GetPod(),
			l.GetContainer(),
			l.GetMessage(),
		})
	}
	return output.Print(format, headers, rows, lines)
}

// logStreamFrame mirrors the SSE `log` frame emitted by
// GET /resources/{slug}/logs/stream. The generated client returns an opaque
// SseEmitter, so --follow reads the raw event stream directly.
type logStreamFrame struct {
	Timestamp *time.Time `json:"timestamp"`
	Message   string     `json:"message"`
	Pod       string     `json:"pod"`
	Container string     `json:"container"`
}

func followLogs(ctx context.Context, slug string, since *time.Time) error {
	serverURL := auth.ServerURL()
	token := auth.Token()
	if serverURL == "" || token == "" {
		return fmt.Errorf("not logged in: run 'appbahn login --server <url>' first")
	}

	q := url.Values{}
	q.Set("types", "log")
	if since != nil {
		q.Set("since", since.UTC().Format(time.RFC3339))
	}
	if resourceLogsPod != "" {
		q.Set("pod", resourceLogsPod)
	}
	if resourceLogsContainer != "" {
		q.Set("container", resourceLogsContainer)
	}

	endpoint := fmt.Sprintf("%s/api/v1/resources/%s/logs/stream?%s",
		strings.TrimRight(serverURL, "/"), url.PathEscape(slug), q.Encode())

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "text/event-stream")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("log stream returned HTTP %d", resp.StatusCode)
	}

	return consumeLogStream(resp.Body)
}

// consumeLogStream parses an SSE byte stream line-by-line, printing the payload
// of every `log` event. Other event types (k8s_event, keepalive) are ignored.
func consumeLogStream(body io.Reader) error {
	scanner := bufio.NewScanner(body)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)

	var event, data string
	flush := func() error {
		if event == "log" && data != "" {
			var frame logStreamFrame
			if err := json.Unmarshal([]byte(data), &frame); err == nil {
				fmt.Printf("%s  %s  %s  %s\n",
					formatLogTimestamp(frame.Timestamp), frame.Pod, frame.Container, frame.Message)
			}
		}
		event, data = "", ""
		return nil
	}

	for scanner.Scan() {
		line := scanner.Text()
		switch {
		case line == "":
			if err := flush(); err != nil {
				return err
			}
		case strings.HasPrefix(line, "event:"):
			event = strings.TrimSpace(strings.TrimPrefix(line, "event:"))
		case strings.HasPrefix(line, "data:"):
			payload := strings.TrimPrefix(strings.TrimPrefix(line, "data:"), " ")
			if data == "" {
				data = payload
			} else {
				data += "\n" + payload
			}
		}
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	return flush()
}

// parseSince accepts either an RFC3339 timestamp or a Go-style relative
// duration (e.g. "1h", "30m", "2h45m") interpreted as an offset into the past.
func parseSince(s string) (time.Time, error) {
	if t, err := time.Parse(time.RFC3339, s); err == nil {
		return t, nil
	}
	if d, err := time.ParseDuration(s); err == nil {
		return time.Now().Add(-d), nil
	}
	return time.Time{}, fmt.Errorf("invalid --since %q: expected an RFC3339 timestamp or a duration like 1h, 30m", s)
}

func formatLogTimestamp(t *time.Time) string {
	if t == nil {
		return ""
	}
	return t.UTC().Format(time.RFC3339)
}

func init() {
	resourceLogsCmd.Flags().StringVar(&resourceLogsSince, "since", "", "Only logs newer than this (RFC3339 timestamp or duration like 1h, 30m)")
	resourceLogsCmd.Flags().StringVar(&resourceLogsDeploymentID, "deployment-id", "", "Show logs for a specific deployment (snapshot only)")
	resourceLogsCmd.Flags().StringVar(&resourceLogsPod, "pod", "", "Restrict to a single pod")
	resourceLogsCmd.Flags().StringVar(&resourceLogsContainer, "container", "", "Restrict to a single container")
	resourceLogsCmd.Flags().Int32Var(&resourceLogsLines, "lines", 0, "Maximum number of lines to return (snapshot only)")
	resourceLogsCmd.Flags().BoolVarP(&resourceLogsFollow, "follow", "f", false, "Stream new log lines as they arrive")
	resourceCmd.AddCommand(resourceLogsCmd)
}
