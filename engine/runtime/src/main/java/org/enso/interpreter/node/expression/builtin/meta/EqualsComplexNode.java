package org.enso.interpreter.node.expression.builtin.meta;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.enso.interpreter.node.expression.builtin.interop.syntax.HostValueToEnsoNode;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.Module;
import org.enso.interpreter.runtime.callable.UnresolvedConversion;
import org.enso.interpreter.runtime.callable.UnresolvedSymbol;
import org.enso.interpreter.runtime.data.EnsoFile;
import org.enso.interpreter.runtime.data.Type;
import org.enso.interpreter.runtime.data.atom.Atom;
import org.enso.interpreter.runtime.data.hash.EnsoHashMap;
import org.enso.interpreter.runtime.data.hash.HashMapInsertAllNode;
import org.enso.interpreter.runtime.library.dispatch.TypesLibrary;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.warning.WarningsLibrary;

@GenerateUncached
abstract class EqualsComplexNode extends Node {

  static EqualsComplexNode build() {
    return EqualsComplexNodeGen.create();
  }

  abstract EqualsAndInfo execute(VirtualFrame frame, Object left, Object right);

  /** Enso specific types */
  @Specialization
  EqualsAndInfo equalsUnresolvedSymbols(
      VirtualFrame frame,
      UnresolvedSymbol self,
      UnresolvedSymbol otherSymbol,
      @Shared("equalsNode") @Cached EqualsNode equalsNode) {
    if (!self.getName().equals(otherSymbol.getName())) {
      return EqualsAndInfo.FALSE;
    } else {
      return equalsNode.execute(frame, self.getScope(), otherSymbol.getScope());
    }
  }

  @Specialization
  EqualsAndInfo equalsUnresolvedConversion(
      VirtualFrame frame,
      UnresolvedConversion selfConversion,
      UnresolvedConversion otherConversion,
      @Shared("equalsNode") @Cached EqualsNode equalsNode) {
    return equalsNode.execute(frame, selfConversion.getScope(), otherConversion.getScope());
  }

  @Specialization
  EqualsAndInfo equalsModuleScopes(ModuleScope selfModuleScope, ModuleScope otherModuleScope) {
    return EqualsAndInfo.valueOf(selfModuleScope == otherModuleScope);
  }

  @Specialization
  EqualsAndInfo equalsModules(Module selfModule, Module otherModule) {
    return EqualsAndInfo.valueOf(selfModule == otherModule);
  }

  @Specialization
  EqualsAndInfo equalsFiles(
      VirtualFrame frame,
      EnsoFile selfFile,
      EnsoFile otherFile,
      @Shared("equalsNode") @Cached EqualsNode equalsNode) {
    return equalsNode.execute(frame, selfFile.getPath(), otherFile.getPath());
  }

  /**
   * There is no specialization for {@link TypesLibrary#hasType(Object)}, because also primitive
   * values would fall into that specialization, and it would be too complicated to make that
   * specialization disjunctive. So we rather specialize directly for {@link Type types}.
   */
  @Specialization(guards = {"typesLib.hasType(selfType)", "typesLib.hasType(otherType)"})
  EqualsAndInfo equalsTypes(
      VirtualFrame frame,
      Type selfType,
      Type otherType,
      @Shared("equalsNode") @Cached EqualsNode equalsNode,
      @Shared("typesLib") @CachedLibrary(limit = "10") TypesLibrary typesLib) {
    return equalsNode.execute(
        frame, getQualifiedTypeName(selfType), getQualifiedTypeName(otherType));
  }

  @TruffleBoundary
  private static String getQualifiedTypeName(Type type) {
    return type.getQualifiedName().toString();
  }

  /**
   * If one of the objects has warnings attached, just treat it as an object without any warnings.
   */
  @Specialization(
      guards = {
        "selfWarnLib.hasWarnings(selfWithWarnings) || otherWarnLib.hasWarnings(otherWithWarnings)"
      },
      limit = "3")
  EqualsAndInfo equalsWithWarnings(
      VirtualFrame frame,
      Object selfWithWarnings,
      Object otherWithWarnings,
      @CachedLibrary("selfWithWarnings") WarningsLibrary selfWarnLib,
      @CachedLibrary("otherWithWarnings") WarningsLibrary otherWarnLib,
      @Shared("equalsNode") @Cached EqualsNode equalsNode,
      @Cached HashMapInsertAllNode insertAllNode) {
    try {
      var all = EnsoHashMap.empty();
      var max = EnsoContext.get(this).getWarningsLimit();
      Object self = selfWithWarnings;
      Object other = otherWithWarnings;
      if (selfWarnLib.hasWarnings(selfWithWarnings)) {
        self = selfWarnLib.removeWarnings(selfWithWarnings);
        var toAdd = selfWarnLib.getWarnings(selfWithWarnings, false);
        all = insertAllNode.executeInsertAll(frame, all, toAdd, max);
      }
      if (otherWarnLib.hasWarnings(otherWithWarnings)) {
        other = otherWarnLib.removeWarnings(otherWithWarnings);
        var toAdd = otherWarnLib.getWarnings(otherWithWarnings, false);
        all = insertAllNode.executeInsertAll(frame, all, toAdd, max);
      }
      var res = equalsNode.execute(frame, self, other);
      if (res.getWarnings() != null) {
        all = insertAllNode.executeInsertAll(frame, all, res.getWarnings(), max);
      }
      return EqualsAndInfo.valueOf(res.isTrue(), all);
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  /** Interop libraries * */
  @Specialization(
      guards = {
        "selfInterop.isNull(selfNull) || otherInterop.isNull(otherNull)",
      },
      limit = "3")
  EqualsAndInfo equalsNull(
      Object selfNull,
      Object otherNull,
      @CachedLibrary("selfNull") InteropLibrary selfInterop,
      @CachedLibrary("otherNull") InteropLibrary otherInterop) {
    return EqualsAndInfo.valueOf(selfInterop.isNull(selfNull) && otherInterop.isNull(otherNull));
  }

  @Specialization(
      guards = {"selfInterop.isBoolean(selfBoolean)", "otherInterop.isBoolean(otherBoolean)"},
      limit = "3")
  EqualsAndInfo equalsBooleanInterop(
      Object selfBoolean,
      Object otherBoolean,
      @CachedLibrary("selfBoolean") InteropLibrary selfInterop,
      @CachedLibrary("otherBoolean") InteropLibrary otherInterop) {
    try {
      return EqualsAndInfo.valueOf(
          selfInterop.asBoolean(selfBoolean) == otherInterop.asBoolean(otherBoolean));
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "isTimeZone(selfTimeZone, selfInterop)",
        "isTimeZone(otherTimeZone, otherInterop)",
      },
      limit = "3")
  EqualsAndInfo equalsTimeZones(
      Object selfTimeZone,
      Object otherTimeZone,
      @CachedLibrary("selfTimeZone") InteropLibrary selfInterop,
      @CachedLibrary("otherTimeZone") InteropLibrary otherInterop) {
    try {
      return EqualsAndInfo.valueOf(
          selfInterop.asTimeZone(selfTimeZone).equals(otherInterop.asTimeZone(otherTimeZone)));
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @TruffleBoundary
  @Specialization(
      guards = {
        "isZonedDateTime(selfZonedDateTime, selfInterop)",
        "isZonedDateTime(otherZonedDateTime, otherInterop)",
      },
      limit = "3")
  EqualsAndInfo equalsZonedDateTimes(
      Object selfZonedDateTime,
      Object otherZonedDateTime,
      @CachedLibrary("selfZonedDateTime") InteropLibrary selfInterop,
      @CachedLibrary("otherZonedDateTime") InteropLibrary otherInterop) {
    try {
      var self =
          ZonedDateTime.of(
              selfInterop.asDate(selfZonedDateTime),
              selfInterop.asTime(selfZonedDateTime),
              selfInterop.asTimeZone(selfZonedDateTime));
      var other =
          ZonedDateTime.of(
              otherInterop.asDate(otherZonedDateTime),
              otherInterop.asTime(otherZonedDateTime),
              otherInterop.asTimeZone(otherZonedDateTime));
      // We cannot use self.isEqual(other), because that does not include timezone.
      return EqualsAndInfo.valueOf(self.compareTo(other) == 0);
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "isDateTime(selfDateTime, selfInterop)",
        "isDateTime(otherDateTime, otherInterop)",
      },
      limit = "3")
  EqualsAndInfo equalsDateTimes(
      Object selfDateTime,
      Object otherDateTime,
      @CachedLibrary("selfDateTime") InteropLibrary selfInterop,
      @CachedLibrary("otherDateTime") InteropLibrary otherInterop) {
    try {
      var self =
          LocalDateTime.of(selfInterop.asDate(selfDateTime), selfInterop.asTime(selfDateTime));
      var other =
          LocalDateTime.of(otherInterop.asDate(otherDateTime), otherInterop.asTime(otherDateTime));
      return EqualsAndInfo.valueOf(self.isEqual(other));
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "isDate(selfDate, selfInterop)",
        "isDate(otherDate, otherInterop)",
      },
      limit = "3")
  EqualsAndInfo equalsDates(
      Object selfDate,
      Object otherDate,
      @CachedLibrary("selfDate") InteropLibrary selfInterop,
      @CachedLibrary("otherDate") InteropLibrary otherInterop) {
    try {
      return EqualsAndInfo.valueOf(
          selfInterop.asDate(selfDate).isEqual(otherInterop.asDate(otherDate)));
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "isTime(selfTime, selfInterop)",
        "isTime(otherTime, otherInterop)",
      },
      limit = "3")
  EqualsAndInfo equalsTimes(
      Object selfTime,
      Object otherTime,
      @CachedLibrary("selfTime") InteropLibrary selfInterop,
      @CachedLibrary("otherTime") InteropLibrary otherInterop) {
    try {
      return EqualsAndInfo.valueOf(
          selfInterop.asTime(selfTime).equals(otherInterop.asTime(otherTime)));
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {"selfInterop.isDuration(selfDuration)", "otherInterop.isDuration(otherDuration)"},
      limit = "3")
  EqualsAndInfo equalsDuration(
      Object selfDuration,
      Object otherDuration,
      @CachedLibrary("selfDuration") InteropLibrary selfInterop,
      @CachedLibrary("otherDuration") InteropLibrary otherInterop) {
    try {
      return EqualsAndInfo.valueOf(
          selfInterop.asDuration(selfDuration).equals(otherInterop.asDuration(otherDuration)));
    } catch (UnsupportedMessageException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "selfInterop.hasArrayElements(selfArray)",
        "otherInterop.hasArrayElements(otherArray)",
        "!selfInterop.hasHashEntries(selfArray)",
        "!otherInterop.hasHashEntries(otherArray)",
      },
      limit = "3")
  EqualsAndInfo equalsArrays(
      VirtualFrame frame,
      Object selfArray,
      Object otherArray,
      @CachedLibrary("selfArray") InteropLibrary selfInterop,
      @CachedLibrary("otherArray") InteropLibrary otherInterop,
      @Shared("equalsNode") @Cached EqualsNode equalsNode,
      @Shared("hostValueToEnsoNode") @Cached HostValueToEnsoNode valueToEnsoNode) {
    try {
      long selfSize = selfInterop.getArraySize(selfArray);
      if (selfSize != otherInterop.getArraySize(otherArray)) {
        return EqualsAndInfo.FALSE;
      }
      for (long i = 0; i < selfSize; i++) {
        Object selfElem = valueToEnsoNode.execute(selfInterop.readArrayElement(selfArray, i));
        Object otherElem = valueToEnsoNode.execute(otherInterop.readArrayElement(otherArray, i));
        var elemsAreEqual = equalsNode.execute(frame, selfElem, otherElem);
        if (!elemsAreEqual.isTrue()) {
          return elemsAreEqual;
        }
      }
      return EqualsAndInfo.TRUE;
    } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "selfInterop.hasHashEntries(selfHashMap)",
        "otherInterop.hasHashEntries(otherHashMap)",
        "!selfInterop.hasArrayElements(selfHashMap)",
        "!otherInterop.hasArrayElements(otherHashMap)"
      },
      limit = "3")
  EqualsAndInfo equalsHashMaps(
      VirtualFrame frame,
      Object selfHashMap,
      Object otherHashMap,
      @CachedLibrary("selfHashMap") InteropLibrary selfInterop,
      @CachedLibrary("otherHashMap") InteropLibrary otherInterop,
      @Shared("interop") @CachedLibrary(limit = "10") InteropLibrary entriesInterop,
      @Shared("equalsNode") @Cached EqualsNode equalsNode,
      @Exclusive @Cached HostValueToEnsoNode keyToEnsoNode,
      @Exclusive @Cached HostValueToEnsoNode valueToEnsoNode) {
    try {
      int selfHashSize = (int) selfInterop.getHashSize(selfHashMap);
      int otherHashSize = (int) otherInterop.getHashSize(otherHashMap);
      if (selfHashSize != otherHashSize) {
        return EqualsAndInfo.FALSE;
      }
      Object selfEntriesIter = selfInterop.getHashEntriesIterator(selfHashMap);
      while (entriesInterop.hasIteratorNextElement(selfEntriesIter)) {
        Object selfKeyValue = entriesInterop.getIteratorNextElement(selfEntriesIter);
        Object key = keyToEnsoNode.execute(entriesInterop.readArrayElement(selfKeyValue, 0));
        Object selfValue =
            valueToEnsoNode.execute(entriesInterop.readArrayElement(selfKeyValue, 1));
        if (otherInterop.isHashEntryExisting(otherHashMap, key)
            && otherInterop.isHashEntryReadable(otherHashMap, key)) {
          var otherValue = valueToEnsoNode.execute(otherInterop.readHashValue(otherHashMap, key));
          var res = equalsNode.execute(frame, selfValue, otherValue);
          if (!res.isTrue()) {
            return res;
          }
        } else {
          return EqualsAndInfo.FALSE;
        }
      }
      return EqualsAndInfo.TRUE;
    } catch (UnsupportedMessageException
        | StopIterationException
        | UnknownKeyException
        | InvalidArrayIndexException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  @Specialization(
      guards = {
        "isObjectWithMembers(selfObject, interop)",
        "isObjectWithMembers(otherObject, interop)",
      })
  EqualsAndInfo equalsInteropObjectWithMembers(
      Object selfObject,
      Object otherObject,
      @Shared("interop") @CachedLibrary(limit = "10") InteropLibrary interop,
      @Shared("typesLib") @CachedLibrary(limit = "10") TypesLibrary typesLib,
      @Shared("equalsNode") @Cached EqualsNode equalsNode,
      @Shared("hostValueToEnsoNode") @Cached HostValueToEnsoNode valueToEnsoNode) {
    if (interop.isIdentical(selfObject, otherObject, interop)) {
      return EqualsAndInfo.TRUE;
    } else {
      return EqualsAndInfo.FALSE;
    }
  }

  @Specialization(guards = {"isJavaObject(selfHostObject)", "isJavaObject(otherHostObject)"})
  EqualsAndInfo equalsHostObjects(
      Object selfHostObject,
      Object otherHostObject,
      @Shared("interop") @CachedLibrary(limit = "10") InteropLibrary interop) {
    try {
      return EqualsAndInfo.valueOf(
          interop.asBoolean(interop.invokeMember(selfHostObject, "equals", otherHostObject)));
    } catch (UnsupportedMessageException
        | ArityException
        | UnknownIdentifierException
        | UnsupportedTypeException e) {
      throw EnsoContext.get(this).raiseAssertionPanic(this, null, e);
    }
  }

  // HostFunction is identified by a qualified name, it is not a lambda.
  // It has well-defined equality based on the qualified name.
  @Specialization(guards = {"isJavaFunction(selfHostFunc)", "isJavaFunction(otherHostFunc)"})
  EqualsAndInfo equalsHostFunctions(
      VirtualFrame frame,
      Object selfHostFunc,
      Object otherHostFunc,
      @Shared("interop") @CachedLibrary(limit = "10") InteropLibrary interop,
      @Shared("equalsNode") @Cached EqualsNode equalsNode) {
    Object selfFuncStrRepr = interop.toDisplayString(selfHostFunc);
    Object otherFuncStrRepr = interop.toDisplayString(otherHostFunc);
    return equalsNode.execute(frame, selfFuncStrRepr, otherFuncStrRepr);
  }

  @Specialization(guards = "fallbackGuard(left, right, interop, warningsLib)")
  @TruffleBoundary
  EqualsAndInfo equalsGeneric(
      Object left,
      Object right,
      @Shared("interop") @CachedLibrary(limit = "10") InteropLibrary interop,
      @Shared("typesLib") @CachedLibrary(limit = "10") TypesLibrary typesLib,
      @CachedLibrary(limit = "10") WarningsLibrary warningsLib) {
    var res =
        left == right
            || interop.isIdentical(left, right, interop)
            || left.equals(right)
            || (isNullOrNothing(left, typesLib, interop)
                && isNullOrNothing(right, typesLib, interop));
    return EqualsAndInfo.valueOf(res);
  }

  // We have to manually specify negation of guards of other specializations, because
  // we cannot use @Fallback here. Note that this guard is not precisely the negation of
  // all the other guards on purpose.
  boolean fallbackGuard(
      Object left, Object right, InteropLibrary interop, WarningsLibrary warnings) {
    if (EqualsSimpleNode.isPrimitive(left, interop)
        && EqualsSimpleNode.isPrimitive(right, interop)) {
      return false;
    }
    if (isJavaObject(left) && isJavaObject(right)) {
      return false;
    }
    if (isJavaFunction(left) && isJavaFunction(right)) {
      return false;
    }
    if (left instanceof Atom && right instanceof Atom) {
      return false;
    }
    if (interop.isNull(left) && interop.isNull(right)) {
      return false;
    }
    if (interop.isString(left) && interop.isString(right)) {
      return false;
    }
    if (interop.hasArrayElements(left) && interop.hasArrayElements(right)) {
      return false;
    }
    if (interop.hasHashEntries(left) && interop.hasHashEntries(right)) {
      return false;
    }
    if (isObjectWithMembers(left, interop) && isObjectWithMembers(right, interop)) {
      return false;
    }
    if (isTimeZone(left, interop) && isTimeZone(right, interop)) {
      return false;
    }
    if (isZonedDateTime(left, interop) && isZonedDateTime(right, interop)) {
      return false;
    }
    if (isDateTime(left, interop) && isDateTime(right, interop)) {
      return false;
    }
    if (isDate(left, interop) && isDate(right, interop)) {
      return false;
    }
    if (isTime(left, interop) && isTime(right, interop)) {
      return false;
    }
    if (interop.isDuration(left) && interop.isDuration(right)) {
      return false;
    }
    if (warnings.hasWarnings(left) || warnings.hasWarnings(right)) {
      return false;
    }
    // For all other cases, fall through to the generic specialization
    return true;
  }

  boolean isTimeZone(Object object, InteropLibrary interop) {
    return !interop.isTime(object) && !interop.isDate(object) && interop.isTimeZone(object);
  }

  boolean isZonedDateTime(Object object, InteropLibrary interop) {
    return interop.isTime(object) && interop.isDate(object) && interop.isTimeZone(object);
  }

  boolean isDateTime(Object object, InteropLibrary interop) {
    return interop.isTime(object) && interop.isDate(object) && !interop.isTimeZone(object);
  }

  boolean isDate(Object object, InteropLibrary interop) {
    return !interop.isTime(object) && interop.isDate(object) && !interop.isTimeZone(object);
  }

  boolean isTime(Object object, InteropLibrary interop) {
    return interop.isTime(object) && !interop.isDate(object) && !interop.isTimeZone(object);
  }

  boolean isObjectWithMembers(Object object, InteropLibrary interop) {
    if (object instanceof Atom) {
      return false;
    }
    if (isJavaObject(object)) {
      return false;
    }
    if (interop.isDate(object)) {
      return false;
    }
    if (interop.isTime(object)) {
      return false;
    }
    if (interop.hasHashEntries(object)) {
      return false;
    }
    return interop.hasMembers(object);
  }

  private boolean isNullOrNothing(Object object, TypesLibrary typesLib, InteropLibrary interop) {
    if (typesLib.hasType(object)) {
      return typesLib.getType(object) == EnsoContext.get(this).getNothing();
    } else if (interop.isNull(object)) {
      return true;
    } else {
      return object == null;
    }
  }

  boolean isJavaObject(Object object) {
    return EnsoContext.get(this).isJavaPolyglotObject(object);
  }

  boolean isJavaFunction(Object object) {
    return EnsoContext.get(this).isJavaPolyglotFunction(object);
  }
}
