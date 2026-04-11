// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	base: '/docs',
	integrations: [
		starlight({
			title: 'AppBahn Docs',
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
						{ label: 'Getting Started', slug: 'getting-started' },
						{ label: 'Your First Deployment', slug: 'first-deployment' },
					],
				},
				{
					label: 'Concepts',
					items: [
						{ label: 'Concepts', slug: 'concepts' },
						{ label: 'Resource Types', slug: 'resource-types' },
					],
				},
				{
					label: 'API Reference',
					items: [
						{ label: 'API Reference', slug: 'api-reference' },
					],
				},
			],
			customCss: [
				'./src/styles/custom.css',
			],
		}),
	],
});
