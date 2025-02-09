package org.enso.interpreter.test.instrument

import org.enso.interpreter.runtime.`type`.{Constants, ConstantsGen}
import org.enso.interpreter.test.Metadata
import org.enso.common.LanguageInfo
import org.enso.common.RuntimeOptions
import org.enso.polyglot.RuntimeServerInfo
import org.enso.polyglot.runtime.Runtime.Api
import org.enso.text.{ContentVersion, Sha3_224VersionCalculator}
import org.graalvm.polyglot.Context
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Paths}
import java.util.UUID

@scala.annotation.nowarn("msg=multiarg infix syntax")
class RuntimeInstrumentTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach {

  // === Test Utilities =======================================================

  var context: TestContext = _

  class TestContext(packageName: String)
      extends InstrumentTestContext(packageName) {
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    val context =
      Context
        .newBuilder(LanguageInfo.ID)
        .allowExperimentalOptions(true)
        .allowAllAccess(true)
        .option(RuntimeOptions.PROJECT_ROOT, pkg.root.getAbsolutePath)
        .option(RuntimeOptions.LOG_LEVEL, "WARNING")
        .option(RuntimeOptions.INTERPRETER_SEQUENTIAL_COMMAND_EXECUTION, "true")
        .option(RuntimeOptions.ENABLE_PROJECT_SUGGESTIONS, "false")
        .option(RuntimeOptions.ENABLE_GLOBAL_SUGGESTIONS, "false")
        .option(RuntimeOptions.ENABLE_EXECUTION_TIMER, "false")
        .option(
          RuntimeOptions.DISABLE_IR_CACHES,
          InstrumentTestContext.DISABLE_IR_CACHE
        )
        .option(RuntimeServerInfo.ENABLE_OPTION, "true")
        .option(RuntimeOptions.INTERACTIVE_MODE, "true")
        .option(
          RuntimeOptions.LANGUAGE_HOME_OVERRIDE,
          Paths
            .get("../../test/micro-distribution/component")
            .toFile
            .getAbsolutePath
        )
        .option(RuntimeOptions.EDITION_OVERRIDE, "0.0.0-dev")
        .out(out)
        .logHandler(System.err)
        .serverTransport(runtimeServerEmulator.makeServerTransport)
        .build()

    def writeMain(contents: String): File =
      Files.write(pkg.mainFile.toPath, contents.getBytes).toFile

    def writeFile(file: File, contents: String): File =
      Files.write(file.toPath, contents.getBytes).toFile

    def writeInSrcDir(moduleName: String, contents: String): File = {
      val file = new File(pkg.sourceDir, s"$moduleName.enso")
      Files.write(file.toPath, contents.getBytes).toFile
    }

    def send(msg: Api.Request): Unit = runtimeServerEmulator.sendToRuntime(msg)

    def consumeOut: List[String] = {
      val result = out.toString
      out.reset()
      result.linesIterator.toList
    }

    def executionComplete(contextId: UUID): Api.Response =
      Api.Response(Api.ExecutionComplete(contextId))
  }

  def contentsVersion(content: String): ContentVersion =
    Sha3_224VersionCalculator.evalVersion(content)

  override protected def beforeEach(): Unit = {
    context = new TestContext("Test")
    context.init()
    val Some(Api.Response(_, Api.InitializedNotification())) = context.receive
  }

  override protected def afterEach(): Unit = {
    if (context != null) {
      context.close()
      context.out.reset()
      context = null
    }
  }

  it should "instrument simple expression" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata = new Metadata
    val mainBody = metadata.addItem(7, 2)

    val code     = "main = 42"
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, mainBody, ConstantsGen.INTEGER_BUILTIN),
      context.executionComplete(contextId)
    )
  }

  it should "instrument default hello world example" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata = new Metadata
    val mainBody = metadata.addItem(7, 14)

    val code =
      """|main = "Hello World!"
         |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, mainBody, ConstantsGen.TEXT_BUILTIN),
      context.executionComplete(contextId)
    )
  }

  it should "instrument conversion methods" in {
    val contextId   = UUID.randomUUID()
    val requestId   = UUID.randomUUID()
    val moduleName  = "Enso_Test.Test.Main"
    val fooTypeName = s"$moduleName.Foo"

    val metadata = new Metadata
    val mainBody = metadata.addItem(52, 12)

    val code =
      """type Foo
        |type Bar
        |
        |Foo.from (_ : Bar) = Foo
        |
        |main = Foo.from Bar
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(3) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(
        contextId,
        mainBody,
        fooTypeName,
        Api.MethodCall(Api.MethodPointer(moduleName, fooTypeName, "from"))
      ),
      context.executionComplete(contextId)
    )
  }

  it should "instrument expressions" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata    = new Metadata
    val mainBody    = metadata.addItem(37, 52, "aaaa")
    val xExpr       = metadata.addItem(46, 2, "bbbb")
    val yExpr       = metadata.addItem(57, 5, "cccc")
    val zExpr       = metadata.addItem(71, 1, "dddd")
    val mainResExpr = metadata.addItem(77, 12, "eeee")

    val code =
      """from Standard.Base import all
        |
        |main =
        |    x = 42
        |    y = x + 1
        |    z = y
        |    IO.println z
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    metadata.assertInCode(xExpr, code, "42")
    metadata.assertInCode(mainResExpr, code, "IO.println z")

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      7
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, xExpr, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        yExpr,
        ConstantsGen.INTEGER,
        Api.MethodCall(
          Api.MethodPointer(
            "Standard.Base.Data.Numbers",
            ConstantsGen.INTEGER,
            "+"
          )
        )
      ),
      TestMessages.update(contextId, zExpr, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        mainResExpr,
        ConstantsGen.NOTHING,
        methodCall = Some(
          Api.MethodCall(
            Api.MethodPointer(
              "Standard.Base.IO",
              "Standard.Base.IO",
              "println"
            )
          )
        )
      ),
      TestMessages.update(contextId, mainBody, ConstantsGen.NOTHING),
      context.executionComplete(contextId)
    )
  }

  it should "instrument expressions returning polyglot values" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata    = new Metadata
    val mainBody    = metadata.addItem(78, 39)
    val xExpr       = metadata.addItem(87, 13)
    val mainResExpr = metadata.addItem(105, 12)

    val code =
      """from Standard.Base import all
        |polyglot java import java.time.LocalDate
        |
        |main =
        |    x = LocalDate.now
        |    IO.println x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      5
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, xExpr, ConstantsGen.DATE),
      TestMessages.update(
        contextId,
        mainResExpr,
        ConstantsGen.NOTHING,
        methodCall = Some(
          Api.MethodCall(
            Api.MethodPointer(
              "Standard.Base.IO",
              "Standard.Base.IO",
              "println"
            )
          )
        )
      ),
      TestMessages.update(contextId, mainBody, ConstantsGen.NOTHING),
      context.executionComplete(contextId)
    )
  }

  it should "instrument sub-expressions" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata     = new Metadata
    val mainBody     = metadata.addItem(37, 42, "aaaa")
    val xExpr        = metadata.addItem(46, 2, "bbbb")
    val yExpr        = metadata.addItem(57, 5, "cccc")
    val mainResExpr  = metadata.addItem(67, 12, "dddd")
    val mainRes1Expr = metadata.addItem(78, 1, "eeee")

    val code =
      """from Standard.Base import all
        |
        |main =
        |    x = 42
        |    y = x + 1
        |    IO.println y
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    metadata.assertInCode(xExpr, code, "42")
    metadata.assertInCode(mainResExpr, code, "IO.println y")

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      7
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, xExpr, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        yExpr,
        ConstantsGen.INTEGER,
        Api.MethodCall(
          Api.MethodPointer(
            "Standard.Base.Data.Numbers",
            ConstantsGen.INTEGER,
            "+"
          )
        )
      ),
      TestMessages.update(contextId, mainRes1Expr, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        mainResExpr,
        ConstantsGen.NOTHING,
        methodCall = Some(
          Api.MethodCall(
            Api.MethodPointer(
              "Standard.Base.IO",
              "Standard.Base.IO",
              "println"
            )
          )
        )
      ),
      TestMessages.update(contextId, mainBody, ConstantsGen.NOTHING),
      context.executionComplete(contextId)
    )
  }

  it should "instrument binding of a lambda" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata = new Metadata
    val mainBody = metadata.addItem(40, 28, "aaaa")
    val fExpr    = metadata.addItem(49, 10, "bbbb")
    // f body
    metadata.addItem(54, 5, "cccc")
    val mainResExpr = metadata.addItem(64, 4, "dddd")

    val code =
      """import Standard.Base.Data.Numbers
        |main =
        |    f = x -> x + 1
        |    f 42
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    metadata.assertInCode(mainResExpr, code, "f 42")

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      5
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, fExpr, ConstantsGen.FUNCTION_BUILTIN),
      TestMessages.update(contextId, mainResExpr, ConstantsGen.INTEGER),
      TestMessages.update(contextId, mainBody, ConstantsGen.INTEGER),
      context.executionComplete(contextId)
    )
  }

  it should "instrument binding of sugared lambda" in {
    pending
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"

    val metadata    = new Metadata
    val fExpr       = metadata.addItem(15, 5)
    val xExpr       = metadata.addItem(29, 4)
    val mainResExpr = metadata.addItem(38, 1)

    val code =
      """main =
        |    f = _ + 1
        |    x = f 42
        |    x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(6) should contain allOf (
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, fExpr, ConstantsGen.FUNCTION_BUILTIN),
      TestMessages.update(contextId, xExpr, ConstantsGen.INTEGER_BUILTIN),
      TestMessages.update(contextId, mainResExpr, ConstantsGen.INTEGER_BUILTIN),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument the body of a method" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata("import Standard.Base.Data.Numbers\n\n")

    // f expression
    metadata.addItem(7, 5, "ffff")
    val xExpr    = metadata.addItem(29, 3, "aaaa")
    val mainRes  = metadata.addItem(37, 1, "cccc")
    val mainExpr = metadata.addItem(20, 18, "dddd")

    val code =
      """
        |f x = x + 1
        |
        |main =
        |    x = f 1
        |    x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    metadata.assertInCode(xExpr, code, "f 1")
    metadata.assertInCode(mainRes, code, "x")

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      5
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages
        .update(
          contextId,
          xExpr,
          ConstantsGen.INTEGER,
          Api.MethodCall(
            Api.MethodPointer("Enso_Test.Test.Main", "Enso_Test.Test.Main", "f")
          )
        ),
      TestMessages.update(contextId, mainRes, ConstantsGen.INTEGER),
      TestMessages.update(contextId, mainExpr, ConstantsGen.INTEGER),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument the body of a function" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    // f expression
    metadata.addItem(42, 5)
    val aExpr    = metadata.addItem(62, 1)
    val fApp     = metadata.addItem(80, 3)
    val mainRes  = metadata.addItem(68, 16)
    val mainExpr = metadata.addItem(37, 47)

    val code =
      """from Standard.Base import all
        |
        |main =
        |    f x = x + 1
        |    a = 1
        |    IO.println (f a)
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      6
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, aExpr, ConstantsGen.INTEGER),
      TestMessages.update(contextId, fApp, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        mainRes,
        ConstantsGen.NOTHING,
        methodCall = Some(
          Api.MethodCall(
            Api.MethodPointer(
              "Standard.Base.IO",
              "Standard.Base.IO",
              "println"
            )
          )
        )
      ),
      TestMessages.update(contextId, mainExpr, ConstantsGen.NOTHING),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument the body of a lambda" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    val aExpr = metadata.addItem(46, 14)
    // lambda
    metadata.addItem(47, 10)
    // lambda expression
    metadata.addItem(52, 5)
    val lamArg  = metadata.addItem(59, 1)
    val mainRes = metadata.addItem(65, 12)

    val code =
      """from Standard.Base import all
        |
        |main =
        |    a = (x -> x + 1) 1
        |    IO.println a
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      5
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, aExpr, ConstantsGen.INTEGER),
      TestMessages.update(contextId, lamArg, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        mainRes,
        ConstantsGen.NOTHING,
        methodCall = Some(
          Api.MethodCall(
            Api.MethodPointer(
              "Standard.Base.IO",
              "Standard.Base.IO",
              "println"
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument the body of a sugared lambda" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    val aExpr = metadata.addItem(46, 9)
    // lambda
    metadata.addItem(47, 5)
    val lamArg  = metadata.addItem(54, 1)
    val mainRes = metadata.addItem(60, 12)

    val code =
      """from Standard.Base import all
        |
        |main =
        |    a = (_ + 1) 1
        |    IO.println a
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      5
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, aExpr, ConstantsGen.INTEGER),
      TestMessages.update(contextId, lamArg, ConstantsGen.INTEGER),
      TestMessages.update(
        contextId,
        mainRes,
        ConstantsGen.NOTHING,
        methodCall = Some(
          Api.MethodCall(
            Api.MethodPointer(
              "Standard.Base.IO",
              "Standard.Base.IO",
              "println"
            )
          )
        )
      ),
      context.executionComplete(contextId)
    )
    context.consumeOut shouldEqual List("2")
  }

  it should "not instrument functions in block expressions" in {
    pending
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    val xExpr = metadata.addItem(14, 33)
    // function body
    metadata.addItem(29, 5)
    // x result
    metadata.addItem(43, 4)
    val mainRes = metadata.addItem(52, 1)

    val code =
      """main =
        |    x =
        |        f x = x + 1
        |        f 42
        |    x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(5) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, xExpr, Constants.THUNK),
      TestMessages.update(contextId, mainRes, Constants.THUNK),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument lambdas in block expressions" in {
    pending
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    val xExpr = metadata.addItem(14, 36)
    // lambda
    metadata.addItem(27, 10)
    // lambda body
    metadata.addItem(32, 5)
    // x result
    metadata.addItem(46, 4)
    val mainRes = metadata.addItem(55, 1)

    val code =
      """main =
        |    x =
        |        f = x -> x + 1
        |        f 42
        |    x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(4) should contain allOf (
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, xExpr, Constants.THUNK),
      TestMessages.update(contextId, mainRes, Constants.THUNK),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument sugared lambdas in block expressions" in {
    pending
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    val xExpr = metadata.addItem(14, 31)
    // lambda
    metadata.addItem(27, 5)
    // x result
    metadata.addItem(41, 4)
    val mainRes = metadata.addItem(50, 1)

    val code =
      """main =
        |    x =
        |        f = _ + 1
        |        f 42
        |    x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveN(5) should contain allOf (
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, xExpr, Constants.THUNK),
      TestMessages.update(contextId, mainRes, Constants.THUNK),
      context.executionComplete(contextId)
    )
  }

  it should "not instrument a lambda in argument" in {
    val contextId  = UUID.randomUUID()
    val requestId  = UUID.randomUUID()
    val moduleName = "Enso_Test.Test.Main"
    val metadata   = new Metadata

    // body of id method
    metadata.addItem(17, 1)
    // body of id1 function
    metadata.addItem(53, 3)
    // default lambda argument a->a in id method
    metadata.addItem(9, 4)
    // default lambda argument a->a in id1 function
    metadata.addItem(45, 4)
    // first x->x argument
    metadata.addItem(79, 4)
    // second x->x argument
    metadata.addItem(108, 4)
    val arg1 = metadata.addItem(65, 2)
    val arg2 = metadata.addItem(76, 2)
    val arg3 = metadata.addItem(98, 2)

    val code =
      """
        |id (x = a->a) = x
        |
        |main =
        |    id1 x=42 (y = a->a) = y x
        |    id1 42
        |    id1 42 x->x
        |    id
        |    id 42
        |    id x->x
        |""".stripMargin.linesIterator.mkString("\n")
    val contents = metadata.appendToCode(code)
    val mainFile = context.writeMain(contents)

    // create context
    context.send(Api.Request(requestId, Api.CreateContextRequest(contextId)))
    context.receive shouldEqual Some(
      Api.Response(requestId, Api.CreateContextResponse(contextId))
    )

    // Open the new file
    context.send(
      Api.Request(requestId, Api.OpenFileRequest(mainFile, contents))
    )
    context.receive shouldEqual Some(
      Api.Response(Some(requestId), Api.OpenFileResponse)
    )

    // push main
    context.send(
      Api.Request(
        requestId,
        Api.PushContextRequest(
          contextId,
          Api.StackItem.ExplicitCall(
            Api.MethodPointer(moduleName, "Enso_Test.Test.Main", "main"),
            None,
            Vector()
          )
        )
      )
    )
    context.receiveNIgnorePendingExpressionUpdates(
      5
    ) should contain theSameElementsAs Seq(
      Api.Response(requestId, Api.PushContextResponse(contextId)),
      TestMessages.update(contextId, arg1, ConstantsGen.INTEGER_BUILTIN),
      TestMessages.update(contextId, arg2, ConstantsGen.INTEGER_BUILTIN),
      TestMessages.update(contextId, arg3, ConstantsGen.INTEGER_BUILTIN),
      context.executionComplete(contextId)
    )
  }

}
