package com.marketdesk.ui;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;

public class PresentationContractTest {
  @Test
  public void fundAndEtfTextMatchesCommittedBehavior() throws Exception {
    JSONArray cases;
    try (InputStream input = getClass().getResourceAsStream("/presentation-contract.json")) {
      assertNotNull("committed display fixture", input);
      cases = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals(4, cases.length());
    for (int i = 0; i < cases.length(); i++) {
      JSONObject test = cases.getJSONObject(i), quote = test.getJSONObject("quote");
      assertEquals(
          test.getString("name"), test.getString("details"), FundPresentation.details(quote));
      JSONObject reference = quote.getJSONObject("fundMetrics").optJSONObject("holdingsReference");
      assertEquals(
          test.getString("name"), test.getString("summary"), FundPresentation.summary(reference));
      if (test.has("stocks"))
        assertEquals(
            test.getString("name"),
            test.getString("stocks"),
            FundPresentation.stockDetails(reference));
    }
  }
}
