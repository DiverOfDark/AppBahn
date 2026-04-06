package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"net"
	"net/http"
	"os/exec"
	"runtime"
	"time"

	"github.com/coreos/go-oidc/v3/oidc"
	"golang.org/x/oauth2"
)

// LoginResult contains the tokens obtained from the OIDC flow.
type LoginResult struct {
	AccessToken  string
	RefreshToken string
	Expiry       time.Time
}

// Login performs the full OIDC Authorization Code + PKCE flow:
//  1. Discover OIDC configuration from the server.
//  2. Generate PKCE code_verifier / code_challenge.
//  3. Start a local HTTP callback server on a random port.
//  4. Open the browser to the authorization URL.
//  5. Wait for the callback with the authorization code.
//  6. Exchange the code for tokens.
func Login(ctx context.Context, serverURL string) (*LoginResult, error) {
	// --- OIDC discovery ---
	issuer := serverURL
	provider, err := oidc.NewProvider(ctx, issuer)
	if err != nil {
		return nil, fmt.Errorf("OIDC discovery failed for %s: %w", issuer, err)
	}

	// --- Allocate a random local port for the callback ---
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, fmt.Errorf("cannot listen on localhost: %w", err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	redirectURL := fmt.Sprintf("http://127.0.0.1:%d/callback", port)

	oauthCfg := oauth2.Config{
		ClientID:    "appbahn-cli",
		Endpoint:    provider.Endpoint(),
		RedirectURL: redirectURL,
		Scopes:      []string{oidc.ScopeOpenID, "profile", "email", "offline_access"},
	}

	// --- PKCE ---
	verifier, challenge, err := generatePKCE()
	if err != nil {
		return nil, fmt.Errorf("PKCE generation failed: %w", err)
	}

	state, err := randomString(32)
	if err != nil {
		return nil, fmt.Errorf("cannot generate state: %w", err)
	}

	authURL := oauthCfg.AuthCodeURL(state,
		oauth2.SetAuthURLParam("code_challenge", challenge),
		oauth2.SetAuthURLParam("code_challenge_method", "S256"),
	)

	// --- Local callback server ---
	type callbackResult struct {
		code string
		err  error
	}
	resultCh := make(chan callbackResult, 1)

	mux := http.NewServeMux()
	mux.HandleFunc("/callback", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("state") != state {
			resultCh <- callbackResult{err: fmt.Errorf("state mismatch")}
			http.Error(w, "State mismatch", http.StatusBadRequest)
			return
		}
		if errMsg := r.URL.Query().Get("error"); errMsg != "" {
			desc := r.URL.Query().Get("error_description")
			resultCh <- callbackResult{err: fmt.Errorf("authorization error: %s — %s", errMsg, desc)}
			fmt.Fprintf(w, "<html><body><h1>Login failed</h1><p>%s: %s</p></body></html>", errMsg, desc)
			return
		}
		code := r.URL.Query().Get("code")
		if code == "" {
			resultCh <- callbackResult{err: fmt.Errorf("no code in callback")}
			http.Error(w, "Missing code", http.StatusBadRequest)
			return
		}
		resultCh <- callbackResult{code: code}
		fmt.Fprint(w, `<html><body><h1>Login successful!</h1><p>You can close this window.</p></body></html>`)
	})

	srv := &http.Server{Handler: mux}
	go func() { _ = srv.Serve(listener) }()
	defer func() { _ = srv.Close() }()

	// --- Open browser ---
	fmt.Printf("Opening browser to authenticate...\n")
	fmt.Printf("If the browser does not open, visit:\n  %s\n\n", authURL)
	OpenBrowserFunc(authURL)

	// --- Wait for callback ---
	var cbResult callbackResult
	select {
	case cbResult = <-resultCh:
	case <-ctx.Done():
		return nil, fmt.Errorf("login timed out waiting for browser callback")
	}
	if cbResult.err != nil {
		return nil, cbResult.err
	}

	// --- Exchange code for tokens ---
	token, err := oauthCfg.Exchange(ctx, cbResult.code,
		oauth2.SetAuthURLParam("code_verifier", verifier),
	)
	if err != nil {
		return nil, fmt.Errorf("token exchange failed: %w", err)
	}

	return &LoginResult{
		AccessToken:  token.AccessToken,
		RefreshToken: token.RefreshToken,
		Expiry:       token.Expiry,
	}, nil
}

// generatePKCE creates a code_verifier and its S256 code_challenge.
func generatePKCE() (verifier, challenge string, err error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", "", err
	}
	verifier = base64.RawURLEncoding.EncodeToString(buf)
	h := sha256.Sum256([]byte(verifier))
	challenge = base64.RawURLEncoding.EncodeToString(h[:])
	return verifier, challenge, nil
}

// randomString generates a URL-safe random string of the given byte length.
func randomString(n int) (string, error) {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

// OpenBrowserFunc is the function used to open a URL in the browser.
// Tests can replace this to intercept the auth URL.
var OpenBrowserFunc = openBrowserDefault

// openBrowserDefault opens the given URL in the default browser.
func openBrowserDefault(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	default: // linux, freebsd, etc.
		cmd = exec.Command("xdg-open", url)
	}
	_ = cmd.Start()
}
