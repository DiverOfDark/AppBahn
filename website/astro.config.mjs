// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
  site: 'https://appbahn.eu',
  output: 'static',
  integrations: [
    starlight({
      title: 'AppBahn Docs',
      favicon: '/favicon.png',
      logo: {
        src: './public/logo.png',
        alt: 'AppBahn',
      },
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/diverofdark/appbahn',
        },
      ],
      sidebar: [
        {
          label: 'Getting Started',
          items: [
            { label: 'Getting Started', slug: 'docs/getting-started' },
            { label: 'Your First Deployment', slug: 'docs/first-deployment' },
          ],
        },
        {
          label: 'Concepts',
          items: [
            { label: 'Concepts', slug: 'docs/concepts' },
            { label: 'Resource Types', slug: 'docs/resource-types' },
          ],
        },
        {
          label: 'API Reference',
          items: [{ label: 'API Reference', slug: 'docs/api-reference' }],
        },
      ],
      customCss: ['./src/styles/custom.css'],
    }),
  ],
});
