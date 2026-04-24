#!/usr/bin/env bash
# AppBahn CLI installer.
#
# Fetches the current release from appbahn.eu and installs it into /usr/local/bin.
# All binaries are served from the public website — no GitHub API calls.
#
# Usage:
#   curl -fsSL https://appbahn.eu/download/install.sh | bash
#   curl -fsSL https://appbahn.eu/download/install.sh | bash -s -- --version 0.3.0
#
# Environment overrides:
#   APPBAHN_VERSION       pin a specific version (e.g. 0.3.0); defaults to latest
#   APPBAHN_INSTALL_DIR   target directory (default: /usr/local/bin)
#   APPBAHN_DOWNLOAD_BASE base URL for artifacts (default: https://appbahn.eu/download)

set -euo pipefail

DOWNLOAD_BASE="${APPBAHN_DOWNLOAD_BASE:-https://appbahn.eu/download}"
INSTALL_DIR="${APPBAHN_INSTALL_DIR:-/usr/local/bin}"
VERSION="${APPBAHN_VERSION:-}"
BINARY="appbahn"

while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --install-dir) INSTALL_DIR="$2"; shift 2 ;;
    --base-url) DOWNLOAD_BASE="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

detect_os() {
  local os
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  case "$os" in
    linux*)  echo "linux" ;;
    darwin*) echo "darwin" ;;
    mingw*|msys*|cygwin*) echo "windows" ;;
    *) echo "Unsupported OS: $os" >&2; exit 1 ;;
  esac
}

detect_arch() {
  local arch
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) echo "amd64" ;;
    aarch64|arm64) echo "arm64" ;;
    *) echo "Unsupported architecture: $arch" >&2; exit 1 ;;
  esac
}

resolve_version() {
  if [ -n "$VERSION" ]; then
    echo "${VERSION#v}"
    return
  fi
  curl -fsSL "${DOWNLOAD_BASE}/version.txt" | tr -d '[:space:]'
}

sha256_of() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    echo ""
  fi
}

main() {
  local os arch version url tmp_dir archive ext expected actual
  os="$(detect_os)"
  arch="$(detect_arch)"
  version="$(resolve_version)"

  if [ -z "$version" ]; then
    echo "Failed to resolve AppBahn CLI version from ${DOWNLOAD_BASE}/version.txt" >&2
    exit 1
  fi

  if [ "$os" = "windows" ]; then
    ext="zip"
  else
    ext="tar.gz"
  fi

  archive="${BINARY}_${version}_${os}_${arch}.${ext}"
  url="${DOWNLOAD_BASE}/${archive}"

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT

  echo "Installing AppBahn CLI ${version} (${os}/${arch})..."
  echo "Downloading ${url}..."
  curl -fsSL -o "${tmp_dir}/${archive}" "${url}"

  echo "Verifying checksum..."
  curl -fsSL -o "${tmp_dir}/checksums.txt" "${DOWNLOAD_BASE}/checksums.txt"
  expected="$(awk -v f="${archive}" '$2 == f {print $1}' "${tmp_dir}/checksums.txt")"
  if [ -z "$expected" ]; then
    echo "Checksum for ${archive} not found in checksums.txt" >&2
    exit 1
  fi
  actual="$(sha256_of "${tmp_dir}/${archive}")"
  if [ -z "$actual" ]; then
    echo "Warning: no sha256 tool found; skipping checksum verification." >&2
  elif [ "$actual" != "$expected" ]; then
    echo "Checksum mismatch for ${archive}" >&2
    echo "  expected: ${expected}" >&2
    echo "  actual:   ${actual}" >&2
    exit 1
  fi

  echo "Extracting..."
  if [ "$ext" = "zip" ]; then
    unzip -q "${tmp_dir}/${archive}" -d "${tmp_dir}"
  else
    tar -xzf "${tmp_dir}/${archive}" -C "${tmp_dir}"
  fi

  echo "Installing to ${INSTALL_DIR}/${BINARY}..."
  if [ -w "${INSTALL_DIR}" ]; then
    install -m 0755 "${tmp_dir}/${BINARY}" "${INSTALL_DIR}/${BINARY}"
  else
    sudo install -m 0755 "${tmp_dir}/${BINARY}" "${INSTALL_DIR}/${BINARY}"
  fi

  echo "AppBahn CLI ${version} installed. Run 'appbahn version' to verify."
}

main "$@"
