package org.enso.interpreter.test;

import static org.enso.test.utils.ContextUtils.createDefaultContext;
import static org.enso.test.utils.ContextUtils.createValue;
import static org.enso.test.utils.ContextUtils.executeInContext;
import static org.enso.test.utils.ContextUtils.unwrapValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.enso.common.MethodNames;
import org.enso.interpreter.node.expression.builtin.interop.syntax.HostValueToEnsoNode;
import org.enso.interpreter.node.expression.builtin.meta.EqualsNode;
import org.enso.interpreter.runtime.callable.UnresolvedConversion;
import org.enso.interpreter.runtime.number.EnsoBigInteger;
import org.enso.test.utils.TestRootNode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class EqualsTest {
  private static Context context;
  private static EqualsNode equalsNode;
  private static TestRootNode testRootNode;
  private static HostValueToEnsoNode hostValueToEnsoNode;

  @BeforeClass
  public static void initContextAndData() {
    context = createDefaultContext();
    executeInContext(
        context,
        () -> {
          testRootNode = new TestRootNode(EqualsTest::equalityCheck);
          equalsNode = EqualsNode.create();
          hostValueToEnsoNode = HostValueToEnsoNode.build();
          testRootNode.insertChildren(equalsNode, hostValueToEnsoNode);
          return null;
        });
    unwrappedValues = fetchAllUnwrappedValues();
  }

  @AfterClass
  public static void disposeContext() {
    context.close();
    context = null;
    unwrappedValues = null;
  }

  @DataPoints public static Object[] unwrappedValues;

  private static Object[] fetchAllUnwrappedValues() {
    var valGenerator =
        ValuesGenerator.create(
            context, ValuesGenerator.Language.ENSO, ValuesGenerator.Language.JAVA);
    List<Value> values = new ArrayList<>();
    values.addAll(valGenerator.numbers());
    values.addAll(valGenerator.booleans());
    values.addAll(valGenerator.textual());
    values.addAll(valGenerator.arrayLike());
    values.addAll(valGenerator.vectors());
    values.addAll(valGenerator.maps());
    values.addAll(valGenerator.multiLevelAtoms());
    values.addAll(valGenerator.timesAndDates());
    values.addAll(valGenerator.timeZones());
    values.addAll(valGenerator.durations());
    values.addAll(valGenerator.periods());
    values.addAll(valGenerator.warnings());
    try {
      return values.stream()
          .map(value -> unwrapValue(context, value))
          .map(unwrappedValue -> hostValueToEnsoNode.execute(unwrappedValue))
          .collect(Collectors.toList())
          .toArray(new Object[] {});
    } catch (Exception e) {
      throw new AssertionError(e);
    } finally {
      valGenerator.dispose();
    }
  }

  private static boolean equalityCheck(VirtualFrame frame) {
    var args = frame.getArguments();
    return equalsNode.execute(frame, args[0], args[1]).isTrue();
  }

  private boolean equalityCheck(Object first, Object second) {
    return (Boolean) testRootNode.getCallTarget().call(first, second);
  }

  @Theory
  public void equalsOperatorShouldBeSymmetric(Object firstValue, Object secondValue) {
    executeInContext(
        context,
        () -> {
          boolean firstResult = equalityCheck(firstValue, secondValue);
          boolean secondResult = equalityCheck(secondValue, firstValue);
          assertEquals("equals should be symmetric", firstResult, secondResult);
          return null;
        });
  }

  @Theory
  public void equalsOperatorShouldBeConsistent(Object value) {
    executeInContext(
        context,
        () -> {
          Object firstResult = equalityCheck(value, value);
          Object secondResult = equalityCheck(value, value);
          assertEquals("equals should be consistent", firstResult, secondResult);
          return null;
        });
  }

  @Theory
  public void equalsNodeCachedIsConsistentWithUncached(Object firstVal, Object secondVal) {
    executeInContext(
        context,
        () -> {
          Object uncachedRes = EqualsNode.getUncached().execute(null, firstVal, secondVal).isTrue();
          Object cachedRes = equalityCheck(firstVal, secondVal);
          assertEquals(
              "Result from uncached EqualsNode should be the same as result from its cached"
                  + " variant",
              uncachedRes,
              cachedRes);
          return null;
        });
  }

  /** Test for some specific values, for which we know that they are equal. */
  @Test
  public void testDateEquality() {
    Object ensoDate =
        unwrapValue(
            context,
            createValue(
                context, "(Date.new 1999 3 23)", "import Standard.Base.Data.Time.Date.Date"));
    Object javaDate = unwrapValue(context, context.asValue(LocalDate.of(1999, 3, 23)));
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoDate, javaDate));
          return null;
        });
  }

  @Test
  public void testTimeEquality() {
    Object ensoTime =
        unwrapValue(
            context,
            createValue(
                context,
                "Time_Of_Day.new 23 59",
                "import Standard.Base.Data.Time.Time_Of_Day.Time_Of_Day"));
    Object javaDate = unwrapValue(context, context.asValue(LocalTime.of(23, 59)));
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoTime, javaDate));
          return null;
        });
  }

  @Test
  public void testDateTimeEquality() {
    Object ensoDateTime =
        unwrapValue(
            context,
            createValue(
                context,
                "(Date_Time.new 1999 3 1 23 59)",
                "import Standard.Base.Data.Time.Date_Time.Date_Time"));
    Object javaDateTime =
        unwrapValue(
            context,
            context.asValue(
                ZonedDateTime.of(
                    LocalDate.of(1999, 3, 1), LocalTime.of(23, 59), ZoneId.systemDefault())));
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoDateTime, javaDateTime));
          return null;
        });
  }

  @Test
  public void testDoubleEqualsEnsoBigInteger() {
    long value = Long.MIN_VALUE;
    double javaNumber = Math.pow(value, 10);
    var ensoNumber = new EnsoBigInteger(BigInteger.valueOf(value).pow(10));
    executeInContext(
        context,
        () -> {
          assertTrue(javaNumber + " == " + ensoNumber, equalityCheck(javaNumber, ensoNumber));
          return null;
        });
  }

  @Test
  public void testEnsoBigIntegerEqualsDoubleEquals() {
    long value = Long.MIN_VALUE;
    double javaNumber = Math.pow(value, 10);
    var ensoNumber = new EnsoBigInteger(BigInteger.valueOf(value).pow(10));
    executeInContext(
        context,
        () -> {
          assertTrue(ensoNumber + " == " + javaNumber, equalityCheck(ensoNumber, javaNumber));
          return null;
        });
  }

  @Test
  public void testDoubleEqualsJavaBigInteger() {
    long value = Long.MIN_VALUE;
    double javaNumber = Math.pow(value, 10);
    var hostNumber = unwrapValue(context, context.asValue(BigInteger.valueOf(value).pow(10)));
    executeInContext(
        context,
        () -> {
          assertTrue(javaNumber + " == " + hostNumber, equalityCheck(javaNumber, hostNumber));
          return null;
        });
  }

  @Test
  public void testJavaBigIntegerEqualsDoubleEquals() {
    long value = Long.MIN_VALUE;
    double javaNumber = Math.pow(value, 10);
    var hostNumber = unwrapValue(context, context.asValue(BigInteger.valueOf(value).pow(10)));
    executeInContext(
        context,
        () -> {
          assertTrue(hostNumber + " == " + javaNumber, equalityCheck(hostNumber, javaNumber));
          return null;
        });
  }

  @Test
  public void testVectorsEquality() {
    Object ensoVector =
        unwrapValue(context, createValue(context, "[1,2,3]", "from Standard.Base import all"));
    Object javaVector = unwrapValue(context, context.asValue(List.of(1L, 2L, 3L)));
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoVector, javaVector));
          return null;
        });
  }

  @Test
  public void testTruffleNumberLong() {
    var ensoNumber = unwrapValue(context, createValue(context, "1", ""));
    var foreignNumber = new WrappedPrimitive(1);
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoNumber, foreignNumber.asDirect()));
          assertTrue(equalityCheck(ensoNumber, foreignNumber));
          assertTrue(equalityCheck(foreignNumber, ensoNumber));
          return null;
        });
  }

  @Test
  public void testTruffleNumberDouble() {
    var ensoNumber = unwrapValue(context, createValue(context, "1.0", ""));
    var foreignNumber = new WrappedPrimitive(1.0);
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoNumber, foreignNumber.asDirect()));
          assertTrue(equalityCheck(ensoNumber, foreignNumber));
          assertTrue(equalityCheck(foreignNumber, ensoNumber));
          return null;
        });
  }

  @Test
  public void testTruffleNumberBigInt() {
    var value = new BigInteger("43207431473298432194374819743291479009431478329");
    var ensoNumber = unwrapValue(context, createValue(context, value.toString(), ""));
    var foreignNumber = new WrappedPrimitive(value);
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoNumber, foreignNumber));
          assertTrue(equalityCheck(foreignNumber, ensoNumber));
          return null;
        });
  }

  @Test
  public void testTruffleBoolean() {
    var ensoBoolean =
        unwrapValue(context, createValue(context, "True", "from Standard.Base import True"));
    var foreignBoolean = new WrappedPrimitive(true);
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoBoolean, foreignBoolean.asDirect()));
          assertTrue(equalityCheck(ensoBoolean, foreignBoolean));
          assertTrue(equalityCheck(foreignBoolean, ensoBoolean));
          return null;
        });
  }

  @Test
  public void testTruffleString() {
    var ensoText = unwrapValue(context, createValue(context, "'Hello'", ""));
    var foreignString = new WrappedPrimitive("Hello");
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(ensoText, foreignString.asDirect()));
          assertTrue(equalityCheck(ensoText, foreignString));
          assertTrue(equalityCheck(foreignString, ensoText));
          return null;
        });
  }

  @Test
  public void testTruffleNumberPlus() {
    var plus100 =
        context
            .eval("enso", """
    plus100 x = 100+x
    """)
            .invokeMember(MethodNames.Module.EVAL_EXPRESSION, "plus100");
    assertTrue("plus100 can be executed", plus100.canExecute());
    var foreignNumber = context.asValue(new WrappedPrimitive(42));
    var hundred42 = unwrapValue(context, plus100.execute(foreignNumber));
    executeInContext(
        context,
        () -> {
          assertTrue(equalityCheck(142L, hundred42));
          assertTrue(equalityCheck(hundred42, 142L));
          return null;
        });
  }

  @Test
  public void testUnresolvedConversionInNamedModules() throws Exception {
    var mod1 = context.eval(Source.newBuilder("enso", "one = 'one'", "one.enso").build());
    var mod2 = context.eval(Source.newBuilder("enso", "two = 'two'", "two.enso").build());
    assertUnresolvedConversions(mod1, mod2);
  }

  @Test
  public void testUnresolvedConversionInUnamedModules() throws Exception {
    var mod1 = context.eval("enso", "one = 'one'");
    var mod2 = context.eval("enso", "two = 'two'");
    assertUnresolvedConversions(mod1, mod2);
  }

  private void assertUnresolvedConversions(Value mod1, Value mod2) {

    assertEquals("one", mod1.invokeMember(MethodNames.Module.EVAL_EXPRESSION, "one").asString());
    assertEquals("two", mod2.invokeMember(MethodNames.Module.EVAL_EXPRESSION, "two").asString());

    if (unwrapValue(context, mod1) instanceof org.enso.interpreter.runtime.Module m1
        && unwrapValue(context, mod2) instanceof org.enso.interpreter.runtime.Module m2) {
      var scope1 = m1.getScope();
      var scope2 = m2.getScope();
      assertNotEquals("Different modules have different scopes", scope1, scope2);

      var conv1 = UnresolvedConversion.build(scope1);
      var conv1_2 = UnresolvedConversion.build(scope1);
      var conv2 = UnresolvedConversion.build(scope2);
      var conv2_2 = UnresolvedConversion.build(scope2);

      executeInContext(
          context,
          () -> {
            assertTrue("Conversions from same module are the same", equalityCheck(conv1, conv1_2));
            assertTrue("Conversions from same module are the same", equalityCheck(conv2, conv2_2));
            assertFalse(
                "Conversions from other modules aren't the same", equalityCheck(conv1, conv2));
            assertFalse(
                "Conversions from other modueles aren't the same", equalityCheck(conv2_2, conv1_2));
            return null;
          });

    } else {
      fail("Expecting module: " + mod1);
    }
  }
}
