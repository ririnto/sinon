export const title = "Promtool child lifecycle";

export const state = ({
  child,
  deferred,
  interrupted,
  pid,
  result,
  spawnError
}: {
  child: "starting" | "running" | "closed";
  deferred: boolean;
  interrupted: "none" | "INT" | "TERM";
  pid: boolean;
  result: "none" | "error" | "INT" | "TERM" | "code" | "signal";
  spawnError: boolean;
}): string => {
  switch (result) {
    case "error": {
      return "Child error reported";
    }
    case "INT":
    case "TERM": {
      return "First interruption reported";
    }
    case "code": {
      return "Exit code reported";
    }
    case "signal": {
      return "Exit signal reported";
    }
    case "none": {
      if (spawnError) {
        return child === "closed"
          ? "Closed with a child error"
          : "Child error received";
      }
      if (child === "closed") {
        return interrupted === "none" ? "Closed" : "Closed after interruption";
      }
      if (child === "running") {
        return interrupted === "none"
          ? "Running"
          : "Running after interruption";
      }
      if (deferred) {
        return "Waiting to forward the first signal";
      }
      if (interrupted !== "none") {
        return "Starting after interruption";
      }
      return pid ? "Starting with a PID" : "Starting without a PID";
    }
    default: {
      throw new Error("Unexpected result in the child lifecycle model");
    }
  }
};

export const step = (
  action: string,
  from: { pid: boolean; deferred: boolean }
): string => {
  switch (action) {
    case "Signal": {
      return from.pid ? "Forward signal" : "Defer signal";
    }
    case "Spawn": {
      return from.deferred
        ? "Start and forward the first signal"
        : "Start child";
    }
    case "SpawnError": {
      return "Receive child error";
    }
    case "Close": {
      return "Child closes";
    }
    case "Finish": {
      return "Remove listeners and report result";
    }
    default: {
      return action;
    }
  }
};
