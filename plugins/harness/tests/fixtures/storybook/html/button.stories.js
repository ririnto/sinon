const button = () => {
  const element = document.createElement("button");
  element.textContent = "Continue";
  return element;
};

const meta = {
  render: button,
  title: "Fixture/Button"
};

export default meta;

/**
 * Render a button using the HTML renderer.
 */
export const Primary = {};
