package api

import (
	"context"
	"fmt"
	"net/http"
	"strings"

	"github.com/diverofdark/appbahn/cli/internal/auth"
)

// NewClient creates an authenticated APIClient configured with the stored
// server URL and bearer token. Returns an error if not logged in.
func NewClient() (*APIClient, context.Context, error) {
	serverURL := auth.ServerURL()
	if serverURL == "" {
		return nil, nil, fmt.Errorf("not logged in: run 'appbahn login --server <url>' first")
	}
	token := auth.Token()
	if token == "" {
		return nil, nil, fmt.Errorf("not logged in: run 'appbahn login --server <url>' first")
	}

	cfg := NewConfiguration()
	cfg.Servers = ServerConfigurations{
		{URL: strings.TrimRight(serverURL, "/") + "/api/v1"},
	}
	cfg.HTTPClient = &http.Client{}
	cfg.AddDefaultHeader("Authorization", "Bearer "+token)

	client := NewAPIClient(cfg)
	ctx := context.WithValue(context.Background(), ContextAccessToken, token)

	return client, ctx, nil
}

// FormatAPIError extracts a readable error message from an API error response.
func FormatAPIError(err error) error {
	if openAPIErr, ok := err.(*GenericOpenAPIError); ok {
		body := string(openAPIErr.Body())
		if body != "" {
			return fmt.Errorf("%s: %s", openAPIErr.Error(), body)
		}
		return fmt.Errorf("%s", openAPIErr.Error())
	}
	return err
}
