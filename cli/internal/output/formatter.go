package output

import (
	"encoding/json"
	"fmt"
	"io"
	"os"

	"github.com/olekukonko/tablewriter"
	"github.com/olekukonko/tablewriter/tw"

	"gopkg.in/yaml.v3"
)

// Format enumerates supported output formats.
type Format string

const (
	FormatTable Format = "table"
	FormatJSON  Format = "json"
	FormatYAML  Format = "yaml"
)

// ParseFormat converts a string flag value into a Format, returning an
// error for unknown values.
func ParseFormat(s string) (Format, error) {
	switch s {
	case "table", "":
		return FormatTable, nil
	case "json":
		return FormatJSON, nil
	case "yaml":
		return FormatYAML, nil
	default:
		return "", fmt.Errorf("unsupported output format %q (use table, json, or yaml)", s)
	}
}

// Print renders data according to the given format. For table output,
// headers and rows must be provided. For json/yaml, the data parameter
// (typically a slice of structs or maps) is serialised directly.
func Print(format Format, headers []string, rows [][]string, data any) error {
	return Fprint(os.Stdout, format, headers, rows, data)
}

// Fprint writes formatted output to w.
func Fprint(w io.Writer, format Format, headers []string, rows [][]string, data any) error {
	switch format {
	case FormatJSON:
		return printJSON(w, data)
	case FormatYAML:
		return printYAML(w, data)
	default:
		printTable(w, headers, rows)
		return nil
	}
}

func printJSON(w io.Writer, data any) error {
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(data)
}

func printYAML(w io.Writer, data any) error {
	enc := yaml.NewEncoder(w)
	enc.SetIndent(2)
	if err := enc.Encode(data); err != nil {
		return err
	}
	return enc.Close()
}

func printTable(w io.Writer, headers []string, rows [][]string) {
	table := tablewriter.NewTable(w,
		tablewriter.WithHeaderAlignment(tw.AlignLeft),
		tablewriter.WithRowAlignment(tw.AlignLeft),
		tablewriter.WithHeaderAutoFormat(tw.On),
		tablewriter.WithPadding(tw.Padding{Left: "  ", Right: "", Overwrite: true}),
		tablewriter.WithBorders(tw.BorderNone),
		tablewriter.WithRendererSettings(tw.Settings{
			Separators: tw.SeparatorsNone,
			Lines:      tw.LinesNone,
		}),
	)
	table.Header(headers)
	table.Bulk(rows)
	table.Render()
}
