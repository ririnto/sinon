---
description: >-
  agent-desktop desktop observation and automation MCP integration for Claude Code and Codex.
metadata:
  reference:
    Agent Plugins:
      version: 1.0.0
      url: https://agent-plugins.org/specification.md
    Claude Code plugin manifests:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    agent-desktop:
      url:
        - https://github.com/vercel-labs/agent-desktop
        - https://www.npmjs.com/package/agent-desktop
    Bun:
      url:
        - https://bun.sh/docs/cli/bunx
        - https://bun.sh/docs/installation/cache
---

# agent-desktop

agent-desktop provides desktop observation and automation through an MCP server.
The server runs over stdio and exposes the `agent-desktop` CLI commands as MCP tools.
Each tool mirrors one CLI command and returns the command's JSON envelope.

## Runtime

The plugin runs the published `agent-desktop` npm package through `bunx`.
The runtime requires `bun` on `PATH`.
The package bundles native binaries for macOS, Windows, and Linux.
Bun's default policy blocks package lifecycle scripts, so the server starts through the package's `node`-shebang launcher, which Bun executes with its own runtime.
A separate Node.js installation is not required.
`bunx` reuses its cached package installation between launches.
Run `bun pm cache rm` when you want `bunx` to resolve the package version again.

## Install And Use

Install the plugin from the Sinon marketplace in Claude Code:

```sh
claude plugin marketplace add ririnto/sinon
claude plugin install agent-desktop@sinon
```

For local development, load the package in a new session:

```sh
claude --plugin-dir ./plugins/agent-desktop
```

In Codex, install agent-desktop from the Sinon repository marketplace and trust the MCP server.
Start a new session after installation.

The `desktop_status` tool reports adapter health, platform information, and permission state.
The `desktop_permissions` tool reports the nested accessibility, screen recording, and automation permission states.
See the upstream documentation for platform permission setup and supported surfaces.

## MCP Tools

The upstream server exposes these 67 tools:

| Tool | Description |
| --- | --- |
| `desktop_snapshot` | Capture the accessibility tree as structured JSON with ref IDs. |
| `desktop_find` | Search elements by role, name, value, or text content. |
| `desktop_screenshot` | Take a PNG screenshot of an application window. |
| `desktop_get` | Read an element property. |
| `desktop_is` | Check an element state. |
| `desktop_click` | Click an element through the accessibility press action. |
| `desktop_double_click` | Physically double-click an element. |
| `desktop_triple_click` | Physically triple-click an element. |
| `desktop_right_click` | Open a context menu semantically or physically. |
| `desktop_type` | Insert text into a text target. |
| `desktop_set_value` | Set an element value through the accessibility attribute. |
| `desktop_clear` | Clear an element value. |
| `desktop_focus` | Set keyboard focus on an element. |
| `desktop_select` | Select an option in a list or dropdown. |
| `desktop_toggle` | Toggle a checkbox or switch. |
| `desktop_check` | Set a checkbox or switch to the checked state. |
| `desktop_uncheck` | Set a checkbox or switch to the unchecked state. |
| `desktop_expand` | Expand a disclosure control or tree item. |
| `desktop_collapse` | Collapse a disclosure control or tree item. |
| `desktop_scroll` | Scroll an element in a direction by an amount. |
| `desktop_scroll_to` | Scroll an element into the visible area. |
| `desktop_press` | Send a key combination. |
| `desktop_key_down` | Hold a key or modifier down, which fails closed in stateless mode. |
| `desktop_key_up` | Release a held key or modifier, which fails closed in stateless mode. |
| `desktop_hover` | Move the cursor to an element center or coordinates. |
| `desktop_drag` | Drag from one element or point to another. |
| `desktop_mouse_move` | Move the cursor to absolute screen coordinates. |
| `desktop_mouse_click` | Click at absolute screen coordinates. |
| `desktop_mouse_down` | Press a mouse button at coordinates, which fails closed in stateless mode. |
| `desktop_mouse_up` | Release the mouse button at coordinates, which fails closed in stateless mode. |
| `desktop_mouse_wheel` | Scroll the mouse wheel at absolute coordinates. |
| `desktop_launch` | Launch an application and return once its process is running. |
| `desktop_close_app` | Quit an application gracefully or terminate it forcefully. |
| `desktop_list_windows` | List visible windows. |
| `desktop_list_displays` | List connected displays with bounds and scale factor. |
| `desktop_list_apps` | List running GUI applications. |
| `desktop_focus_window` | Bring a window to the front and confirm focus. |
| `desktop_resize_window` | Resize an application window. |
| `desktop_move_window` | Move an application window. |
| `desktop_minimize` | Minimize an application window. |
| `desktop_maximize` | Maximize or zoom an application window. |
| `desktop_restore` | Restore a minimized or maximized window. |
| `desktop_list_surfaces` | List accessibility surfaces for an application. |
| `desktop_open_system_surface` | Open a shell surface and return its snapshot. |
| `desktop_list_notifications` | List notifications from the notification center. |
| `desktop_dismiss_notification` | Dismiss a notification by index. |
| `desktop_dismiss_all_notifications` | Dismiss all notifications. |
| `desktop_notification_action` | Click an action button on a notification. |
| `desktop_clipboard_get` | Read clipboard contents. |
| `desktop_clipboard_set` | Write text to the clipboard. |
| `desktop_clipboard_clear` | Clear the clipboard. |
| `desktop_wait` | Wait for a time, element, text, or window. |
| `desktop_status` | Show adapter health, platform information, and permission state. |
| `desktop_permissions` | Check the accessibility, screen recording, and automation permission states. |
| `desktop_version` | Show the version, target architecture, and OS. |
| `desktop_batch` | Execute a bounded, sequential JSON command batch. |
| `desktop_skills_list` | List the bundled skills. |
| `desktop_skills_get` | Print a bundled skill. |
| `desktop_skills_path` | Print where the bundled skills live. |
| `desktop_session_start` | Create a session directory and manifest. |
| `desktop_session_end` | Seal a session manifest. |
| `desktop_session_list` | List session manifests. |
| `desktop_session_gc` | Remove ended or stale sessions. |
| `desktop_cursor_overlay_enable` | Enable the cursor overlay for the selected session. |
| `desktop_cursor_overlay_disable` | Disable the cursor overlay for the selected session. |
| `desktop_trace_show` | Merge session trace segments into a bounded JSON timeline. |
| `desktop_trace_export` | Export a self-contained HTML trace viewer. |

## Package Contents

- The Sinon Claude marketplace entry declares the plugin metadata.
- `.codex-plugin/plugin.json` declares the Codex plugin.
- `.mcp.json` registers the Claude Code MCP server.
- `mcp.json` registers the portable MCP server configuration.
