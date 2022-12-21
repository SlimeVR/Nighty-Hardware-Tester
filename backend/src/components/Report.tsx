import clsx from "clsx";
import { FC } from "react";
import { TestReportDto, TestReportValueDto } from "../server/dtos/helper";
import { Chip } from "./Chip";

const ValueComponent: FC<{ value: TestReportValueDto }> = ({ value }) => {
  return (
    <li
      key={value.id}
      className={clsx(
        "rounded-lg border-l-4 bg-transparent px-2 py-1 backdrop-brightness-150 space-y-1",
        value.failed ? "border-l-red-600" : "border-l-green-600"
      )}
    >
      <p>Message: {value.message}</p>
      <p>
        Condition: <Chip text={value.condition} />
      </p>
      <p>
        Value: <Chip text={value.value} />
      </p>
    </li>
  );
};

export const ReportComponent: FC<{ report: TestReportDto }> = ({ report }) => {
  return (
    <div
      key={report.id}
      className="w-4/12-without-gap space-y-1 rounded-lg border border-gray-800 bg-card p-3"
    >
      <p>
        <span className="mr-1">ID:</span>
        <Chip text={report.id} />
      </p>

      <p>
        <span>Values:</span>
        {report.values.length ? (
          <ul className="mt-1 flex flex-col gap-2">
            {report.values.map((error) => (
              <ValueComponent value={error} key={error.id} />
            ))}
          </ul>
        ) : (
          <span className="ml-1 text-yellow-400">No values</span>
        )}
      </p>
    </div>
  );
};
