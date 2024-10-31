package org.enso.runtime.parser.processor.test;

import org.enso.runtime.parser.processor.test.gen.ir.NameTestIR;
import org.enso.runtime.parser.processor.test.gen.ir.NameTestIRGen;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;


public class TestGeneratedIR {
  @Test
  public void generatedCodeHasBuilder() {
    NameTestIR myIr = NameTestIRGen.builder().name("name").build();
    assertThat(myIr.name(), is("name"));
  }

  @Test
  public void myIRHasNoChildren() {
    NameTestIR myIr = NameTestIRGen.builder().name("name").build();
    assertThat(myIr.children().isEmpty(), is(true));
  }

  @Test
  public void nonChildFieldIsNotNullable() {
    var bldr = NameTestIRGen.builder();
    try {
      bldr.build();
      fail("Expected exception - name field must be specified in the builder");
    } catch (Exception e) {
      assertThat(e, is(notNullValue()));
    }
  }

  @Test
  public void canDuplicate() {
    NameTestIR myIr = NameTestIRGen.builder().name("name").build();
    var duplicated = myIr.duplicate(true, true, true, true);
    assertThat("duplicate returns same type",
        duplicated, instanceOf(NameTestIR.class));
    assertThat("name was correctly duplicated",
        ((NameTestIR) duplicated).name(), is("name"));
  }

}
