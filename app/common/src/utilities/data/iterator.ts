import { mapIterator } from 'lib0/iterator'

/** Similar to {@link Array.prototype.reduce|}, but consumes elements from an iterator instead of an Array. */
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

/** Consumes the provided iterator, returning the number of elements it yielded. */
export function intoCount(it: Iterator<unknown>): number {
  return reduce(it, a => a + 1, 0)
}

/** @returns An iterator that yields the results of applying the given function to each value of the given iterable. */
export function mapIterable<T, U>(it: Iterable<T>, f: (value: T) => U): IterableIterator<U> {
  return mapIterator(it[Symbol.iterator](), f)
}
