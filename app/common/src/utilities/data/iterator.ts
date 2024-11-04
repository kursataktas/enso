import { mapIterator } from 'lib0/iterator'

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

export function mapIterable<T, U>(it: Iterable<T>, f: (value: T) => U): IterableIterator<U> {
  return mapIterator(it[Symbol.iterator](), f)
}
