import { z } from "zod";

export const MACAddressValidator = z
  .string()
  .regex(/^([0-9a-f]{1,2}[\.:-]){5}([0-9a-f]{1,2})$/i);

export const DatabasePagination = z.object({
  limit: z.number().positive().max(100).default(100),
  offset: z.number().nonnegative().default(0),
});
