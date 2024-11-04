package org.enso.runtime.parser.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An abstract method annotated with this annotation will have generated implementation in the
 * generated class. The types and names of the parameters must correspond to any field (abstract
 * parameterless methods in the interface hierarchy), or to any of the following:
 *
 * <ul>
 *   <li>{@code MetadataStorage passData}
 *   <li>{@code IdentifiedLocation location}
 *   <li>{@code UUID id}
 * </ul>
 *
 * The order of the parameters is not important. Number of parameters must not exceed total number
 * of fields and meta fields.
 *
 * <p>There can be more methods annotated with this annotation. All of them must follow the contract
 * describe in this docs. For each of those methods, an implementation will be generated.
 *
 * <p>The name of the annotated method can be arbitrary, but the convention is to use the {@code
 * copy} name.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface IRCopyMethod {}
