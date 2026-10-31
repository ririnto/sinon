import { createElement } from "react";

const Button = () => createElement("button", null, "Continue");

const meta = {
  component: Button,
  title: "Fixture/Button"
};

export default meta;

/**
 * Render a button using the React renderer.
 */
export const Primary = {};
