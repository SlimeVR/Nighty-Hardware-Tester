import { FC } from "react";

export const Chip: FC<{ text: string }> = ({ text }) => (
  <span className="rounded-lg bg-opacity-30 bg-black px-2 py-1 font-mono">{text}</span>
);
