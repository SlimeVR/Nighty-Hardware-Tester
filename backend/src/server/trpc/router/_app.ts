import { TestReportToDto } from "../../dtos/helper";
import { DatabasePagination } from "../../dtos/validation";
import { publicProcedure, router } from "../trpc";

export const appRouter = router({
  reports: publicProcedure
    .input(DatabasePagination)
    .query(async ({ ctx, input }) =>
      ctx.prisma.testReport
        .findMany({
          take: input.limit,
          skip: input.offset,
          include: {
            values: true,
          },
        })
        .then((reports) => reports.map(TestReportToDto))
    ),
});

export type AppRouter = typeof appRouter;
