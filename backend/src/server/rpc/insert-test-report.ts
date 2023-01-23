import { prisma } from "@/server/db/client";
import { TestReportValidator } from "@/server/dtos/helper";
import { err, ok, Result } from "@/utils/result";
import { TestReportValue } from "@prisma/client";
import { NextApiRequest, NextApiResponse } from "next";
import { z } from "zod";

export const InsertTestReportValidator = z.object({
  method: z.enum(["insert_test_report"]),
  params: TestReportValidator,
});

export const handleInsertTestReportRPC = async (
  req: NextApiRequest,
  res: NextApiResponse,
  raw: unknown
): Promise<Result<{ id: string }, string>> => {
  try {
    const params = TestReportValidator.parse(raw);

    const { id } = await prisma.testReport.create({
      data: {
        id: params.id,
        type: params.type,
        startedAt: new Date(params.startedAt),
        endedAt: new Date(params.endedAt),
        values: {
          createMany: {
            data: params.values.map(
              (value): Omit<Omit<TestReportValue, "id">, "testReportId"> => ({
                step: value.step,
                failed: value.failed,
                condition: value.condition,
                value: value.value,
                logs: value.logs,
                startedAt: new Date(value.startedAt),
                endedAt: new Date(value.endedAt),
              })
            ),
          },
        },
      },
      select: {
        id: true,
      },
    });

    return ok({ id });
  } catch (e) {
    return err((e as Error).message);
  }
};
