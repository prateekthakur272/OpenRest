# OpenRest

An open-source desktop API client (Postman / Bruno style) built with Kotlin + Compose Desktop.
Collections are **plain folders of YAML files**, so they diff cleanly in git, and can be exported to
Postman, Bruno, OpenAPI/Swagger, Insomnia and more.

## File format

```
my-api/
  openrest.yaml          # collection: name, auth, headers, vars, scripts, tests, docs
  environments/dev.yaml  # variables (secret values are stored outside the file)
  Ping.yaml              # a request
  Users/
    folder.yaml          # folder-level headers, auth, vars, scripts
    Get user.yaml
```

```yaml
name: Get user
seq: 1
method: GET
url: '{{baseUrl}}/users/:id?verbose=true'
params:
  query: [{name: verbose, value: 'true'}]
  path:  [{name: id, value: '7'}]
headers:
- {name: Accept, value: application/json}
body:
  type: json            # none | json | text | xml | graphql | formUrlEncoded | multipartForm | file
  json: |-
    {"name": "Ada"}
auth: {type: bearer, token: '{{token}}'}   # omitted = inherit from folder/collection
vars:  {pre: [], post: [{name: id, value: res.body.id}]}
assertions: [{name: res.status, value: eq 200}]
scripts: {pre: "req.setHeader('X-A', '1')"}
tests: |-
  test('ok', () => expect(res.status).to.equal(200));
```

## Run

```bash
./gradlew :desktopApp:run                 # launch
./gradlew :shared:jvmTest                 # core + headless UI tests
./gradlew :desktopApp:packageDmg          # or packageMsi / packageDeb
```

Requires JDK 17+.

## Features

- **Requests**: all HTTP methods, query + path (`:id`) params kept in sync with the URL, headers, auth
  (Basic, Bearer, API key, Digest, OAuth2 client-credentials/password, inherit from folder/collection)
- **Bodies**: JSON, Text, XML, GraphQL (query + variables), Form URL-encoded, Multipart (text + files), raw file
- **Collections as folders**: nested folders, `collection.bru` / `folder.bru` shared headers, auth, vars, scripts, tests
- **Environments** with secret variables (kept out of the files), collection / folder / request / runtime vars,
  `{{var}}` highlighting (green = defined, red = missing), `{{$guid}}`, `{{$timestamp}}`, `{{process.env.X}}` (`.env` supported)
- **Scripting** (GraalJS, async/await ok): `bru.*`, `req.*`, `res.*`, `console`, chai-style `expect`, `test()`
- **Assertions** (`res.status  eq 200`, 25 operators) and **post-response variables**
- **Response viewer**: pretty JSON / XML, raw, image preview, JSONPath filter, headers, cookies, timeline, tests, console, save / copy
- **Code generation**: cURL, fetch, axios, Node, Python, Java, Kotlin, Go, PHP, C#, Ruby, Swift, Rust, Dart, PowerShell, HTTPie, wget
- **Collection runner** for a whole collection or one folder (stop on failure, delay, skip/stop/setNextRequest)
- **Import**: Postman collections + environments, Insomnia, OpenAPI/Swagger (JSON or YAML), cURL (also paste a `curl …` into the URL bar), Bruno folders (converted), OpenRest bundles
- **Export** (collection menu → Export…): Postman v2.1, Bruno (`.bru` folder), OpenAPI 3 / Swagger (YAML or JSON), Insomnia v4, `.http` file (VS Code / JetBrains), cURL shell script, single-file OpenRest bundle
- Cookie jar, proxy, SSL verify toggle, redirects, timeouts, light/dark theme, split layout toggle
- **Developer UX**: command palette (`⌘/Ctrl+K`) for any request or action, `{{variable}}` autocomplete in every field
  (env, collection, folder, runtime vars and `{{$guid}}`-style dynamic ones), request history with full responses,
  status bar, tab context menus (middle-click closes), bulk edit for headers / params / variables, auto-save option,
  stacked or side-by-side layout, resolved-URL preview with undefined-variable warnings, live JSON validation,
  response search, response history with a side-by-side diff, script / test snippets, UI zoom (`⌘/Ctrl +/−/0`),
  open tabs and layout restored on restart
- **Sidebar**: drag & drop to move requests/folders or reorder requests, inline rename (`F2`), keyboard navigation
  (`↑ ↓ ← →`, `Enter`, `F2`, `Delete`), hover quick-add, per-request status / unsaved indicators
- Shortcuts: `⌘/Ctrl+K` palette · `+Enter` send · `+S` save · `+Shift+S` save all · `+L` focus URL · `+B` toggle sidebar ·
  `+1…9` switch tab · `Ctrl+Tab` / `+Shift+[ ]` cycle tabs · `+Shift+C` copy as cURL · `+Shift+L` toggle layout ·
  `+N` new request · `+W` close tab · `+,` preferences

## Layout

```
shared/src/jvmMain/kotlin/dev/prateekthakur/openrest
  core/   Bru format, collections on disk, variables, HTTP, scripting, code-gen, importers, runner
  app/    AppState (tabs, collections, environments, actions)
  ui/     Compose UI (sidebar, request/response panes, dialogs)
desktopApp/  window entry point
```
