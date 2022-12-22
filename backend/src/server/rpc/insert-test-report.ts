import { prisma } from "@/server/db/client";
import { TestReportValidator } from "@/server/dtos/helper";
import { err, ok, Result } from "@/utils/result";
import { NextApiRequest, NextApiResponse } from "next";
import { z } from "zod";

export const InsertTestReportValidator = z.object({
  method: z.enum(["insert_test_report"]),
  params: TestReportValidator,
});

type InsertTestReport = z.infer<typeof InsertTestReportValidator>;

export const handleInsertTestReportRPC = async (
  req: NextApiRequest,
  res: NextApiResponse,
  params: InsertTestReport["params"]
): Promise<Result<{ id: string }, string>> => {
  try {
    const { id } = await prisma.testReport.create({
      data: {
        id: params.id,
        type: params.type,
        values: {
          createMany: {
            data: params.values.map((value) => ({
              step: value.step,
              failed: value.failed,
              condition: value.condition,
              value: value.value,
            })),
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
