package com.motifgen.intro;

import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IntroEntryPlanner} — covers all 4 entry-planning acceptance criteria.
 *
 * <p>Criteria covered:
 * <ul>
 *   <li>High-arousal: all by bar 2.</li>
 *   <li>Low-arousal: lead bar 1, others stagger 2 &amp; 3.</li>
 *   <li>Negative valence: drums lead (bar 1).</li>
 *   <li>Folk/ballad archetype: guitar leads (bar 1).</li>
 * </ul>
 */
class IntroEntryPlannerTest {

  private static final KeySignature C_MAJOR = KeySignature.major(0);

  // -----------------------------------------------------------------------
  // Scenario: High-arousal → all instruments enter by bar 2
  // -----------------------------------------------------------------------

  @Test
  void highArousal_allByBar2() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.9); // arousal > 0.75
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    assertTrue(plan.get(IntroEntryPlanner.GUITAR) <= 2, "Guitar should enter by bar 2");
    assertTrue(plan.get(IntroEntryPlanner.BASS)   <= 2, "Bass should enter by bar 2");
    assertTrue(plan.get(IntroEntryPlanner.DRUMS)  <= 2, "Drums should enter by bar 2");
  }

  @Test
  void highArousal_introSpans4Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    // offsetTicks = 4 * 4 * 480 = 7680
    assertEquals(7680L, ctx.offsetTicks());
  }

  // -----------------------------------------------------------------------
  // Scenario: Low-arousal → atmospheric staggered entry
  // -----------------------------------------------------------------------

  @Test
  void lowArousal_leadEntersBar1_othersStagger() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.3); // arousal <= 0.45
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    // Guitar is the default lead for non-negative-valence non-folk
    assertEquals(1, plan.get(IntroEntryPlanner.GUITAR), "Lead (guitar) enters bar 1");
    // Others must be bars 2 and 3
    int bassBar  = plan.get(IntroEntryPlanner.BASS);
    int drumsBar = plan.get(IntroEntryPlanner.DRUMS);
    assertTrue(bassBar >= 2 && bassBar <= 3, "Bass staggers to bar 2 or 3");
    assertTrue(drumsBar >= 2 && drumsBar <= 3, "Drums stagger to bar 2 or 3");
    assertNotEquals(bassBar, drumsBar, "Bass and drums should stagger to different bars");
  }

  // -----------------------------------------------------------------------
  // Scenario: Negative valence → drums lead
  // -----------------------------------------------------------------------

  @Test
  void negativeValence_drumsLeadBar1() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.2, 0.4); // valence < 0.35
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    assertEquals(1, plan.get(IntroEntryPlanner.DRUMS), "Drums should lead (bar 1) for negative valence");
  }

  // -----------------------------------------------------------------------
  // Scenario: Folk/ballad archetype → guitar leads
  // -----------------------------------------------------------------------

  @Test
  void folkArchetype_guitarLeadsBar1() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.4);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    assertEquals(1, plan.get(IntroEntryPlanner.GUITAR), "Guitar leads for folk archetype");
  }

  @Test
  void balladArchetype_guitarLeadsBar1() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.4);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    assertEquals(1, plan.get(IntroEntryPlanner.GUITAR), "Guitar leads for ballad archetype");
  }

  // -----------------------------------------------------------------------
  // Plan completeness
  // -----------------------------------------------------------------------

  @Test
  void planAlwaysContainsAllThreeInstruments() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.5);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    assertTrue(plan.containsKey(IntroEntryPlanner.GUITAR));
    assertTrue(plan.containsKey(IntroEntryPlanner.BASS));
    assertTrue(plan.containsKey(IntroEntryPlanner.DRUMS));
  }
}
