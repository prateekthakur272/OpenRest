# Contributing to OpenRest

Thanks for helping. This page covers how to set up, how the code is organised, what we expect from a change and how to send it.

## Ways to help

- **Report a bug** with the steps to reproduce, what you expected, what happened, your operating system, and the OpenRest version (**About OpenRest**). A screenshot or a (sanitised) request file helps a lot.
- **Suggest a feature** by describing the problem you are trying to solve before the solution.
- **Improve the docs**: fix a typo, add an example, clarify a step. Docs live in `docs/`.
- **Send a pull request** for a fix or a feature.

## Setting up

1. Install **JDK 17 or newer**.
2. Fork and clone the repository.
3. Run the app: `./gradlew :desktopApp:run`
4. Run the tests: `./gradlew :shared:jvmTest`

Open the project in IntelliJ IDEA (the Kotlin plugin is bundled) as a Gradle project.

## Finding your way around

Read [project-structure.md](project-structure.md) first. The short version:

- `features/<name>/` for a part of the app a user can point at, `components/` for reusable widgets, `utils/` for pure helpers, `core/` for logic with no UI, `app/AppState.kt` for state.
- A new reusable widget goes in `components/`. A new pure helper goes in `utils/`. Prefer reusing what is there over writing a second copy.

## Conventions

**Kotlin**

- Follow the existing style (`kotlin.code.style=official`), four-space indentation.
- Keep files focused. If a file passes a few hundred lines or mixes two responsibilities, split it by responsibility into the same folder.
- Declarations are `private` unless another file needs them, then `internal`. Make something public only when another feature needs it.
- Comments explain *why*, not *what*. Public functions that are not obvious get a one-line KDoc.

**Compose UI**

- Use the palette (`LocalPal.current`) and the shared components (`T`, `Btn`, `Field`, `ModalBox`, `Tabs`) rather than raw Material widgets, so the look stays consistent in light and dark.
- Give interactive elements a `testTag` when a test needs to find them.
- Keep state in `AppState` or a small session class, not scattered in composables, if more than one place reads it.
- Keep the UI usable by keyboard.

**Files and data**

- Never change the on-disk YAML format in a way that breaks existing collections. Add optional fields; keep reading old ones.
- Secrets never go in collection files.

**Documentation**

- A user-visible change should update the matching page in `docs/` and, if it adds a feature, [features.md](features.md).
- Menus and shortcuts changes should update [keyboard-shortcuts.md](keyboard-shortcuts.md).

## Tests

```bash
./gradlew :shared:jvmTest
```

Tests are in `shared/src/jvmTest/kotlin/dev/prateekthakur/openrest`.

- **Logic** (parsers, importers, scripts, workflows) is tested with plain unit tests and, where HTTP is needed, a throw-away `com.sun.net.httpserver.HttpServer` on a random local port.
- **UI** is tested headlessly with `runDesktopComposeUiTest`. Find elements by `testTag` or text, drive them with `performClick`, and assert on `AppState`.
- Tests that read or write settings are isolated: Gradle points `openrest.home` at a throw-away folder, so tests never touch your real `~/.openrest`.
- Add a test with every bug fix, using the failing case.

If your change affects the screenshots in the docs, regenerate them (see [building-and-releasing.md](building-and-releasing.md#regenerating-the-screenshots)).

## Pull requests

1. Create a branch from `main`: `git checkout -b fix/short-description`.
2. Keep the change focused. Separate refactors from behaviour changes.
3. Make sure `./gradlew :shared:jvmTest` passes.
4. Write a clear description: what changed, why, how you tested it, and a before/after screenshot for UI changes.
5. Be ready to iterate on review comments.

### Commit messages

A short imperative summary, then a blank line and the reason if it is not obvious:

```
Fix path parameters lost when the URL is edited

The query string was re-parsed and dropped :name segments.
```

## Reporting security problems

Please do not open a public issue for a security problem. Contact the maintainers privately and give them time to fix it before disclosure.

## Code of conduct

Be kind and assume good intent. Critique code, not people. Harassment of any kind is not tolerated.
