import type { AppType } from "next/app";
import "../styles/globals.css";
import { trpc } from "../utils/trpc";

const MyApp: AppType = ({ Component, pageProps }) => {
  return (
    <div className="h-full bg-bg p-2 text-white overflow-y-scroll">
      <Component {...pageProps} />
    </div>
  );
};

export default trpc.withTRPC(MyApp);
