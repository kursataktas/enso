export function reduce<T, A>(
  iterator: Iterator<T>,
  f: (accumulator: A, element: T) => A,
  initialAccumulator: A,
): A {
  let accumulator = initialAccumulator
  let result = iterator.next()
  while (!result.done) {
    accumulator = f(accumulator, result.value)
    result = iterator.next()
  }
  return accumulator
}

export function intoCount(it: Iterator<unknown>): number {
  return reduce(it, a => a + 1, 0)
}
