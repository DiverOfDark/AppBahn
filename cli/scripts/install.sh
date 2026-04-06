#!/usr/bin/env bash
# AppBahn CLI installer
# Usage:  curl -fsSL https://appbahn.eu/install.sh | bash
set -euo pipefail

REPO="diverofdark/appbahn"
INSTALL_DIR="/usr/local/bin"
BINARY="appbahn"

# ---------------------------------------------------------------------------
# Detect OS
# ---------------------------------------------------------------------------
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

# ---------------------------------------------------------------------------
# Detect architecture
# ---------------------------------------------------------------------------
detect_arch() {
  local arch
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) echo "amd64" ;;
    aarch64|arm64) echo "arm64" ;;
    *) echo "Unsupported architecture: $arch" >&2; exit 1 ;;
  esac
}

# ---------------------------------------------------------------------------
# Fetch latest release tag from GitHub
# ---------------------------------------------------------------------------
latest_version() {
  curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
    | grep '"tag_name"' \
    | sed -E 's/.*"tag_name":\s*"([^"]+)".*/\1/'
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  local os arch version url tmp_dir archive ext

  os="$(detect_os)"
  arch="$(detect_arch)"
  version="$(latest_version)"

  echo "Installing AppBahn CLI ${version} (${os}/${arch}) ..."

  if [ "$os" = "windows" ]; then
    ext="zip"
  else
    ext="tar.gz"
  fi

  archive="${BINARY}_${version#v}_${os}_${arch}.${ext}"
  url="https://github.com/${REPO}/releases/download/${version}/${archive}"

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT

  echo "Downloading ${url} ..."
  curl -fsSL -o "${tmp_dir}/${archive}" "${url}"

  echo "Extracting ..."
  if [ "$ext" = "zip" ]; then
    unzip -q "${tmp_dir}/${archive}" -d "${tmp_dir}"
  else
    tar -xzf "${tmp_dir}/${archive}" -C "${tmp_dir}"
  fi

  echo "Installing to ${INSTALL_DIR}/${BINARY} ..."
  if [ -w "${INSTALL_DIR}" ]; then
    cp "${tmp_dir}/${BINARY}" "${INSTALL_DIR}/${BINARY}"
    chmod +x "${INSTALL_DIR}/${BINARY}"
  else
    sudo cp "${tmp_dir}/${BINARY}" "${INSTALL_DIR}/${BINARY}"
    sudo chmod +x "${INSTALL_DIR}/${BINARY}"
  fi

  echo "AppBahn CLI ${version} installed successfully!"
  echo "Run 'appbahn version' to verify."
}

main "$@"
