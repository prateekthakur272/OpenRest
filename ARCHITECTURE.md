# OpenRest source layout

One Gradle module (`shared`) plus `desktopApp` (the `main()` and packaging). Source is grouped by role under
`shared/src/jvmMain/kotlin/dev/prateekthakur/openrest`; the folder is the package.

| Folder | What lives here |
|---|---|
| `features/<name>` | One folder per user-facing feature: `sidebar`, `request`, `response`, `runner`, `workflow`, `preferences`, `dialogs`, `onboarding` (first-run walkthrough; replay from Help ▸ Welcome Tour), `shell` (window, title bar, menus, command palette, tabs, `Modals`) |
| `components` | Reusable UI widgets with no feature knowledge: `Text`, `Buttons`, `Field`, `Overlay` (dropdowns), `Layout` (tabs, split pane, modal box), `Tables`, `Code` (editor/viewer/diff), `ContextMenu`, `MethodPill`, `InlineRename`, `TestResults`, `Preview`, `Modifiers` |
| `theme` | Palette, theme, icons |
| `utils` | Pure helpers with no UI: `Json`, `Yml`, `Bru`, `Diff`, `Xml`, `UrlCodec`, `Format` (`formatSize`, `timeAgo`) |
| `core` | Domain logic with no UI: `model`, `http`, `run` (runner, workflows), `script`, `codegen`, `interop` (import/export), `storage` |
| `app` | `AppState` (single source of truth), `Modal`, per-tab sessions |
| `platform` | OS access: clipboard, file dialogs, reveal in Finder/Explorer |

Dependencies point one way: `features → components → theme`, `features → app → core → utils`; features do not reach into each other
except `request`/`runner`/`workflow` using `response`, and `shell` using all of them.

## Where new code goes
- A reusable widget → `components`. If two features need it, it belongs there, not copied.
- A pure helper → `utils`. OS access → `platform`.
- A new feature → `features/<name>`; hook it up in `features/shell`.
- A new dialog → a `Modal` case in `app/Modal.kt`, the dialog in its feature (or `features/dialogs`), one line in `features/shell/Modals.kt`.
- A new menu item → `AppMenus.build` in `features/shell/Menus.kt` (macOS and Windows/Linux menus both render from it).
- Declarations are `private` unless another file needs them, then `internal`; only cross-feature APIs are public.
