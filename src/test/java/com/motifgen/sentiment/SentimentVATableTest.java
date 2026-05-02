package com.motifgen.sentiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link SentimentVATable}. Covers Scenario 1 label-lookup. */
class SentimentVATableTest {

  @Test
  void closestLabelForHappyCoordinatesReturnsHappy() {
    // HAPPY is at V=0.75, A=0.70 — exact match should return HAPPY
    assertEquals("HAPPY", SentimentVATable.closestLabel(0.75, 0.70));
  }

  @Test
  void closestLabelForExcitedCoordinatesReturnsExcited() {
    assertEquals("EXCITED", SentimentVATable.closestLabel(0.65, 0.85));
  }

  @Test
  void closestLabelForSadCoordinatesReturnsSad() {
    assertEquals("SAD", SentimentVATable.closestLabel(0.20, 0.30));
  }

  @Test
  void closestLabelForAngryCoordinatesReturnsAngry() {
    assertEquals("ANGRY", SentimentVATable.closestLabel(0.15, 0.80));
  }

  @Test
  void closestLabelForGloomyCoordinatesReturnsGloomy() {
    assertEquals("GLOOMY", SentimentVATable.closestLabel(0.20, 0.20));
  }

  @Test
  void closestLabelChoosesNearestByEuclideanDistance() {
    // A point very close to RELAXED (V=0.70, A=0.25) but not exact
    String label = SentimentVATable.closestLabel(0.71, 0.26);
    assertEquals("RELAXED", label);
  }

  @Test
  void allEightLabelsArePresentInTable() {
    String[] expected = {"HAPPY", "EXCITED", "RELAXED", "CONTENT", "SAD", "GLOOMY", "TENSE", "ANGRY"};
    for (String label : expected) {
      // Each label's own coordinates should resolve back to itself
      double[] va = SentimentVATable.vaForLabel(label);
      assertEquals(label, SentimentVATable.closestLabel(va[0], va[1]),
          "Label " + label + " should map back to itself");
    }
  }
}
