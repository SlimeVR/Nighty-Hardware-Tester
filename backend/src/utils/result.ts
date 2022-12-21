export type Stringable = { toString(): string };

export type Err<E extends Stringable> = {
  success: false;
  error: E;
};

export type Ok<T> = {
  success: true;
  data: T;
};

export type Result<T, E extends Stringable = string> = Err<E> | Ok<T>;

export const ok = <T>(data: T): Ok<T> => {
  return { success: true, data };
};

export const err = <E extends Stringable>(error: E): Err<E> => {
  return { success: false, error };
};

export const isOk = <T, E extends Stringable>(
  result: Result<T, E>
): result is Ok<T> => {
  return result.success;
};

export const isErr = <T, E extends Stringable>(
  result: Result<T, E>
): result is Err<E> => {
  return !result.success;
};

export const unwrap = <T, E extends Stringable>(
  result: Result<T, E>
): T | never => {
  if (isOk(result)) {
    return result.data;
  } else {
    throw new Error(result.error.toString());
  }
};
