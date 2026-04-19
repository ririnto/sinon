#!/usr/bin/env python3
import json
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


def hover_result(uri, position):
    line = position.get("line", 0)
    character = position.get("character", 0)
    return {
        "contents": {
            "kind": "markdown",
            "value": (
                f"Hover from local example LSP.\\n\\n"
                f"- uri: `{uri}`\\n"
                f"- line: `{line}`\\n"
                f"- character: `{character}`"
            ),
        }
    }


def handle_request(message):
    method = message.get("method")
    message_id = message.get("id")

    if method == "initialize":
        return success(
            message_id,
            {
                "capabilities": {
                    "textDocumentSync": 1,
                    "hoverProvider": True,
                },
                "serverInfo": {
                    "name": "example-local-lsp",
                    "version": "0.1.0",
                },
            },
        )

    if method == "initialized":
        return None

    if method == "textDocument/hover":
        params = message.get("params", {})
        text_document = params.get("textDocument", {})
        position = params.get("position", {})
        return success(message_id, hover_result(text_document.get("uri", ""), position))

    if method == "shutdown":
        return success(message_id, None)

    return None


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
