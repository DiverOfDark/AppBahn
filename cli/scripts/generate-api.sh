#!/usr/bin/env bash
# Generates the Go API client from the committed OpenAPI spec using
# openapi-generator. `api/public-api.yaml` is emitted by springdoc from the
# platform's `@RestController`s (code-first) via the Gradle
# `:platform:app:syncOpenApi` task, and its freshness is gated in CI by
# `:platform:app:verifyOpenApi`.
#
# Usage: ./scripts/generate-api.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLI_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$CLI_DIR")"
SPEC_FILE="$PROJECT_ROOT/api/public-api.yaml"
OUTPUT_DIR="$CLI_DIR/internal/api"

# Clean previous generated code (preserve .openapi-generator-ignore and the files
# it whitelists — currently helpers.go, which provides NewClient + FormatAPIError).
if [ -d "$OUTPUT_DIR" ]; then
  find "$OUTPUT_DIR" -type f \
    ! -name '.openapi-generator-ignore' \
    ! -name 'helpers.go' \
    -delete 2>/dev/null || true
  find "$OUTPUT_DIR" -type d -empty -delete 2>/dev/null || true
fi

mkdir -p "$OUTPUT_DIR"

podman run --rm \
  -v "$PROJECT_ROOT:/work:Z" \
  docker.io/openapitools/openapi-generator-cli:latest generate \
  -i /work/api/public-api.yaml \
  -g go \
  -o /work/cli/internal/api \
  --package-name api \
  --git-repo-id appbahn \
  --git-user-id diverofdark \
  --additional-properties=generateInterfaces=true,enumClassPrefix=true,isGoSubmodule=true \
  --global-property=apiTests=false,modelTests=false,apiDocs=false,modelDocs=false

# Remove unnecessary generated files
rm -f "$OUTPUT_DIR/go.mod" "$OUTPUT_DIR/go.sum" "$OUTPUT_DIR/.travis.yml" "$OUTPUT_DIR/git_push.sh"
rm -rf "$OUTPUT_DIR/.openapi-generator" "$OUTPUT_DIR/test" "$OUTPUT_DIR/docs" "$OUTPUT_DIR/api"

# Format generated Go code
gofmt -w "$OUTPUT_DIR"

echo "API client generated in $OUTPUT_DIR"
