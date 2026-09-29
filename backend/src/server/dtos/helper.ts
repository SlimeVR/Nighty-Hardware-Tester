import { State, TestReport, TestReportValue } from "@prisma/client";
import { z } from "zod";

export const TestReportValueValidator = z.object({
  step: z.string().trim().min(1),
  condition: z.string().trim().min(1),
  value: z.string().trim().min(1),
  failed: z.boolean(),
  logs: z.string().nullable(),
  startedAt: z.string().datetime(),
  endedAt: z.string().datetime(),
});
export type TestReportValueDto = z.infer<typeof TestReportValueValidator> & {
  id: string;
};
export const TestReportValueToDto = (
  value: TestReportValue
): TestReportValueDto => ({
  id: value.id,
  step: value.step,
  condition: value.condition,
  value: value.value,
  failed: value.failed,
  logs: value.logs,
  startedAt: value.startedAt.toISOString(),
  endedAt: value.endedAt.toISOString(),
});

export const TestReportValidator = z.object({
  id: z.string().trim().min(1),
  type: z.string().trim().min(1),
  tester: z.string().trim().min(1),
  values: z.array(TestReportValueValidator),
  state: z.enum(["failed", "succeeded"]).nullish(),
  startedAt: z.string().datetime(),
  endedAt: z.string().datetime(),
});
export type TestReportStateDto = "failed" | "succeeded";
export const TestReportStateToPrisma = (
  state: TestReportStateDto | null | undefined
): State | null =>
  state === "failed"
    ? State.Failed
    : state === "succeeded"
    ? State.Succeeded
    : null;

export type TestReportDto = Omit<
  z.infer<typeof TestReportValidator>,
  "values" | "state"
> & {
  uuid: string;
  values: TestReportValueDto[];
  state: TestReportStateDto;
};
export const TestReportToDto = (
  report: TestReport & { values: TestReportValue[] }
): TestReportDto => {
  const noValuesFailed =
    report.state === null && !report.values.some((v) => v.failed);
  const state =
    report.state === State.Succeeded || noValuesFailed ? "succeeded" : "failed";

  return {
    uuid: report.uuid,
    id: report.id,
    type: report.type,
    tester: report.tester,
    values: report.values.map(TestReportValueToDto),
    state,
    startedAt: report.startedAt.toISOString(),
    endedAt: report.endedAt.toISOString(),
  };
};
