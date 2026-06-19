# AGENTS.md — St. Petersburg Time Machine

## Project goal

Build a beautiful, fast, interactive one-page website about Saint Petersburg.
The first version should prove the core idea:

- a map of Saint Petersburg
- a timeline slider
- landmarks / historical annotations
- a clean information panel

Optimize for shipping a polished MVP, not for architecture purity.

## Core principles

- Keep the app simple.
- Prefer small, composable changes.
- Do not introduce backend code unless the feature truly needs it.
- Do not introduce extra frameworks just because they are popular.
- Do not overengineer the data model before the map works.
- Preserve the ClojureScript-first direction of the project.

## Current stack assumption

This project was bootstrapped with `npx create-cljs-project`.
Assume the app uses:

- ClojureScript
- `shadow-cljs`
- browser-based frontend code
- npm packages as needed for UI and mapping

Before changing tooling, inspect the existing files first:

- `package.json`
- `shadow-cljs.edn`
- `src/main`
- `public/` or equivalent static assets directory

## Preferred implementation style

- Use plain, understandable ClojureScript.
- Prefer pure functions for data transformation.
- Keep application state small and explicit.
- Use one central state atom or a similarly simple state shape.
- Keep rendering code separate from data-fetching / data-shaping code.
- Use interop only where it adds clear value.
- Keep components small and readable.

## UI direction

This is a visual project. Prioritize:

- map clarity
- typography
- smooth interaction
- legible timeline controls
- simple, satisfying transitions
- historically meaningful details

Do not spend time on unnecessary admin features, authentication, or multi-user machinery in the MVP.

## Data direction

Start with static, versioned data files if possible.

Good early choices:

- EDN
- JSON
- simple local fixtures

Do not introduce a database until the project clearly needs one.

## Mapping direction

The map is the heart of the app.
Choose the simplest mapping approach that can:

- render Saint Petersburg
- place markers / overlays
- respond to the timeline
- show details for a selected place

Keep the first map experience working before adding historical layers, photo overlays, or AI features.

## Workflow

When making a change:

1. Understand the current file structure.
2. Make the smallest useful edit.
3. Keep the project runnable.
4. Verify the app still works.
5. Commit in small increments.

## Before adding a dependency

Ask:

- Is this dependency actually necessary?
- Can the same result be achieved with existing tools?
- Does this make the app easier to maintain?
- Will this improve the user experience immediately?

If the answer is not clearly yes, do not add it yet.

## Testing / verification

Prefer the lightest verification that proves the change:

- build succeeds
- browser loads
- app renders
- timeline changes state
- map updates correctly

Add tests when logic becomes non-trivial, especially for:

- date / year filtering
- landmark selection
- timeline-driven visibility
- data normalization

## Coding conventions

- Use descriptive names.
- Keep functions short.
- Avoid clever abstractions.
- Avoid premature macros.
- Prefer data over configuration complexity.
- Document unusual decisions briefly in code or commit messages.

## What not to do

- Do not add a backend by default.
- Do not add authentication.
- Do not add a database early.
- Do not switch frameworks without a strong reason.
- Do not refactor for elegance unless it solves a real problem.
- Do not let the scaffold become larger than the actual feature.

## Good first milestones

1. Show Saint Petersburg on a map.
2. Add a slider for the year.
3. Show a few landmark points.
4. Clicking a point opens a detail panel.
5. Each year changes which points are visible.
6. Add historical notes and images.

## If uncertain

Prefer the simplest implementation that keeps the project moving.
If a choice affects architecture, leave a brief note explaining the tradeoff.
