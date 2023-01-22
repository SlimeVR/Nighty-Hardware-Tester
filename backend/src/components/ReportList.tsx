import { FC } from "react";
import { trpc } from "../utils/trpc";
import { ReportComponent } from "./Report";

export const ReportList: FC<{
  id: string | null;
  onlyFailedReports: boolean;
}> = ({ id, onlyFailedReports }) => {
  const { data: reports, error } = trpc.reports.useQuery(
    {
      limit: 100,
      offset: 0,
      onlyFailedReports,
      id,
    },
    {
      keepPreviousData: true,
      refetchInterval: 20 * 1000,
    }
  );

  if (error) {
    return <div>error</div>;
  }

  if (!reports) {
    return <div>loading...</div>;
  }
  return (
    <>
      {reports.length === 0 && <div>No reports found</div>}
      {reports.map((report) => (
        <ReportComponent report={report} key={report.uuid} />
      ))}
    </>
  );
};
