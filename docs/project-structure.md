# Project structure

OpenRest is a Kotlin project built with Gradle and Compose Multiplatform (desktop). There are two Gradle modules:

| Module | Purpose |
|---|---|
| `shared` | Everything: logic, state and UI |
| `desktopApp` | The `main()` function, the window and packaging (DMG / MSI / DEB) |

```
OpenRest/
├── README.md
├── ARCHITECTURE.md              short version of this page
├── docs/                        user and contributor documentation
│   └── screenshots/             images used by the docs
├── gradle/libs.versions.toml    dependency and plugin versions
├── settings.gradle.kts
├── build.gradle.kts
├── desktopApp/
│   ├── build.gradle.kts         app label, version, icons, installer formats
│   ├── icons/                   icon.icns, icon.ico, icon.png
│   └── src/main/kotlin/…/main.kt
└── shared/
    ├── build.gradle.kts
    └── src/
        ├── jvmMain/kotlin/dev/prateekthakur/openrest/
        │   ├── features/        one folder per user-facing feature
        │   ├── components/      reusable UI widgets
        │   ├── theme/           palette, theme, icons
        │   ├── utils/           pure helpers (JSON, YAML, diff, formatting)
        │   ├── core/            domain logic, no UI
        │   ├── app/             application state
        │   └── platform/        operating-system access
        ├── jvmMain/resources/   prelude.js (the script API)
        └── jvmTest/kotlin/…     tests
```

The folder is the package: `features/sidebar/Tree.kt` is in `dev.prateekthakur.openrest.features.sidebar`.

## `features/`

Each folder is one part of the app a user can point at.

| Folder | What it is |
|---|---|
| `shell` | The window: `App.kt` (layout, tab bar, header), title bar and `WindowChrome`, `Menus` (the menu content, as data), command `Palette`, `Modals` (which dialog is showing) |
| `sidebar` | Collection tree, drag and drop, context menus, history |
| `request` | The request editor: params, body, headers, auth, scripts, assertions, tests |
| `response` | The response viewer: body, headers, cookies, timeline, tests, console |
| `runner` | The collection runner tab |
| `workflow` | The workflow builder (`WorkflowView`), run log, request and logic steps, value pickers, response viewer |
| `preferences` | The Preferences window |
| `dialogs` | Create, open, import, export, environments, variables, cookies and code-generation dialogs |
| `onboarding` | The first-run walkthrough |

## `components/`

Reusable widgets that know nothing about any feature: `Text`, `Buttons`, `Field` (the variable-aware input with highlighting and autocomplete), `Overlay` (dropdowns), `Layout` (tabs, split pane, `ModalBox`), `Tables`, `Code` (editor, viewer, diff), `ContextMenu`, `MethodPill`, `InlineRename`, `TestResults`, `Preview` (HTML and image preview), `Modifiers`.

If a second feature needs a widget, it belongs here.

## `core/`

Domain logic. No Compose UI.

| Folder | What it is |
|---|---|
| `model` | Requests, collections, environments, settings |
| `http` | The HTTP client, variable interpolation, URL encoding |
| `run` | Running a request (`Runner`), workflows (`Workflow`, `WorkflowEngine`) |
| `script` | The JavaScript engine bridge |
| `codegen` | Request to code snippets |
| `interop` | Importers and exporters |
| `storage` | Files on disk: collections, settings, secrets, environments |

## `utils/`, `theme/`, `app/`, `platform/`

- `utils`: `Json`, `Yml`, `Bru`, `Diff`, `Xml`, `UrlCodec`, `Format` (`formatSize`, `timeAgo`). Pure functions.
- `theme`: the colour palette, light and dark themes, icon set.
- `app`: `AppState` is the single source of truth (Compose snapshot state: tabs, collections, environments, settings, the current dialog). `Modal` lists every dialog. `Sessions` holds per-tab state.
- `platform`: clipboard, file dialogs, "reveal in Finder / Explorer".

## How the pieces fit

```
features ──► components ──► theme
   │
   └──► app ──► core ──► utils
          └──► platform
```

- Features read and change state through `AppState`; they do not call each other, except `request`, `runner` and `workflow` which reuse `response`, and `shell` which assembles everything.
- Menus are data (`AppMenus.build`), rendered natively on macOS and by `TitleMenus` on Windows and Linux, so one definition serves both.
- Dialogs are cases of `Modal`; `Modals` picks the dialog to draw.

## Where does new code go?

| I want to… | Put it in |
|---|---|
| Add a reusable button, input or table | `components/` |
| Add a feature | `features/<name>/`, then hook it into `features/shell` |
| Add a dialog | a `Modal` case in `app/Modal.kt`, the dialog in its feature, a line in `features/shell/Modals.kt` |
| Add a menu item | `AppMenus.build` in `features/shell/Menus.kt` |
| Add a pure helper | `utils/` |
| Add a request or import feature with no UI | `core/` |
| Touch the OS | `platform/` |

## Tests

Tests are in `shared/src/jvmTest`. They cover the core (parsing, importing, exporting, scripts, workflows) and the UI headlessly with Compose's desktop test API (`runDesktopComposeUiTest`). See [contributing](contributing.md#tests).
