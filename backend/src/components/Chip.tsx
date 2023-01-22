import clsx from "clsx";
import { FC } from "react";

export const Chip: FC<{
  text: string;
  preformatted?: boolean;
  monospace?: boolean;
  color?: "black" | "green";
}> = ({ text, preformatted, monospace, color }) => {
  if (!color) {
    if (monospace) {
      color = "black";
    } else {
      color = "green";
    }
  }

  return (
    <div
      className={clsx(
        "inline-block flex-1 rounded-lg bg-opacity-30 px-2 py-1",
        monospace && "font-mono",
        color === "black" && "bg-black",
        color === "green" && "bg-green-600 text-white",
        preformatted && "whitespace-pre-wrap"
      )}
    >
      {text}
    </div>
  );
};
