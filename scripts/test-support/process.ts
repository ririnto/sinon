export type ProcessOptions = Readonly<{
  cwd?: string;
  env?: Readonly<Record<string, string | undefined>>;
  stdin?: string;
}>;

export const runProcess = (
  command: readonly string[],
  options: ProcessOptions = {}
): Promise<number> => {
  const child = Bun.spawn([...command], {
    ...(options.cwd === undefined ? {} : { cwd: options.cwd }),
    ...(options.env === undefined ? {} : { env: { ...options.env } }),
    stderr: "ignore",
    stdin: "pipe",
    stdout: "ignore"
  });
  if (options.stdin !== undefined) {
    child.stdin.write(options.stdin);
  }
  child.stdin.end();
  return child.exited;
};
