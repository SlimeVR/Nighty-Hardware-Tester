-- CreateTable
CREATE TABLE "TestReport" (
    "id" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "testedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TestReport_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TestReportValue" (
    "id" TEXT NOT NULL,
    "failed" BOOLEAN NOT NULL,
    "condition" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "message" TEXT NOT NULL,
    "testReportId" TEXT,

    CONSTRAINT "TestReportValue_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "TestReport_id_key" ON "TestReport"("id");

-- CreateIndex
CREATE INDEX "TestReport_type_id_idx" ON "TestReport"("type", "id");

-- CreateIndex
CREATE UNIQUE INDEX "TestReportValue_id_key" ON "TestReportValue"("id");

-- AddForeignKey
ALTER TABLE "TestReportValue" ADD CONSTRAINT "TestReportValue_testReportId_fkey" FOREIGN KEY ("testReportId") REFERENCES "TestReport"("id") ON DELETE SET NULL ON UPDATE CASCADE;
