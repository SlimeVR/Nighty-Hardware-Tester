-- AlterTable
ALTER TABLE "TestReport" ADD COLUMN     "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE "TestReport" SET "startedAt" = "testedAt";
