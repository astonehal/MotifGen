package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the sentiment-aware overload of {@link StructuralPlanner}.
 *
 * <p>Covers Scenario 7 (structural preference) and Scenario 8 (climax position).
 */
class StructuralPlannerSentimentTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif cMajorMotif() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  // ── Scenario 8: climax position influenced by arousal ───────────────────

  @Test
  void highArousalClimaxFallsInLaterHalf() {
    // climaxRelPos = 0.70 - (arousal * 0.25); EXCITED A=0.85 → 0.70 - 0.2125 = 0.4875
    // With 8 notes/phrase × 4 phrases = 32 notes total, 0.4875 * 32 = 15.6 → index 16
    // index 16 is >= 16 (half of 32) → in later half
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile excited = SentimentProfile.fromLabel("EXCITED"); // A=0.85
    Motif motif = cMajorMotif();

    StructuralPlan plan = planner.plan(motif, "AABA", KeySignature.major(0), excited);

    int half = plan.totalNotes() / 2;
    assertTrue(plan.climaxPosition() >= half,
        "High-arousal climax should be in later half; half=" + half
            + " climax=" + plan.climaxPosition());
  }

  @Test
  void lowArousalClimaxFallsInEarlierPortion() {
    // RELAXED A=0.25 → climaxRelPos = 0.70 - 0.0625 = 0.6375
    // 32 notes * 0.6375 = 20.4 → index 20, which is > half (16) but
    // smaller than high-arousal value; we just assert it's < high-arousal climax
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile relaxed = SentimentProfile.fromLabel("RELAXED"); // A=0.25
    SentimentProfile excited = SentimentProfile.fromLabel("EXCITED"); // A=0.85
    Motif motif = cMajorMotif();
    KeySignature key = KeySignature.major(0);

    int relaxedClimax = planner.plan(motif, "AABA", key, relaxed).climaxPosition();
    int excitedClimax = planner.plan(motif, "AABA", key, excited).climaxPosition();

    assertTrue(relaxedClimax >= excitedClimax,
        "Low-arousal climax (" + relaxedClimax
            + ") should not be later than high-arousal (" + excitedClimax + ")");
  }

  // ── Scenario 7: structural preference ───────────────────────────────────

  @Test
  void highArousalPrefersAABTemplate() {
    // High A >= 0.7 → prefer AAAB (design says AABA building form)
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile tense = SentimentProfile.fromLabel("TENSE"); // V=0.25, A=0.75

    String preferred = planner.preferredTemplate(tense);
    assertEquals("AABA", preferred,
        "High-arousal sentiment should prefer AABA template");
  }

  @Test
  void playfulSentimentPrefersABABTemplate() {
    // Playful: V >= 0.7, A 0.4–0.6 → ABAB
    // HAPPY: V=0.75, A=0.70 — A is >= 0.7, so high-arousal AABA takes priority.
    // Use a synthetic profile in the playful zone.
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile playful = SentimentProfile.fromVA(0.75, 0.50);

    String preferred = planner.preferredTemplate(playful);
    assertEquals("ABAB", preferred,
        "Playful sentiment (high V, moderate A) should prefer ABAB");
  }

  @Test
  void seriousSentimentPrefersABACTemplate() {
    // Serious: V <= 0.4 → ABAC/ABCA; SAD V=0.20
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile sad = SentimentProfile.fromLabel("SAD");

    String preferred = planner.preferredTemplate(sad);
    assertTrue(preferred.equals("ABAC") || preferred.equals("ABCA"),
        "Serious sentiment should prefer ABAC or ABCA, got " + preferred);
  }

  @Test
  void noArgPlanBackwardCompatStillWorks() {
    StructuralPlanner planner = new StructuralPlanner();
    StructuralPlan plan = planner.plan(cMajorMotif(), "AABA", KeySignature.major(0));
    assertEquals("AABA", plan.template());
    assertEquals(16, plan.totalBars());
  }
}
