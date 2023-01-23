-- AlterTable
ALTER TABLE "TestReportValue" ADD COLUMN     "endedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN     "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- CreateIndex
CREATE INDEX "TestReportValue_testReportId_idx" ON "TestReportValue"("testReportId");
