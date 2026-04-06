package auth

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

// Config holds the persisted CLI configuration, stored at
// ~/.appbahn/config.json.
type Config struct {
	Server       string    `json:"server"`
	AccessToken  string    `json:"access_token"`
	RefreshToken string    `json:"refresh_token,omitempty"`
	ExpiresAt    time.Time `json:"expires_at,omitempty"`
}

// configDir returns ~/.appbahn, creating it if necessary.
func configDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot determine home directory: %w", err)
	}
	dir := filepath.Join(home, ".appbahn")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return "", fmt.Errorf("cannot create config directory: %w", err)
	}
	return dir, nil
}

// configPath returns the full path to the config file.
func configPath() (string, error) {
	dir, err := configDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "config.json"), nil
}

// LoadConfig reads and parses the stored configuration. Returns nil (no
// error) when the file does not exist.
func LoadConfig() (*Config, error) {
	p, err := configPath()
	if err != nil {
		return nil, err
	}
	data, err := os.ReadFile(p)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, fmt.Errorf("cannot read config: %w", err)
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("cannot parse config: %w", err)
	}
	return &cfg, nil
}

// SaveConfig writes the configuration to disk.
func SaveConfig(cfg *Config) error {
	p, err := configPath()
	if err != nil {
		return err
	}
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return fmt.Errorf("cannot marshal config: %w", err)
	}
	if err := os.WriteFile(p, data, 0600); err != nil {
		return fmt.Errorf("cannot write config: %w", err)
	}
	return nil
}

// ServerURL returns the effective server URL. The APPBAHN_SERVER env var
// takes precedence over the stored config value.
func ServerURL() string {
	if v := os.Getenv("APPBAHN_SERVER"); v != "" {
		return v
	}
	cfg, err := LoadConfig()
	if err != nil || cfg == nil {
		return ""
	}
	return cfg.Server
}

// Token returns the effective access token. The APPBAHN_TOKEN env var
// takes precedence over the stored config value.
func Token() string {
	if v := os.Getenv("APPBAHN_TOKEN"); v != "" {
		return v
	}
	cfg, err := LoadConfig()
	if err != nil || cfg == nil {
		return ""
	}
	return cfg.AccessToken
}
