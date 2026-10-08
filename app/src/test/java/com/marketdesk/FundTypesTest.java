package com.marketdesk;

import static org.junit.Assert.*;

import org.junit.Test;

public class FundTypesTest {
  @Test
  public void existingFundTypeLabelsRemainAvailableWithoutImport() {
    assertTrue(FundTypes.type("F:023638").contains("指数型"));
    assertTrue(FundTypes.type("F:017436").contains("主动"));
    assertTrue(FundTypes.type("F:100055").contains("多市场"));
  }

  @Test
  public void unknownFundDoesNotReceiveAnInventedType() {
    assertEquals("", FundTypes.type("F:000001"));
  }
}
