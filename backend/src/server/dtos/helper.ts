import { TestReport, TestReportValue } from "@prisma/client";
import { z } from "zod";

export const TestReportValueValidator = z.object({
  id: z.string(),
  failed: z.boolean(),
  condition: z.string(),
  value: z.string(),
  message: z.string(),
});
export type TestReportValueDto = z.infer<typeof TestReportValueValidator>;
export const TestReportValueToDto = (value: TestReportValue) => ({
  id: value.id,
  failed: value.failed,
  condition: value.condition,
  value: value.value,
  message: value.message,
});

export const TestReportValidator = z.object({
  id: z.string(),
  type: z.string(),
  values: z.array(TestReportValueValidator),
});
export type TestReportDto = z.infer<typeof TestReportValidator>;
export const TestReportToDto = (
  report: TestReport & { values: TestReportValue[] }
) => ({
  id: report.id,
  type: report.type,
  values: report.values.map(TestReportValueToDto),
});
