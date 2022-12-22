import { TestReport, TestReportValue } from "@prisma/client";
import { z } from "zod";

export const TestReportValueValidator = z.object({
  step: z.string(),
  condition: z.string(),
  value: z.string(),
  failed: z.boolean(),
  logs: z.string().nullable(),
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
});

export const TestReportValidator = z.object({
  id: z.string(),
  type: z.string(),
  values: z.array(TestReportValueValidator),
});
export type TestReportDto = Omit<
  z.infer<typeof TestReportValidator>,
  "values"
> & {
  values: TestReportValueDto[];
};
export const TestReportToDto = (
  report: TestReport & { values: TestReportValue[] }
): TestReportDto => ({
  id: report.id,
  type: report.type,
  values: report.values.map(TestReportValueToDto),
});
