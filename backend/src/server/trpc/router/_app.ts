import { z } from "zod";
import { TestReportToDto } from "../../dtos/helper";
import { DatabasePagination } from "../../dtos/validation";
import { publicProcedure, router } from "../trpc";

export const appRouter = router({
  reports: publicProcedure
    .input(
      z
        .object({
          onlyFailedReports: z.boolean().optional(),
          id: z.string().optional(),
        })
        .and(DatabasePagination)
    )
    .query(async ({ ctx, input }) =>
      ctx.prisma.testReport
        .findMany({
          take: input.limit,
          skip: input.offset,
          include: {
            values: true,
          },
          orderBy: {
            testedAt: "desc",
          },
        })
        .then((reports) =>
          reports
            .map(TestReportToDto)
            .filter(
              (report) =>
                !input.id ||
                report.id.toLowerCase().includes(input.id.toLowerCase())
            )
            .filter(
              (report) =>
                !input.onlyFailedReports ||
                report.values.some((value) => value.failed)
            )
        )
    ),
});

export type AppRouter = typeof appRouter;
