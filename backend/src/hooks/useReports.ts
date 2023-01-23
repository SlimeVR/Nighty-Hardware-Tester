import { inferProcedureInput } from "@trpc/server";
import { useEffect, useState } from "react";
import { TestReportDto, TestReportValueDto } from "../server/dtos/helper";
import { AppRouter } from "../server/trpc/router/_app";
import { trpc } from "../utils/trpc";

export type TestReportValue = Omit<
  TestReportValueDto,
  "startedAt" | "endedAt"
> & {
  startedAt: Date;
  endedAt: Date;
};

export type TestReport = Omit<
  TestReportDto,
  "values" | "startedAt" | "endedAt"
> & {
  values: TestReportValue[];
  startedAt: Date;
  endedAt: Date;
};

export const useReports = (
  input: inferProcedureInput<AppRouter["reports"]>
) => {
  const [reports, setReports] = useState<TestReport[] | undefined>(undefined);
  const unmodifiedReports = trpc.reports.useQuery(input, {
    keepPreviousData: true,
    refetchInterval: 20 * 1000,
  });

  useEffect(() => {
    const r = unmodifiedReports.data;
    console.log(r);

    if (r) {
      const reports = r.map(
        (report): TestReport => ({
          ...report,
          startedAt: new Date(report.startedAt),
          endedAt: new Date(report.endedAt),
          values: report.values.map(
            (value): TestReportValue => ({
              ...value,
              startedAt: new Date(value.startedAt),
              endedAt: new Date(value.endedAt),
            })
          ),
        })
      );

      setReports(reports);
    }
  }, [unmodifiedReports.data]);

  return { ...unmodifiedReports, reports };
};
