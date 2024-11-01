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
   * @return If not-null, the iteration is stopped and the value is returned. If null, the iteration
   *     continues.
   */
  T visitInterface(TypeElement iface);
}
