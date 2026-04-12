const APP_TITLE = 'AppBahn'

export function usePageTitle() {
  function setPageTitle(...parts: string[]) {
    const filtered = parts.filter(Boolean)
    document.title = filtered.length > 0 ? `${filtered.join(' — ')} — ${APP_TITLE}` : APP_TITLE
  }

  return { setPageTitle }
}
