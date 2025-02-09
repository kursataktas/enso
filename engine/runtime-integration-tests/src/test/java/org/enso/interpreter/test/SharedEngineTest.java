package org.enso.interpreter.test;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;
import java.util.logging.Level;
import org.enso.common.RuntimeOptions;
import org.enso.test.utils.ContextUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SharedEngineTest {
  private static Engine sharedEngine;
  private Context ctx;

  @BeforeClass
  public static void initializeSharedEngine() {
    sharedEngine =
        Engine.newBuilder()
            .allowExperimentalOptions(true)
            .option(RuntimeOptions.LOG_LEVEL, Level.WARNING.getName())
            .logHandler(System.err)
            .option(RuntimeOptions.STRICT_ERRORS, "true")
            .option(
                RuntimeOptions.LANGUAGE_HOME_OVERRIDE,
                Paths.get("../../test/micro-distribution/component").toFile().getAbsolutePath())
            .build();
  }

  @Before
  public void initializeContext() {
    this.ctx = ContextUtils.defaultContextBuilder().engine(sharedEngine).build();
  }

  @AfterClass
  public static void disposeEngine() {
    sharedEngine.close();
  }

  @After
  public void disposeCtx() {
    this.ctx.close();
  }

  private final Source typeCase =
      Source.newBuilder(
              "enso",
              """
    from Standard.Base import Vector, Text, Number

    check x = case x of
        _ : Vector -> 1
        _ : Text -> 2
        _ : Number -> 3
        _ -> 4
    """,
              "type_case.enso")
          .buildLiteral();

  @Test
  public void typeCaseFirstRun() {
    var fn = this.ctx.eval(typeCase).invokeMember("eval_expression", "check");
    var r = fn.execute("Hi");
    assertEquals(2, r.asInt());
  }

  @Test
  public void typeCaseSecondRun() {
    typeCaseFirstRun();
  }
}
