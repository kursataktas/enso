package org.enso.runtime.parser.processor;

import javax.lang.model.element.TypeElement;

/**
 * A visitor for traversing the interface hierarchy of an interface - it iterates over all the super
 * interfaces until it encounters {@code org.enso.compiler.ir.IR} interface.
 */
@FunctionalInterface
interface InterfaceHierarchyVisitor<T> {
  /**
   * Visits the interface hierarchy of the given interface.
   *
   * @param iface the interface to visit
   * @param currResult the current result of the previous visit. Can be null if this is the first
   *     visit.
   * @return the result of the visit that may signalize whether the iteration should continue or
   *     stop.
   */
  IterationResult<T> visitInterface(TypeElement iface, T currResult);

  abstract class IterationResult<T> {
    final T value;

    IterationResult(T value) {
      this.value = value;
    }

    abstract boolean shouldStop();
  }

  final class Continue<T> extends IterationResult<T> {

    Continue(T value) {
      super(value);
    }

    @Override
    public boolean shouldStop() {
      return false;
    }
  }

  final class Stop<T> extends IterationResult<T> {

    Stop(T value) {
      super(value);
    }

    @Override
    public boolean shouldStop() {
      return false;
    }
  }
}
