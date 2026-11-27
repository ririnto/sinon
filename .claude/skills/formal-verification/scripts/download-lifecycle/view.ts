export const title = "Promtool download lifecycle";

export const state = ({
  aggregate,
  catchPhase,
  firstError,
  phase
}: {
  aggregate: "pending" | "fulfilled" | "rejected";
  catchPhase: "waiting" | "draining" | "thrown" | "returned";
  firstError: "none" | "archive" | "checksums";
  phase: Record<
    "archive" | "checksums",
    "request" | "body" | "fulfilled" | "rejected"
  >;
}): string => {
  switch (catchPhase) {
    case "draining": {
      return Object.values(phase).every(
        (status) => status === "fulfilled" || status === "rejected"
      )
        ? "All downloads settled after cancellation request"
        : "Waiting for downloads after cancellation request";
    }
    case "thrown": {
      return "First download error reported";
    }
    case "returned": {
      return "Both downloads returned";
    }
    case "waiting": {
      if (aggregate === "fulfilled") {
        return "Both downloads succeeded";
      }
      if (aggregate === "rejected") {
        return "Download group rejected";
      }
      if (firstError !== "none") {
        return "First download failed";
      }
      return "Downloading";
    }
    default: {
      throw new Error("Unexpected catch phase in the download lifecycle model");
    }
  }
};

export const step = (action: string): string => {
  switch (action) {
    case "Reject": {
      return "Download fails";
    }
    case "AggregateReject": {
      return "Reject download group";
    }
    case "AggregateFulfill": {
      return "Finish both downloads";
    }
    case "Abort": {
      return "Request cancellation";
    }
    case "BodyOk": {
      return "Finish body read";
    }
    case "Throw": {
      return "Report first error";
    }
    case "Return": {
      return "Return both downloads";
    }
    default: {
      return action;
    }
  }
};
