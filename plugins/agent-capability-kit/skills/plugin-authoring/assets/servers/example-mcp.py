#!/usr/bin/env python3
import json
import os
import sys


def read_message():
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


def write_message(message):
    encoded = json.dumps(message).encode("utf-8")
    sys.stdout.buffer.write(f"Content-Length: {len(encoded)}\r\n\r\n".encode("utf-8"))
    sys.stdout.buffer.write(encoded)
    sys.stdout.buffer.flush()


def success(message_id, result):
    return {"jsonrpc": "2.0", "id": message_id, "result": result}


def error(message_id, code, message):
    return {
        "jsonrpc": "2.0",
        "id": message_id,
        "error": {"code": code, "message": message},
    }


def tool_definition():
    return {
        "name": "read_plugin_paths",
        "description": "Return the plugin root and data directories for local runtime checks.",
        "inputSchema": {
            "type": "object",
            "properties": {},
            "additionalProperties": False,
        },
    }


def tool_result():
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
            }
        ]
    }


def handle_request(message):
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


def main():
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
