import rss from '@astrojs/rss'
import { getCollection } from 'astro:content'
import type { APIContext } from 'astro'

export async function GET(context: APIContext) {
  const posts = (await getCollection('blog', ({ data }) => !data.draft)).sort(
    (a, b) => b.data.date.getTime() - a.data.date.getTime(),
  )

  return rss({
    title: 'AppBahn Blog',
    description: 'Engineering writing from the AppBahn team.',
    site: context.site ?? 'https://appbahn.eu',
    items: posts.map((post) => ({
      title: post.data.title,
      description: post.data.summary,
      pubDate: post.data.date,
      link: `/blog/${post.id}/`,
      author: post.data.author,
      categories: post.data.tags,
    })),
  })
}
