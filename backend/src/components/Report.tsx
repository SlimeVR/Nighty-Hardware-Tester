import { Disclosure } from "@headlessui/react";
import clsx from "clsx";
import { FC } from "react";
import { HiChevronDown, HiChevronUp } from "react-icons/hi2";
import { TestReportDto, TestReportValueDto } from "../server/dtos/helper";
import { Chip } from "./Chip";

const ValueComponent: FC<{ value: TestReportValueDto }> = ({ value }) => {
  return (
    <div
      className={clsx(
        "space-y-1 rounded-lg border-l-4 bg-transparent backdrop-brightness-150",
        value.failed ? "border-l-red-600" : "border-l-green-600"
      )}
      key={value.id}
    >
      <Disclosure>
        {({ open }) => (
          <>
            <Disclosure.Button className="flex w-full justify-between p-2 outline-none">
              <div className="w-fit self-start">{value.step}</div>

              <div className="w-fit self-center">
                {open ? (
                  <HiChevronUp className="text-white" />
                ) : (
                  <HiChevronDown className="text-white" />
                )}
              </div>
            </Disclosure.Button>
            <Disclosure.Panel className="space-y-1 p-2 pt-0">
              <div className="flex gap-2">
                <span className="py-1">Condition:</span>
                <Chip text={value.condition} monospace />
              </div>
              <div className="flex gap-2">
                <span className="py-1">Value:</span>
                <Chip text={value.value} preformatted monospace />
              </div>
              {value.logs && (
                <div className="flex gap-2">
                  <span className="py-1">Logs:</span>
                  <Chip text={value.logs} preformatted monospace />
                </div>
              )}
            </Disclosure.Panel>
          </>
        )}
      </Disclosure>
    </div>
  );
};

export const ReportComponent: FC<{ report: TestReportDto }> = ({ report }) => {
  return (
    <div
      className={clsx(
        "space-y-1 rounded-lg border border-l-4 border-gray-800 bg-card",
        report.values.some((v) => v.failed)
          ? "border-l-red-600"
          : "border-l-green-600"
      )}
      key={report.id}
    >
      <Disclosure>
        {({ open }) => (
          <>
            <Disclosure.Button className="flex w-full gap-4 p-2 text-left outline-none">
              <div className="flex flex-1 gap-1">
                <div className="w-fit">
                  <Chip text={report.type} color="green" />
                </div>

                <div className="w-fit">
                  <Chip text={report.id} monospace />
                </div>
              </div>

              <div className="w-fit self-center">
                {new Date(report.testedAt).toLocaleString()}
              </div>

              <div className="w-fit self-center">
                {open ? (
                  <HiChevronUp className="text-white" />
                ) : (
                  <HiChevronDown className="text-white" />
                )}
              </div>
            </Disclosure.Button>
            <Disclosure.Panel className="p-2 pt-0">
              {report.values.length ? (
                <ul className="mt-1 flex flex-col gap-2">
                  {report.values.map((value) => (
                    <ValueComponent value={value} key={value.id} />
                  ))}
                </ul>
              ) : (
                <span className="ml-1 text-yellow-400">No values</span>
              )}
            </Disclosure.Panel>
          </>
        )}
      </Disclosure>
    </div>
  );
};
