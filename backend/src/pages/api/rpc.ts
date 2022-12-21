import type { NextApiRequest, NextApiResponse } from "next";
import { env } from "../../env/server.mjs";
import { handleInsertTestReportRPC } from "../../server/rpc/insert-test-report";

const rpcApi = async (req: NextApiRequest, res: NextApiResponse) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  if (req.headers.authorization !== env.RPC_PASSWORD) {
    res.status(401).json({ error: "Unauthorized" });
    return;
  }
  console.log(JSON.stringify(req.body, null, "\t"));

  const { method, params } = req.body;
  switch (method) {
    case "insert_test_report": {
      const a = await handleInsertTestReportRPC(req, res, params);
      if (a.success) {
        res.status(200).json(a.data);
      } else {
        res.status(400).json({ error: a.error });
      }
      break;
    }
    default: {
      res.status(400).json({ error: "Bad request" });
      break;
    }
  }
};

export default rpcApi;
