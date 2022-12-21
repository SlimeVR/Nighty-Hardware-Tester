import type { NextPage } from "next";
import { ReportComponent } from "../components/Report";
import { trpc } from "../utils/trpc";

const Home: NextPage = () => {
  const { data: reports, error } = trpc.reports.useQuery({
    limit: 100,
    offset: 0,
  });

  if (error) {
    return <div>error</div>;
  }

  if (!reports) {
    return <div>loading...</div>;
  }

  return (
    <div className="flex flex-wrap gap-4">
      {reports.map((report) => (
        <ReportComponent report={report} key={report.id} />
      ))}
    </div>
  );
};

export default Home;
