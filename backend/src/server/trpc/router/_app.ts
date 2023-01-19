import { z } from "zod";
import { TestReportToDto } from "../../dtos/helper";
import { DatabasePagination } from "../../dtos/validation";
import { publicProcedure, router } from "../trpc";

export const appRouter = router({
  reports: publicProcedure
    .input(
      z
        .object({
          onlyFailedReports: z.boolean(),
          id: z.string().nullable(),
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
          where: {
            id: input.id === null ? undefined : { search: input.id },
            values: input.onlyFailedReports
              ? { some: { failed: input.onlyFailedReports } }
              : undefined,
          },
        })
        .then((reports) => reports.map(TestReportToDto))
    ),
});

export type AppRouter = typeof appRouter;
