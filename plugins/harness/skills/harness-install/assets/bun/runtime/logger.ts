/**
 * Structured CLI logger that writes to stdout or stderr.
 */
export const logger = {
	error: (msg: string): void => {
		process.stderr.write(`${msg}\n`);
	},
	warn: (msg: string): void => {
		process.stderr.write(`${msg}\n`);
	},
	info: (msg: string): void => {
		process.stdout.write(`${msg}\n`);
	},
	log: (msg: string): void => {
		process.stdout.write(`${msg}\n`);
	},
};
