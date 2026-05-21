#!/usr/bin/env -S uv run
# /// script
# dependencies = []
# ///

"""Example Model Context Protocol (MCP) server implementation."""

from typing import Any

import json
import os
import sys


def read_message() -> dict[str, Any] | None:
    """Read a message from stdin with MCP headers."""
    headers = {}
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            return None
        if line in (b"\r\n", b"\n"):
            break
        name, value = line.decode("utf-8").split(":", 1)
        headers[name.lower()] = value.strip()
    length = int(headers.get("content-length", "0"))
    if length == 0:
        return None
    body = sys.stdin.buffer.read(length)
    return json.loads(body.decode("utf-8"))


def write_message(message: dict[str, Any]) -> None:
    """Write a message to stdout with MCP headers."""
    encoded = json.dumps(message).encode("utf-8")
    sys.stdout.buffer.write(
        f"Content-Length: {len(encoded)}\r\n\r\n".encode(),
    )
    sys.stdout.buffer.write(encoded)
    sys.stdout.buffer.flush()


def success(message_id: Any, result: Any) -> dict[str, Any]:
    """Create a successful JSON-RPC response."""
    return {"jsonrpc": "2.0", "id": message_id, "result": result}


def error(
    message_id: Any,
    code: int,
    message: str,
) -> dict[str, Any]:
    """Create a JSON-RPC error response."""
    return {
        "jsonrpc": "2.0",
        "id": message_id,
        "error": {"code": code, "message": message},
    }


def tool_definition() -> dict[str, Any]:
    """Return the tool definition for read_plugin_paths."""
    return {
        "name": "read_plugin_paths",
        "description": (
            "Return the plugin root and data directories for local runtime checks."
        ),
        "inputSchema": {
            "type": "object",
            "properties": {},
            "additionalProperties": False,
        },
    }


def tool_result() -> dict[str, Any]:
    """Return the result of the read_plugin_paths tool."""
    plugin_root = os.environ.get("CLAUDE_PLUGIN_ROOT", "")
    plugin_data = os.environ.get("CLAUDE_PLUGIN_DATA", "")
    return {
        "content": [
            {
                "type": "text",
                "text": json.dumps(
                    {
                        "pluginRoot": plugin_root,
                        "pluginData": plugin_data,
                    },
                    indent=2,
                ),
            },
        ],
    }


def handle_request(message: dict[str, Any]) -> dict[str, Any] | None:
    """Handle an MCP request message."""
    method = message.get("method")
    message_id = message.get("id")
    if method == "initialize":
        return success(
            message_id,
            {
                "protocolVersion": "2024-11-05",
                "serverInfo": {"name": "example-local-mcp", "version": "0.1.0"},
                "capabilities": {"tools": {}},
            },
        )
    if method == "notifications/initialized":
        return None
    if method == "tools/list":
        return success(message_id, {"tools": [tool_definition()]})
    if method == "tools/call":
        params = message.get("params", {})
        if params.get("name") != "read_plugin_paths":
            return error(message_id, -32602, "Unknown tool")
        return success(message_id, tool_result())
    if method == "shutdown":
        return success(message_id, {})
    return error(message_id, -32601, f"Unsupported method: {method}")


def main() -> None:
    """Run the MCP server."""
    while True:
        message = read_message()
        if message is None:
            break
        if message.get("method") == "exit":
            break
        response = handle_request(message)
        if response is not None:
            write_message(response)


if __name__ == "__main__":
    main()
