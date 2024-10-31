package org.enso.runtime.parser.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An interface annotated with this annotation will be processed by the IR processor. The processor
 * will generate a class that extends this interface. The generated class will have the same package
 * as this interface, and its name will have the "Gen" suffix. The interface must be a subtype of
 * {@code org.enso.compiler.ir.IR}. The interface can contain {@link IRChild} and {@link
 * IRCopyMethod} annotated methods.
 *
 * <p>For every abstract parameterless method of the interface, there will be a field in the
 * generated class.
 *
 * <p>The interface can contain arbitrary number of nested interfaces. In such case, the processor
 * will generate nested static classes for all these nested interfaces.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface IRNode {}
