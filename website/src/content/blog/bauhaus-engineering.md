---
title: 'Bauhaus engineering: a design system for infrastructure UI'
summary: We built the AppBahn design system around one question — how do you make infrastructure tools that feel engineered rather than marketed?
date: 2026-04-23
author: AppBahn
tags: [design, engineering]
---

Most infrastructure UI falls into one of two traps. It is either over-decorated — gradient
buttons, hero illustrations, marketing-grade shadows applied to a kubectl wrapper — or it is
under-designed to the point that every table looks like someone ran `kubectl get` and stopped.

We picked a third shape, which we call Bauhaus engineering. The brief was short:

- Dark-first, warm-neutral. OKLch across the board.
- One accent colour (signal amber). Hierarchy through weight, not colour.
- No shadows. 1-pixel borders. 8-pixel grid.
- Typography that looks like a terminal had a child with a good paperback.

This post is a walkthrough of the decisions behind that brief, why each one earned its
place in the system, and what that means for anyone porting code into the AppBahn console.

### The token layer

We use OKLch for every colour token. The practical payoff is that the perceptual distance
between `oklch(80% 0.16 80)` (signal amber) and `oklch(65% 0.16 80)` (its muted sibling) is
the same distance for every user, on every display. sRGB gradients break this promise the
moment someone calibrates their monitor. OKLch does not.

The grayscale ramp is warm-neutral — every "grey" has a slight chroma bias toward amber
(`chroma ≈ 0.01, hue ≈ 80`). On a calibrated screen this is nearly invisible; what it does
is prevent the dead-computer feeling of pure grey surfaces. Backgrounds look lit even when
they are not.

### Why no shadows

Shadows are how most dark UIs fake depth. They are also how every dark UI looks identical
to every other dark UI. We opted out. Depth in AppBahn comes from a flat surface stack
(`bg-base`, `bg-surface`, `bg-raised`) with 1-pixel borders to separate them. The effect is
more technical and more honest — the interface admits it is flat, and the reader can trust
the geometry.

### Typography as a tool

Three typefaces, each with a single job:

- **Geist** for headings. Tight tracking, geometric feel, holds up at 72px.
- **Inter** for body. Nothing controversial here; it does the job.
- **JetBrains Mono** for every number, identifier, command, and hostname.

The mono family is the one nobody regrets. Every piece of information on an infrastructure
dashboard is ultimately a string a machine will also read — `0.24 vCPU`, `412 MB`,
`abp-api-5f6d7c9b4-2k9ql`. Presenting those in a proportional font is a small lie about
what the data actually is. Mono removes the lie.

### Voice, enforced

The design system also codifies voice. No exclamation marks. No emoji. No hype adjectives.
Error copy is specific and low-key: `readiness probe failed: timeout 5s` rather than `Oops,
something went wrong!` The rules live in `CONTENT.md` and they apply to marketing copy,
docs, error strings, and CLI output alike.

### What we are not

We are not shipping a component library as a separate product. There is no `@appbahn/ui`
on npm. The design system is internal-facing, the tokens are CSS custom properties, and
the consistency comes from everyone reading the same `custom.css`. That is a deliberate
choice — platform engineering teams who fork AppBahn get a coherent UI, not a dependency.

If you want to see the system applied end-to-end, the AppBahn console is the reference.
Every page there is built against the same tokens, the same six-component vocabulary, and
the same voice rules.
