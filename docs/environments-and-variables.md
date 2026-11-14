# Environments and variables

## Two kinds of variable syntax

| Syntax | Meaning | Where it works |
|---|---|---|
| `{{name}}` | An environment, collection, folder, request or runtime variable | Everywhere: URL, params, headers, body, auth, scripts |
| `{name}` | A value passed on by an earlier [workflow](workflows.md) step | Inside workflows only |

Single braces are only replaced when `name` is a known workflow value, so ordinary JSON bodies are never touched.

## Environments

An **environment** is a named set of variables, such as `Local`, `Staging` or `Production`.

- **Collection environments** belong to one collection and are stored inside it (`environments/<name>.yaml`).
- **Global environments** are available to every collection and live in `~/.openrest/environments`.

Pick the active one of each from the header. Manage them with **Environments** in the header, from **Request ▸ Environment**, or from the command palette.

![Environments](screenshots/environments.png)

### Secrets

Mark a variable as **secret** to keep its value out of the YAML file. The name is saved with the collection; the value is stored in `~/.openrest/secrets.json` on your machine only. That makes it safe to commit a collection to git.

## Where a variable comes from

When the same name is defined in several places, the strongest one wins:

1. Runtime variables set by scripts (`bru.setVar`)
2. Request variables
3. Folder variables
4. Collection environment
5. Collection variables
6. Global environment

The **Variables** button in the header shows every variable in scope and where each value comes from.

## Dynamic and process variables

| Variable | Value |
|---|---|
| `{{$guid}}` or `{{$randomUUID}}` | A new UUID |
| `{{$timestamp}}` | The current Unix time in seconds |
| `{{$isoTimestamp}}` | The current time as an ISO 8601 string |
| `{{$randomInt}}` | A random number from 0 to 999 |
| `{{$randomBoolean}}` | `true` or `false` |
| `{{$randomEmail}}`, `{{$randomFirstName}}`, `{{$randomLastName}}` | Sample names and addresses |
| `{{process.env.NAME}}` | An operating-system environment variable |

A `.env` file in the collection folder is read too, so `{{process.env.API_KEY}}` works without exporting anything.

## Highlighting and autocomplete

In every field, a defined variable is shown in green and a missing one in red. Type `{{` to autocomplete from everything in scope, including the dynamic values above.

## Using variables in scripts

```js
bru.getEnvVar('baseUrl')        // read an environment variable
bru.setVar('token', res.body.token)   // set a runtime variable for later requests
bru.setEnvVar('token', res.body.token) // update the active environment
bru.interpolate('{{baseUrl}}/v2')     // resolve variables in a string
```

More in [Scripting and testing](scripting-and-testing.md).
