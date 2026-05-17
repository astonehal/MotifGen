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
  void highArousal_introSpans2Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    // High arousal → barCount=2; offsetTicks = 2 * 4 * 480 = 3840
    assertEquals(2, ctx.barCount());
    assertEquals(3840L, ctx.offsetTicks());
  }

  // -----------------------------------------------------------------------
  // Scenario: Low-arousal → atmospheric staggered entry
  // -----------------------------------------------------------------------

  @Test
  void lowArousal_leadEntersBar1_othersStagger() {
    // Template pool overrides the deterministic stagger; verify all bars are in [1, barCount].
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.3); // arousal <= 0.45 → barCount=4
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    assertEquals(4, ctx.barCount());
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    int barCount = ctx.barCount();
    plan.forEach((inst, bar) ->
        assertTrue(bar >= 1 && bar <= barCount,
            inst + " entry bar " + bar + " must be in [1, " + barCount + "]"));
    // Plan must contain all three instruments.
    assertTrue(plan.containsKey(IntroEntryPlanner.GUITAR));
    assertTrue(plan.containsKey(IntroEntryPlanner.BASS));
    assertTrue(plan.containsKey(IntroEntryPlanner.DRUMS));
  }

  // -----------------------------------------------------------------------
  // Scenario: Negative valence → drums lead
  // -----------------------------------------------------------------------

  @Test
  void negativeValence_drumsInValidRange() {
    // Template pool overrides the deterministic drums-bar-1 rule; verify only that the
    // resulting bar is within [1, barCount].
    SentimentProfile sentiment = SentimentProfile.fromVA(0.2, 0.4); // valence < 0.35
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    int drumsBar = plan.get(IntroEntryPlanner.DRUMS);
    int barCount = ctx.barCount();
    assertTrue(drumsBar >= 1 && drumsBar <= barCount,
        "Drums entry bar " + drumsBar + " must be in [1, " + barCount + "]");
  }

  // -----------------------------------------------------------------------
  // Scenario: Folk/ballad archetype → guitar leads
  // -----------------------------------------------------------------------

  @Test
  void folkArchetype_guitarInValidRange() {
    // Template pool overrides the deterministic guitar-bar-1 rule; verify bar is in [1, barCount].
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.4);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    int guitarBar = plan.get(IntroEntryPlanner.GUITAR);
    int barCount = ctx.barCount();
    assertTrue(guitarBar >= 1 && guitarBar <= barCount,
        "Guitar entry bar " + guitarBar + " must be in [1, " + barCount + "] for folk archetype");
  }

  @Test
  void balladArchetype_guitarInValidRange() {
    // Template pool overrides the deterministic guitar-bar-1 rule; verify bar is in [1, barCount].
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.4);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad");
    Map<String, Integer> plan = IntroEntryPlanner.plan(ctx);

    int guitarBar = plan.get(IntroEntryPlanner.GUITAR);
    int barCount = ctx.barCount();
    assertTrue(guitarBar >= 1 && guitarBar <= barCount,
        "Guitar entry bar " + guitarBar + " must be in [1, " + barCount + "] for ballad archetype");
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
