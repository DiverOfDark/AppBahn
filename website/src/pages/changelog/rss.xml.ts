import rss from '@astrojs/rss'
import { getCollection } from 'astro:content'
import type { APIContext } from 'astro'

export async function GET(context: APIContext) {
  const entries = (await getCollection('changelog')).sort(
    (a, b) => b.data.date.getTime() - a.data.date.getTime(),
  )

  return rss({
    title: 'AppBahn Changelog',
    description: 'Release notes for AppBahn — self-hosted PaaS on Kubernetes.',
    site: context.site ?? 'https://appbahn.eu',
    items: entries.map((entry) => {
      const anchor = entry.data.version ?? entry.id
      const title = entry.data.version
        ? `${entry.data.version} — ${entry.data.title}`
        : entry.data.title
      return {
        title,
        description: entry.data.summary,
        pubDate: entry.data.date,
        link: `/changelog/#${anchor}`,
      }
    }),
  })
}
