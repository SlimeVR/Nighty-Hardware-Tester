-- CreateEnum
CREATE TYPE "State" AS ENUM ('Failed', 'Succeeded');

-- AlterTable
ALTER TABLE "TestReport" ADD COLUMN     "state" "State";
