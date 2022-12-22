import clsx from "clsx";
import { FC } from "react";

export const Chip: FC<{
  text: string;
  preformatted?: boolean;
  monospace?: boolean;
}> = ({ text, preformatted = false, monospace = false }) => (
  <div
    className={clsx(
      "inline-block flex-1 rounded-lg bg-black bg-opacity-30 px-2 py-1",
      monospace && "font-mono",
      preformatted && "whitespace-pre-wrap"
    )}
  >
    {text}
  </div>
);
