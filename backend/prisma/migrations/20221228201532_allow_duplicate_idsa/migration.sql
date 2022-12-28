/*
  Warnings:

  - The primary key for the `TestReport` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - The `testReportId` column on the `TestReportValue` table would be dropped and recreated. This will lead to data loss if there is data in the column.
  - A unique constraint covering the columns `[uuid]` on the table `TestReport` will be added. If there are existing duplicate values, this will fail.
  - The required column `uuid` was added to the `TestReport` table with a prisma-level default value. This is not possible if the table is not empty. Please add this column as optional, then populate it before making it required.

*/
-- DropForeignKey
ALTER TABLE "TestReportValue" DROP CONSTRAINT "TestReportValue_testReportId_fkey";

-- DropIndex
DROP INDEX "TestReport_id_key";

-- AlterTable
ALTER TABLE "TestReport" DROP CONSTRAINT "TestReport_pkey",
ADD COLUMN     "uuid" UUID NOT NULL,
ADD CONSTRAINT "TestReport_pkey" PRIMARY KEY ("uuid");

-- AlterTable
ALTER TABLE "TestReportValue" DROP COLUMN "testReportId",
ADD COLUMN     "testReportId" UUID;

-- CreateIndex
CREATE UNIQUE INDEX "TestReport_uuid_key" ON "TestReport"("uuid");

-- AddForeignKey
ALTER TABLE "TestReportValue" ADD CONSTRAINT "TestReportValue_testReportId_fkey" FOREIGN KEY ("testReportId") REFERENCES "TestReport"("uuid") ON DELETE SET NULL ON UPDATE CASCADE;
