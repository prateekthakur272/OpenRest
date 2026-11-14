# Troubleshooting and FAQ

## Where does OpenRest keep my data?

| What | Where |
|---|---|
| Your requests, folders, environments and workflows | In the collection folder you chose |
| Settings | `~/.openrest/settings.json` |
| Open collections, tabs and layout | `~/.openrest/state.json` |
| Global environments | `~/.openrest/environments/` |
| Secret variable values | `~/.openrest/secrets.json` |

Use **Help ▸ Show Data Folder** to open `~/.openrest`. Request history is kept for the current session only.

## The first-run walkthrough comes back

It is shown when `~/.openrest` has no settings yet. Finish or skip it once and it will not return. You can open it again from **Help ▸ Welcome Tour**.

## I want a fresh start

Quit OpenRest and move `~/.openrest` somewhere else. Your collections are not inside it, so they are not affected.

## A `{{variable}}` is red

It is not defined in the active environment, collection, folder or request. Pick the right environment in the header, or open **Variables** to see what is in scope. See [environments and variables](environments-and-variables.md).

## My request works elsewhere but fails here with a certificate error

For a local server with a self-signed certificate, turn off SSL verification in **Preferences** (search for "SSL"). Do not do this for real servers.

## Requests have to go through a proxy

Set it in **Preferences ▸ Proxy** (host and port).

## A change I made in a text editor does not show up

OpenRest reloads collections when the window regains focus. Press the reload button in the sidebar to force it.

## An imported collection looks different

Import maps what both tools share, and anything without an equivalent (for example scripts written for another tool's API) may need adjusting. Please open an issue with a sample so it can be improved.

## The app will not open on macOS ("unidentified developer")

The installer is not notarised yet. Right-click the app and choose **Open**, then confirm once.

## Text is too small or too large

Use ⌘+ / ⌘− (Ctrl on Windows and Linux) or **Preferences ▸ Appearance**. ⌘0 resets it.

## Still stuck?

Open an issue with the OpenRest version, your operating system and the steps to reproduce.
