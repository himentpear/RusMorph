# WeRus Product Contract

This document is the source of truth for the learner-facing product surface. Historical branch
`chore/align-phase1-refactor` is reference material only and must not be used as an implementation
baseline or merged wholesale.

## Product identity

- Canonical product name: **全员俄人 WeRus**.
- Launcher label: **全员俄人WeRus**.
- `org.namchieh.rusmorph`, `RusMorphApplication`, database/repository names, GitHub repository
  names, and the `RusMorph-{versionName}-production.apk` filename are technical identities and
  remain unchanged.
- Ordinary learner-facing copy must not present `RusMorph` as the product name.

## Primary navigation

The exact ordered primary navigation is:

1. Home (`home`, 首页)
2. Learning (`learning`, 学习)
3. Dictionary (`dictionary`, 词典)
4. Review (`review`, 复习)
5. Profile (`profile`, 我的)

**FORBIDDEN LEGACY NAVIGATION:** Home / Tools / Dictionary / Review / Profile.
`Tools` must never be a `BottomDestination`; a deprecated internal route may exist only for
compatibility and must not be reachable from primary learner navigation.

## Screen responsibilities

- Home answers “What should I learn now?” It contains today's review/memory state, the active
  course continuation, and lightweight dictionary/pronunciation actions. It is not a tool catalog.
- Learning owns textbook selection, the active plan, lesson continuation, texts, vocabulary,
  grammar/patterns, dialogue, pronunciation practice, and contextual AI learning assistance.
- Profile owns learning statistics, favorites, accumulated study history, and Settings.
- Settings exposes normal preferences and update checks. Diagnostics, Worker tests, review
  workbench, simulated updates, logs, and internal controls are hidden behind Developer Mode.
- Developer Mode defaults to off and is enabled by tapping the version row seven times.

## Visual language

Theme identifier: `werus-red-beige-v1`.

| Token | Value |
| --- | --- |
| Red / RedDark / RedSoft | `#A33A32` / `#702822` / `#F0DCD8` |
| Paper / Canvas | `#FFFCF7` / `#F7F4EE` |
| Beige / BeigeMuted | `#F0E5D1` / `#F0ECE4` |
| Gold / GoldDark | `#C19A5B` / `#8B642F` |
| Ink / InkMuted / InkFaint | `#292421` / `#716861` / `#8C827A` |

`WerusColors`, `WerusTypography`, `WerusSpacing`, `WerusShapes`, and `WerusTheme` are canonical.
Book/learning UI uses serif headings and readable paper surfaces. Terminal/diagnostic visuals stay
isolated from ordinary learning UI. Success, warning, and error retain distinct muted feedback hues.

## Launcher identity

Launcher baseline identifier: `werus-brand-v1`. It is the safe-zone calibrated artwork from
historical commit `9dec524`: brand-red `#ED0229`, warm foreground `#F8E9D4`, `Ру`, Orthodox-cross
and WeRus identity. Source and generated Android assets are hash-locked in
`release/brand-assets.json`; changes require an explicit brand-baseline update.

## Change policy

UI baseline, launcher identity, and navigation IA are frozen by default. Database, textbook, AI,
login, update, API, performance, and architecture changes must leave this contract untouched unless
the task explicitly authorizes a product-surface change. Current main UI wins merge conflicts.
