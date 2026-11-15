# OpenRest architecture

OpenRest is a Kotlin Multiplatform / Compose Desktop app. `desktopApp` holds only `main()` (window, menu bar,
window-size persistence); everything else lives in `shared`.

## Layers (`shared/src/jvmMain/.../openrest`)

| Package | Layer | Owns | May depend on |
|---|---|---|---|
| `core/model` | Domain | Requests, collections, environments, settings, `HttpResult` | nothing in the app |
| `core/run`, `core/script`, `core/codegen` | Application / domain services | Sending requests, workflows (`WorkflowModel`, `WorkflowTree`, `WorkflowRunModels`, `WorkflowBuilder`), scripting, code generation | `core/model`, `utils` |
| `core/http`, `core/storage`, `core/interop` | Data | HTTP client, files under `~/.openrest` and in collections, importers / exporters | `core/model` |
| `core/logging` | Infrastructure | `Log`: levels, tags, stack traces, size-capped log file `~/.openrest/openrest.log` | nothing |
| `platform` | Infrastructure | File pickers, clipboard, opening URLs | nothing in the app |
| `app` | Presentation state | `AppState` (state owner) and its operations, split by topic: `AppStateWorkflows`, `AppStateSidebar`, `AppStateEnvironments`, `AppStateTransfer` | `core`, `platform` |
| `features/*` | Presentation | One package per screen area (sidebar, request, response, workflow, runner, dialogs, shell, ...) | `app`, `components`, `core` |
| `components`, `theme` | Shared presentation | Reusable widgets and design tokens; no knowledge of any feature | `theme`, `core/model` |
| `utils` | Utilities | Pure helpers (JSON, YAML, XML, diff, formatting) | nothing in the app |

Dependencies point inward: `features` -> `app` -> `core`; `core` never imports `features`, `app` or `components`
(known exception: `core/run` still uses `components` for syntax-highlighting helpers; move it out when touched).

## Conventions

- One responsibility per file; a file over ~300 lines is a signal to split by topic (see `components/` `Code*`, `*Preview`).
- Operations on `AppState` are extension functions grouped by topic, so the class holds state and the files hold behavior.
- Failures are never swallowed silently: use `Log.warning/error` (or `Log.orElse` for work allowed to fail). Never log secrets or bodies.
- HTML preview uses JavaFX WebKit (`HtmlPreview.kt`) with a Swing fallback when it cannot start.
- Window size and position persist in `~/.openrest/window.json` (`AppStore.loadWindow/saveWindow`).

## Tests

`shared/src/jvmTest`: logic tests (workflow, import/export, URL codec, logging) and Compose UI tests. Run `./gradlew :shared:jvmTest`.
