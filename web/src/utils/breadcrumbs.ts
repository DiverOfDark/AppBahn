export type BreadcrumbItem = {
  label: string
  to?: { name: string; params?: Record<string, string> }
}

/**
 * Builds a console breadcrumb chain from workspace/project/environment slugs.
 * All items except the last are clickable navigation links.
 *
 * @param current - Label for the final (non-clickable) breadcrumb item
 * @param includeRoot - Whether to prepend a "Workspaces" root link
 */
export function buildBreadcrumbChain(
  slugs: { wsSlug: string; projSlug?: string; envSlug?: string },
  current: string,
  includeRoot = false,
): BreadcrumbItem[] {
  const { wsSlug, projSlug, envSlug } = slugs
  const items: BreadcrumbItem[] = []

  if (includeRoot) {
    items.push({ label: 'Workspaces', to: { name: 'workspaces' } })
  }

  const levels: BreadcrumbItem[] = [
    { label: wsSlug, to: { name: 'workspace', params: { wsSlug } } },
    ...(projSlug
      ? [{ label: projSlug, to: { name: 'project', params: { wsSlug, projSlug } } }]
      : []),
    ...(envSlug && projSlug
      ? [{ label: envSlug, to: { name: 'environment', params: { wsSlug, projSlug, envSlug } } }]
      : []),
  ]

  for (const level of levels) {
    if (level.label === current && level === levels[levels.length - 1]) {
      items.push({ label: current })
    } else {
      items.push(level)
    }
  }

  if (levels.length === 0 || levels[levels.length - 1]!.label !== current) {
    items.push({ label: current })
  }

  return items
}
