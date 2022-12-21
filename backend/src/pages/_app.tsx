import type { AppType } from "next/app";
import "../styles/globals.css";
import { trpc } from "../utils/trpc";

const MyApp: AppType = ({ Component, pageProps }) => {
  return (
    <div className="bg-bg text-white min-h-full p-2">
      <Component {...pageProps} />
    </div>
  );
};

export default trpc.withTRPC(MyApp);
