import { FC } from "react";
import { useReports } from "../hooks/useReports";
import { ReportComponent } from "./Report";

export const ReportList: FC<{
  id: string | null;
  onlyFailedReports: boolean;
}> = ({ id, onlyFailedReports }) => {
  const { reports, error, isLoading } = useReports({
    limit: 100,
    offset: 0,
    onlyFailedReports,
    id,
  });

  if (error) {
    return <div>error</div>;
  }

  if (isLoading || !reports) {
    return <div>loading...</div>;
  }

  return (
    <>
      {reports.length === 0 ? (
        <div>No reports found</div>
      ) : (
        reports.map((report) => (
          <ReportComponent report={report} key={report.uuid} />
        ))
      )}
    </>
  );
};
