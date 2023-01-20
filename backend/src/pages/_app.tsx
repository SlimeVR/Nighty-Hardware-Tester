import "@fontsource/poppins/400.css";
import "@fontsource/roboto-mono/400.css";
import type { AppType } from "next/app";
import "../styles/globals.css";
import { trpc } from "../utils/trpc";

const MyApp: AppType = ({ Component, pageProps }) => {
  return (
    <div className="h-full overflow-y-scroll bg-bg p-2 text-white">
      <Component {...pageProps} />
    </div>
  );
};

export default trpc.withTRPC(MyApp);
