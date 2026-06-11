package cmd

import (
	"context"
	"fmt"
	"time"

	"github.com/diverofdark/appbahn/cli/internal/api"
	"github.com/diverofdark/appbahn/cli/internal/output"
	"github.com/spf13/cobra"
)

const (
	metricTypeCPU             = "cpu"
	metricTypeRAM             = "ram"
	metricTypeNetworkInbound  = "network-inbound"
	metricTypeNetworkOutbound = "network-outbound"
)

var metricTypes = []string{metricTypeCPU, metricTypeRAM, metricTypeNetworkInbound, metricTypeNetworkOutbound}

var (
	resourceMetricsType  string
	resourceMetricsStart string
	resourceMetricsEnd   string
	resourceMetricsStep  int32
	resourceMetricsPod   string
)

var resourceMetricsCmd = &cobra.Command{
	Use:   "metrics <slug>",
	Short: "Show time-series metrics for a resource",
	Long: `Print a metric time series for a resource. Choose the metric with
--metric-type (cpu, ram, network-inbound, network-outbound) and narrow the
window with --start/--end/--step/--pod.

Examples:
  appbahn resource metrics my-app-abc1234 --metric-type cpu
  appbahn resource metrics my-app-abc1234 --metric-type ram --start 2026-06-07T00:00:00Z --step 60
  appbahn resource metrics my-app-abc1234 --metric-type network-inbound --pod my-app-abc1234-7d9`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		slug := args[0]

		if !isValidMetricType(resourceMetricsType) {
			return fmt.Errorf("invalid --metric-type %q (use one of: cpu, ram, network-inbound, network-outbound)", resourceMetricsType)
		}

		client, ctx, err := api.NewClient()
		if err != nil {
			return err
		}

		res, err := executeMetrics(client, ctx, slug)
		if err != nil {
			return api.FormatAPIError(err)
		}

		format, err := output.ParseFormat(OutputFormat())
		if err != nil {
			return err
		}

		headers := []string{"POD", "TIMESTAMP", "VALUE"}
		var rows [][]string
		for _, series := range res.GetSeries() {
			pod := series.GetPod()
			for _, p := range series.GetValues() {
				rows = append(rows, []string{
					pod,
					formatMetricTimestamp(p.Timestamp),
					formatMetricValue(p.Value),
				})
			}
		}
		return output.Print(format, headers, rows, res)
	},
}

func executeMetrics(client *api.APIClient, ctx context.Context, slug string) (*api.MetricsResponse, error) {
	switch resourceMetricsType {
	case metricTypeCPU:
		req := client.ResourcesAPI.GetResourceCpuMetrics(ctx, slug)
		if resourceMetricsStart != "" {
			req = req.Start(resourceMetricsStart)
		}
		if resourceMetricsEnd != "" {
			req = req.End(resourceMetricsEnd)
		}
		if resourceMetricsStep > 0 {
			req = req.Step(resourceMetricsStep)
		}
		if resourceMetricsPod != "" {
			req = req.Pod(resourceMetricsPod)
		}
		res, _, err := req.Execute()
		return res, err
	case metricTypeRAM:
		req := client.ResourcesAPI.GetResourceRamMetrics(ctx, slug)
		if resourceMetricsStart != "" {
			req = req.Start(resourceMetricsStart)
		}
		if resourceMetricsEnd != "" {
			req = req.End(resourceMetricsEnd)
		}
		if resourceMetricsStep > 0 {
			req = req.Step(resourceMetricsStep)
		}
		if resourceMetricsPod != "" {
			req = req.Pod(resourceMetricsPod)
		}
		res, _, err := req.Execute()
		return res, err
	case metricTypeNetworkInbound:
		req := client.ResourcesAPI.GetResourceNetworkInbound(ctx, slug)
		if resourceMetricsStart != "" {
			req = req.Start(resourceMetricsStart)
		}
		if resourceMetricsEnd != "" {
			req = req.End(resourceMetricsEnd)
		}
		if resourceMetricsStep > 0 {
			req = req.Step(resourceMetricsStep)
		}
		if resourceMetricsPod != "" {
			req = req.Pod(resourceMetricsPod)
		}
		res, _, err := req.Execute()
		return res, err
	default:
		req := client.ResourcesAPI.GetResourceNetworkOutbound(ctx, slug)
		if resourceMetricsStart != "" {
			req = req.Start(resourceMetricsStart)
		}
		if resourceMetricsEnd != "" {
			req = req.End(resourceMetricsEnd)
		}
		if resourceMetricsStep > 0 {
			req = req.Step(resourceMetricsStep)
		}
		if resourceMetricsPod != "" {
			req = req.Pod(resourceMetricsPod)
		}
		res, _, err := req.Execute()
		return res, err
	}
}

func isValidMetricType(t string) bool {
	for _, m := range metricTypes {
		if t == m {
			return true
		}
	}
	return false
}

func formatMetricTimestamp(ts *float64) string {
	if ts == nil {
		return ""
	}
	secs := int64(*ts)
	nanos := int64((*ts - float64(secs)) * 1e9)
	return time.Unix(secs, nanos).UTC().Format(time.RFC3339)
}

func formatMetricValue(v *float64) string {
	if v == nil {
		return ""
	}
	return fmt.Sprintf("%g", *v)
}

func init() {
	resourceMetricsCmd.Flags().StringVar(&resourceMetricsType, "metric-type", metricTypeCPU, "Metric to fetch: cpu, ram, network-inbound, network-outbound")
	resourceMetricsCmd.Flags().StringVar(&resourceMetricsStart, "start", "", "Range start (RFC3339 or relative, e.g. -1h)")
	resourceMetricsCmd.Flags().StringVar(&resourceMetricsEnd, "end", "", "Range end (RFC3339, defaults to now)")
	resourceMetricsCmd.Flags().Int32Var(&resourceMetricsStep, "step", 0, "Sample step in seconds")
	resourceMetricsCmd.Flags().StringVar(&resourceMetricsPod, "pod", "", "Restrict to a single pod")
	resourceCmd.AddCommand(resourceMetricsCmd)
}
