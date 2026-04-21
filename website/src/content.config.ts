import { defineCollection, z } from 'astro:content'
import { glob } from 'astro/loaders'
import { docsLoader } from '@astrojs/starlight/loaders'
import { docsSchema } from '@astrojs/starlight/schema'

const blog = defineCollection({
  loader: glob({ base: './src/content/blog', pattern: '**/*.md' }),
  schema: z.object({
    title: z.string(),
    summary: z.string(),
    date: z.coerce.date(),
    author: z.string().default('AppBahn'),
    tags: z.array(z.string()).default([]),
    draft: z.boolean().default(false),
  }),
})

const changelog = defineCollection({
  loader: glob({ base: './src/content/changelog', pattern: '**/*.md' }),
  schema: z.object({
    version: z.string().optional(),
    title: z.string(),
    date: z.coerce.date(),
    summary: z.string(),
  }),
})

export const collections = {
  docs: defineCollection({ loader: docsLoader(), schema: docsSchema() }),
  blog,
  changelog,
}
