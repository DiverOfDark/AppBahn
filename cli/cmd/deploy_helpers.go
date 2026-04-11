package cmd

import (
	"fmt"

	"github.com/diverofdark/appbahn/cli/internal/api"
)

func deploymentToRow(dep api.Deployment) []string {
	return []string{
		dep.GetId(),
		dep.GetStatus(),
		dep.GetImageRef(),
		dep.GetTriggeredBy(),
		fmt.Sprintf("%t", dep.GetIsPrimary()),
		dep.GetCreatedAt().Format("2006-01-02T15:04:05Z"),
	}
}
