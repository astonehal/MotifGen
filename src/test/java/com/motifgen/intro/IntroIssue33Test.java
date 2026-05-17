package com.motifgen.intro;

import com.motifgen.guitar.backing.DrumGrooveArchetype;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for GitHub issue #33 — Improve intro variation, sentence-fit, and variable bar count.
 *
 * <p>Acceptance criteria covered:
 * <ol>
 *   <li>High-arousal intro spans 2 bars (offsetTicks = 2 * beatsPerBar * ticksPerBeat)</li>
 *   <li>Mid-arousal intro spans 3 bars</li>
 *   <li>Low-arousal intro spans 4 bars</li>
 *   <li>Final bar always contains launch fill (groove + snare fill)</li>
 *   <li>Sentence-aware vamp uses actual sentence chord root (vampTonicMidi)</li>
 *   <li>Intro groove archetype matches sentence (drumArchetype)</li>
 *   <li>Template pool produces variation (at least 2 distinct patterns from 20 intros)</li>
 *   <li>All generated intros score >= 30.0 (up to 8 retry attempts)</li>
 *   <li>Variable-length intro: all notes within [0, offsetTicks); sentence at or after</li>
 * </ol>
 */
class IntroIssue33Test {

  private static final int PPQ = 480;
  private static final int BPB = 4;
  private static final KeySignature C_MAJOR = KeySignature.major(0);

  // -----------------------------------------------------------------------
  // Scenario 1: High-arousal intro spans 2 bars
  // -----------------------------------------------------------------------

  @Test
  void highArousal_barCountIs2() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9); // arousal > 0.75
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    assertEquals(2, ctx.barCount(), "Arousal > 0.75 should give barCount=2");
  }

  @Test
  void highArousal_offsetTicksIs2Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    long expected = 2L * BPB * PPQ;
    assertEquals(expected, ctx.offsetTicks(),
        "High-arousal offsetTicks should be 2 * beatsPerBar * ticksPerBeat");
  }

  // -----------------------------------------------------------------------
  // Scenario 2: Mid-arousal intro spans 3 bars
  // -----------------------------------------------------------------------

  @Test
  void midArousal_barCountIs3_lowerBound() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.45); // arousal == 0.45
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    assertEquals(3, ctx.barCount(), "Arousal == 0.45 should give barCount=3");
  }

  @Test
  void midArousal_barCountIs3_upperBound() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.75); // arousal == 0.75
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    assertEquals(3, ctx.barCount(), "Arousal == 0.75 should give barCount=3");
  }

  @Test
  void midArousal_offsetTicksIs3Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    long expected = 3L * BPB * PPQ;
    assertEquals(expected, ctx.offsetTicks(),
        "Mid-arousal offsetTicks should be 3 * beatsPerBar * ticksPerBeat");
  }

  // -----------------------------------------------------------------------
  // Scenario 3: Low-arousal intro spans 4 bars
  // -----------------------------------------------------------------------

  @Test
  void lowArousal_barCountIs4() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.3); // arousal < 0.45
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad", PPQ, BPB);
    assertEquals(4, ctx.barCount(), "Arousal < 0.45 should give barCount=4");
  }

  @Test
  void lowArousal_offsetTicksIs4Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.2);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad", PPQ, BPB);
    long expected = 4L * BPB * PPQ;
    assertEquals(expected, ctx.offsetTicks(),
        "Low-arousal offsetTicks should be 4 * beatsPerBar * ticksPerBeat");
  }

  // -----------------------------------------------------------------------
  // Scenario 4: Final bar always contains launch fill (snare hits in last bar)
  // -----------------------------------------------------------------------

  @Test
  void launchFill_presentIn2BarIntro() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    IntroDrumBuilder db = new IntroDrumBuilder();
    var events = db.build(ctx, 1);

    long lastBarStart = (long) (ctx.barCount() - 1) * BPB * PPQ;
    boolean hasSnare = events.stream()
        .anyMatch(ev -> ev.startTick() >= lastBarStart
            && ev.gmNote() == com.motifgen.guitar.backing.DrumPattern.SNARE);
    assertTrue(hasSnare, "Last bar of 2-bar intro should contain snare fill");
  }

  @Test
  void launchFill_presentIn3BarIntro() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    IntroDrumBuilder db = new IntroDrumBuilder();
    var events = db.build(ctx, 1);

    long lastBarStart = (long) (ctx.barCount() - 1) * BPB * PPQ;
    boolean hasSnare = events.stream()
        .anyMatch(ev -> ev.startTick() >= lastBarStart
            && ev.gmNote() == com.motifgen.guitar.backing.DrumPattern.SNARE);
    assertTrue(hasSnare, "Last bar of 3-bar intro should contain snare fill");
  }

  @Test
  void launchFill_presentIn4BarIntro() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.2);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad", PPQ, BPB);
    IntroDrumBuilder db = new IntroDrumBuilder();
    var events = db.build(ctx, 1);

    long lastBarStart = (long) (ctx.barCount() - 1) * BPB * PPQ;
    boolean hasSnare = events.stream()
        .anyMatch(ev -> ev.startTick() >= lastBarStart
            && ev.gmNote() == com.motifgen.guitar.backing.DrumPattern.SNARE);
    assertTrue(hasSnare, "Last bar of 4-bar intro should contain snare fill");
  }

  // -----------------------------------------------------------------------
  // Scenario 5: Sentence-aware vamp uses actual sentence chord root
  // -----------------------------------------------------------------------

  @Test
  void vampTonicMidi_storedFromFirstChordRoot() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    // firstChordRoot = E4 = 64; normalised to register [40,76] it stays ~64.
    int firstChordRoot = 64;
    IntroContext ctx = IntroContext.of(
        sentiment, C_MAJOR, "folk", PPQ, BPB, firstChordRoot, DrumGrooveArchetype.FOLK);
    // vampTonicMidi normalised to guitar register [40, 76]
    int tonic = ctx.vampTonicMidi();
    assertTrue(tonic >= 40 && tonic <= 76,
        "vampTonicMidi should be in guitar register [40,76], was " + tonic);
    // Pitch class should match firstChordRoot pitch class
    assertEquals(firstChordRoot % 12, tonic % 12,
        "vampTonicMidi pitch class should match firstChordRoot");
  }

  @Test
  void vampTonicMidi_usesKeyRootWhenNotProvided() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    // Key root of C_MAJOR = 0; vampTonicMidi should be C in guitar register
    assertEquals(0, ctx.vampTonicMidi() % 12,
        "vampTonicMidi should default to key root pitch class");
  }

  // -----------------------------------------------------------------------
  // Scenario 6: Intro groove archetype matches sentence
  // -----------------------------------------------------------------------

  @Test
  void drumArchetype_storedFromParameter() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(
        sentiment, C_MAJOR, "folk", PPQ, BPB, 60, DrumGrooveArchetype.FUNK);
    assertEquals(DrumGrooveArchetype.FUNK, ctx.drumArchetype(),
        "drumArchetype should match the passed-in archetype");
  }

  @Test
  void drumArchetype_derivedFromArchetypeString_driving() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    assertEquals(DrumGrooveArchetype.DRIVING, ctx.drumArchetype());
  }

  @Test
  void drumArchetype_derivedFromArchetypeString_folk() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    assertEquals(DrumGrooveArchetype.FOLK, ctx.drumArchetype());
  }

  @Test
  void drumArchetype_derivedFromArchetypeString_ballad() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.2);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad", PPQ, BPB);
    assertEquals(DrumGrooveArchetype.BALLAD, ctx.drumArchetype());
  }

  @Test
  void drumArchetype_unknownDefaultsToDriving() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.5);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "jazzfusion", PPQ, BPB);
    assertEquals(DrumGrooveArchetype.DRIVING, ctx.drumArchetype());
  }

  // -----------------------------------------------------------------------
  // Scenario 7: Template pool produces variation (20 intros → >= 2 distinct patterns)
  // -----------------------------------------------------------------------

  @Test
  void templatePool_producesVariation_entryPatterns() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.8);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    Random rng = new Random(42);

    Set<String> entrySignatures = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      IntroTemplatePool.EntryTemplate t = IntroTemplatePool.drawEntry(ctx, rng);
      entrySignatures.add(t.name());
    }
    assertTrue(entrySignatures.size() >= 2,
        "20 draws should produce at least 2 distinct entry templates, got: " + entrySignatures);
  }

  @Test
  void templatePool_producesVariation_guitarPatterns() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.8);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    Random rng = new Random(42);

    Set<String> guitarNames = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      IntroTemplatePool.GuitarTemplate t = IntroTemplatePool.drawGuitar(ctx, rng);
      guitarNames.add(t.name());
    }
    assertTrue(guitarNames.size() >= 2,
        "20 draws should produce at least 2 distinct guitar templates, got: " + guitarNames);
  }

  @Test
  void templatePool_producesVariation_drumSubPatterns() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.8);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    Random rng = new Random(42);

    Set<String> drumNames = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      IntroTemplatePool.DrumSubTemplate t = IntroTemplatePool.drawDrum(ctx, rng);
      drumNames.add(t.name());
    }
    assertTrue(drumNames.size() >= 2,
        "20 draws should produce at least 2 distinct drum sub-templates, got: " + drumNames);
  }

  // -----------------------------------------------------------------------
  // Scenario 8: All generated intros score >= 30.0 (with up to 8 retries)
  // -----------------------------------------------------------------------

  @Test
  void generator_scoreAtLeast30_highArousal() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    IntroTrack track = new IntroGenerator().generate(ctx);
    assertTrue(track.score() >= 30.0,
        "Generated intro should score >= 30.0, got: " + track.score());
  }

  @Test
  void generator_scoreAtLeast30_midArousal() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    IntroTrack track = new IntroGenerator().generate(ctx);
    assertTrue(track.score() >= 30.0,
        "Generated intro should score >= 30.0, got: " + track.score());
  }

  @Test
  void generator_scoreAtLeast30_lowArousal() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.2);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad", PPQ, BPB);
    IntroTrack track = new IntroGenerator().generate(ctx);
    assertTrue(track.score() >= 30.0,
        "Generated intro should score >= 30.0, got: " + track.score());
  }

  // -----------------------------------------------------------------------
  // Scenario 9: Variable-length intro correct — notes within [0, offsetTicks)
  // -----------------------------------------------------------------------

  @Test
  void variableLength_2bar_guitarNotesWithinOffset() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    long offset = ctx.offsetTicks();

    var events = new IntroGuitarBuilder().build(ctx, 1);
    for (var ev : events) {
      assertTrue(ev.note().startTick() >= 0 && ev.note().startTick() < offset,
          "Guitar note at tick " + ev.note().startTick()
              + " should be within [0, " + offset + ")");
    }
  }

  @Test
  void variableLength_2bar_drumNotesWithinOffset() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    long offset = ctx.offsetTicks();

    var events = new IntroDrumBuilder().build(ctx, 1);
    for (var ev : events) {
      assertTrue(ev.startTick() >= 0 && ev.startTick() < offset,
          "Drum event at tick " + ev.startTick()
              + " should be within [0, " + offset + ")");
    }
  }

  @Test
  void variableLength_3bar_bassNotesWithinOffset() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    long offset = ctx.offsetTicks();

    var events = new IntroBassBuilder().build(ctx, 1);
    for (var ev : events) {
      assertTrue(ev.note().startTick() >= 0 && ev.note().startTick() < offset,
          "Bass note at tick " + ev.note().startTick()
              + " should be within [0, " + offset + ")");
    }
  }

  // -----------------------------------------------------------------------
  // IntroScorer: meetsMinimum helper
  // -----------------------------------------------------------------------

  @Test
  void scorer_meetsMinimum_trueFor30Plus() {
    IntroScorer scorer = new IntroScorer();
    assertTrue(scorer.meetsMinimum(30.0));
    assertTrue(scorer.meetsMinimum(100.0));
  }

  @Test
  void scorer_meetsMinimum_falseBelow30() {
    IntroScorer scorer = new IntroScorer();
    assertFalse(scorer.meetsMinimum(29.9));
    assertFalse(scorer.meetsMinimum(0.0));
  }

  // -----------------------------------------------------------------------
  // IntroScorer: rampScore denominator safe for 2-bar intros
  // -----------------------------------------------------------------------

  @Test
  void scorer_2barIntro_doesNotThrow() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    IntroScorer scorer = new IntroScorer();
    IntroDrumBuilder db = new IntroDrumBuilder();
    IntroBassBuilder bb = new IntroBassBuilder();
    IntroGuitarBuilder gb = new IntroGuitarBuilder();
    var plan = IntroEntryPlanner.plan(ctx);
    var guitar = gb.build(ctx, plan.getOrDefault(IntroEntryPlanner.GUITAR, 1));
    var bass   = bb.build(ctx, plan.getOrDefault(IntroEntryPlanner.BASS, 1));
    var drums  = db.build(ctx, plan.getOrDefault(IntroEntryPlanner.DRUMS, 1));
    IntroTrack track = IntroTrack.of(guitar, bass, drums, 0.0, ctx);

    assertDoesNotThrow(() -> scorer.score(track, plan, ctx),
        "Scoring a 2-bar intro should not throw");
    double score = scorer.score(track, plan, ctx);
    assertTrue(score >= 0.0 && score <= 100.0);
  }

  // -----------------------------------------------------------------------
  // IntroGenerator: 8-attempt retry, offsetTicks matches barCount
  // -----------------------------------------------------------------------

  @Test
  void generator_2bar_offsetMatchesBarCount() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    IntroTrack track = new IntroGenerator().generate(ctx);
    assertEquals(ctx.offsetTicks(), track.offsetTicks(),
        "offsetTicks in track should match 2-bar context");
  }

  @Test
  void generator_3bar_offsetMatchesBarCount() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "folk", PPQ, BPB);
    IntroTrack track = new IntroGenerator().generate(ctx);
    assertEquals(ctx.offsetTicks(), track.offsetTicks(),
        "offsetTicks in track should match 3-bar context");
  }

  // -----------------------------------------------------------------------
  // EntryTemplate clamped to barCount
  // -----------------------------------------------------------------------

  @Test
  void entryPlanner_templateBarsClampedToBarCount() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9); // barCount=2
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", PPQ, BPB);
    // All planned bars must be <= barCount
    var plan = IntroEntryPlanner.plan(ctx);
    int barCount = ctx.barCount();
    for (int bar : plan.values()) {
      assertTrue(bar <= barCount,
          "Planned entry bar " + bar + " exceeds barCount=" + barCount);
    }
  }

  // -----------------------------------------------------------------------
  // IntroDrumBuilder: archetype-aware groove kernel
  // -----------------------------------------------------------------------

  @Test
  void drumBuilder_drivingArchetype_kickOnBeats1And3() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(
        sentiment, C_MAJOR, "driving", PPQ, BPB, 60, DrumGrooveArchetype.DRIVING);
    var events = new IntroDrumBuilder().build(ctx, 1);
    // Driving: kick on beats 1 and 3 of a groove bar (bar 0 = first bar)
    long beat1 = 0L;
    long beat3 = 2L * PPQ;
    boolean hasKickBeat1 = events.stream()
        .anyMatch(ev -> ev.startTick() == beat1
            && ev.gmNote() == com.motifgen.guitar.backing.DrumPattern.KICK);
    boolean hasKickBeat3 = events.stream()
        .anyMatch(ev -> ev.startTick() == beat3
            && ev.gmNote() == com.motifgen.guitar.backing.DrumPattern.KICK);
    assertTrue(hasKickBeat1, "DRIVING archetype should have kick on beat 1");
    assertTrue(hasKickBeat3, "DRIVING archetype should have kick on beat 3");
  }

  @Test
  void drumBuilder_folkArchetype_kickOnBeat1Only() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    IntroContext ctx = IntroContext.of(
        sentiment, C_MAJOR, "folk", PPQ, BPB, 60, DrumGrooveArchetype.FOLK);
    var events = new IntroDrumBuilder().build(ctx, 1);
    // FOLK/BALLAD: kick on beat 1, snare on beat 3 — so beat 2 should NOT have kick in groove bars
    long beat2 = (long) PPQ;
    boolean hasKickBeat2 = events.stream()
        .filter(ev -> ev.startTick() < (long) BPB * PPQ) // only bar 1
        .anyMatch(ev -> ev.startTick() == beat2
            && ev.gmNote() == com.motifgen.guitar.backing.DrumPattern.KICK);
    assertFalse(hasKickBeat2, "FOLK archetype groove bar should NOT have kick on beat 2");
  }
}
