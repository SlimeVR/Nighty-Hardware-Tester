import clsx from "clsx";
import debounce from "lodash.debounce";
import type { NextPage } from "next";
import { useMemo, useState } from "react";
import { HiCheck, HiOutlineXMark } from "react-icons/hi2";
import { ReportList } from "../components/ReportList";

const Home: NextPage = () => {
  const [onlyFailedReports, setOnlyFailedReports] = useState(false);
  const [id, setId] = useState<string | null>(null);

  const setIdInner = (q: string) => setId(q === "" ? null : q);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const debouncedSetSearch = useMemo(() => debounce(setIdInner, 250), [setId]);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2">
        <div className="flex-1">
          <input
            type="text"
            placeholder="ID"
            className="w-full rounded-lg border border-transparent bg-blue-300 bg-opacity-10 px-2 py-1 outline-none transition-all hover:border-gray-600 focus:border-blue-600 focus:hover:border-blue-500"
            onChange={(e) => debouncedSetSearch(e.target.value)}
          />
        </div>

        <div className="flex w-fit gap-2">
          <button
            onClick={() => setOnlyFailedReports(!onlyFailedReports)}
            className={clsx(
              "flex h-full items-center gap-1 rounded-lg border border-transparent px-2 py-1 transition-all",
              onlyFailedReports
                ? "border-blue-600 bg-blue-300 bg-opacity-20 text-white hover:border-blue-500"
                : "bg-blue-300 bg-opacity-10 text-white hover:border-gray-600"
            )}
          >
            <span>{onlyFailedReports ? <HiCheck /> : <HiOutlineXMark />}</span>
            <p>Only failed reports</p>
          </button>
        </div>
      </div>

      <ReportList id={id} onlyFailedReports={onlyFailedReports} />
    </div>
  );

  // return (
  //   <div className="flex flex-wrap gap-4">
  //   </div>
  // );
};

export default Home;
