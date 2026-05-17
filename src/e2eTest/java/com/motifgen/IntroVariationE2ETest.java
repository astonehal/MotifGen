package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.guitar.backing.DrumGrooveArchetype;
import com.motifgen.guitar.backing.DrumPattern;
import com.motifgen.intro.IntroContext;
import com.motifgen.intro.IntroGenerator;
import com.motifgen.intro.IntroTemplatePool;
import com.motifgen.intro.IntroTrack;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for issue #33 (improve intro variation, sentence-fit, and variable bar count).
 *
 * <p>Each test method mirrors one Gherkin scenario from the acceptance criteria.
 * All fixtures are generated programmatically — no external files are referenced.
 */
class IntroVariationE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR  = 4;

  // C major, root = 60.
  private static final KeySignature C_MAJOR = KeySignature.major(60 % 12);

  // ---------------------------------------------------------------------------
  // Scenario: High-arousal intro spans 2 bars
  // Given arousal > 0.75 → barCount = 2, offsetTicks = 2 × beatsPerBar × ticksPerBeat
  // ---------------------------------------------------------------------------

  /**
   * Given a sentiment with arousal > 0.75
   * When IntroContext is constructed
   * Then barCount == 2 and offsetTicks == 2 * beatsPerBar * ticksPerBeat
   */
  @Test
  void given_highArousal_when_contextCreated_then_barCount2AndOffsetTicks2Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9); // arousal = 0.9 > 0.75
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", TICKS_PER_BEAT, BEATS_PER_BAR);

    assertEquals(2, ctx.barCount(),
        "High arousal (> 0.75) must produce barCount = 2");

    long expectedOffset = 2L * BEATS_PER_BAR * TICKS_PER_BEAT;
    assertEquals(expectedOffset, ctx.offsetTicks(),
        "offsetTicks must equal 2 * beatsPerBar * ticksPerBeat for high arousal");
  }

  // ---------------------------------------------------------------------------
  // Scenario: Mid-arousal intro spans 3 bars
  // Given 0.45 ≤ arousal ≤ 0.75 → barCount = 3, offsetTicks = 3 × beatsPerBar × ticksPerBeat
  // ---------------------------------------------------------------------------

  /**
   * Given a sentiment with 0.45 <= arousal <= 0.75
   * When IntroContext is constructed
   * Then barCount == 3 and offsetTicks == 3 * beatsPerBar * ticksPerBeat
   */
  @Test
  void given_midArousal_when_contextCreated_then_barCount3AndOffsetTicks3Bars() {
    // Test boundary values: exactly 0.45 and exactly 0.75
    for (double arousal : new double[]{0.45, 0.60, 0.75}) {
      SentimentProfile sentiment = SentimentProfile.fromVA(0.6, arousal);
      IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "pop", TICKS_PER_BEAT, BEATS_PER_BAR);

      assertEquals(3, ctx.barCount(),
          "Mid arousal (" + arousal + " in [0.45,0.75]) must produce barCount = 3");

      long expectedOffset = 3L * BEATS_PER_BAR * TICKS_PER_BEAT;
      assertEquals(expectedOffset, ctx.offsetTicks(),
          "offsetTicks must equal 3 * beatsPerBar * ticksPerBeat for mid arousal " + arousal);
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario: Low-arousal intro spans 4 bars
  // Given arousal < 0.45 → barCount = 4, offsetTicks = 4 × beatsPerBar × ticksPerBeat
  // ---------------------------------------------------------------------------

  /**
   * Given a sentiment with arousal < 0.45
   * When IntroContext is constructed
   * Then barCount == 4 and offsetTicks == 4 * beatsPerBar * ticksPerBeat
   */
  @Test
  void given_lowArousal_when_contextCreated_then_barCount4AndOffsetTicks4Bars() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.3); // arousal = 0.3 < 0.45
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad", TICKS_PER_BEAT, BEATS_PER_BAR);

    assertEquals(4, ctx.barCount(),
        "Low arousal (< 0.45) must produce barCount = 4");

    long expectedOffset = 4L * BEATS_PER_BAR * TICKS_PER_BEAT;
    assertEquals(expectedOffset, ctx.offsetTicks(),
        "offsetTicks must equal 4 * beatsPerBar * ticksPerBeat for low arousal");
  }

  // ---------------------------------------------------------------------------
  // Scenario: Final bar always contains launch fill regardless of intro length
  // Given any bar count (2, 3, or 4 bars), the last bar has groove + snare fill
  // ---------------------------------------------------------------------------

  /**
   * Given bar counts of 2, 3, and 4 (covering all arousal tiers)
   * When drums are generated
   * Then the last bar's second half contains a snare fill and the bar opens with a crash
   */
  @Test
  void given_anyBarCount_when_drumsGenerated_then_lastBarHasLaunchFill() {
    // arousal values that yield barCount = 2, 3, 4
    double[][] arousalAndExpectedBars = {
        {0.9, 2},   // high → 2 bars
        {0.6, 3},   // mid  → 3 bars
        {0.3, 4}    // low  → 4 bars
    };

    for (double[] pair : arousalAndExpectedBars) {
      double arousal      = pair[0];
      int    expectedBars = (int) pair[1];

      SentimentProfile sentiment = SentimentProfile.fromVA(0.6, arousal);
      IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", TICKS_PER_BEAT, BEATS_PER_BAR);

      assertEquals(expectedBars, ctx.barCount(),
          "Expected barCount=" + expectedBars + " for arousal=" + arousal);

      IntroGenerator generator = new IntroGenerator();
      IntroTrack track = generator.generate(ctx);

      long barTicks     = (long) BEATS_PER_BAR * TICKS_PER_BEAT;
      long lastBarStart = (long) (expectedBars - 1) * barTicks;
      long lastBarHalf  = lastBarStart + barTicks / 2L;

      // The last bar must open with a crash cymbal.
      boolean hasCrash = track.drumEvents().stream()
          .anyMatch(e -> e.gmNote() == DrumPattern.CRASH && e.startTick() == lastBarStart);
      assertTrue(hasCrash,
          "Last bar (arousal=" + arousal + ", barCount=" + expectedBars
              + ") must have a crash on beat 1");

      // The second half of the last bar must contain snare fill hits.
      boolean hasSnareFill = track.drumEvents().stream()
          .anyMatch(e -> e.gmNote() == DrumPattern.SNARE
              && e.startTick() >= lastBarHalf
              && e.startTick() < lastBarStart + barTicks);
      assertTrue(hasSnareFill,
          "Last bar second half (arousal=" + arousal + ", barCount=" + expectedBars
              + ") must contain snare fill events");
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario: Sentence-aware vamp uses actual sentence chord root
  // Given firstChordRoot passed to IntroContext, vampTonicMidi has the same pitch class
  // ---------------------------------------------------------------------------

  /**
   * Given a firstChordRoot MIDI value passed to the extended IntroContext factory
   * When IntroContext is constructed
   * Then vampTonicMidi has the same pitch class as firstChordRoot
   */
  @Test
  void given_firstChordRootPassedToContext_when_contextCreated_then_vampTonicMidiMatchesPitchClass() {
    // Test several first chord roots across different octaves/pitch classes
    int[] firstChordRoots = {48, 52, 55, 60, 64, 67}; // C3, E3, G3, C4, E4, G4

    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);
    DrumGrooveArchetype drumArch = DrumGrooveArchetype.DRIVING;

    for (int root : firstChordRoots) {
      IntroContext ctx = IntroContext.of(
          sentiment, C_MAJOR, "driving",
          TICKS_PER_BEAT, BEATS_PER_BAR,
          root, drumArch);

      int expectedPitchClass = root % 12;
      int actualPitchClass   = ctx.vampTonicMidi() % 12;

      assertEquals(expectedPitchClass, actualPitchClass,
          "vampTonicMidi pitch class must match firstChordRoot pitch class for root=" + root
              + "; vampTonicMidi=" + ctx.vampTonicMidi());
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario: Intro groove archetype matches sentence
  // Given drumArchetype passed to IntroContext, IntroDrumBuilder uses that archetype
  // ---------------------------------------------------------------------------

  /**
   * Given a specific DrumGrooveArchetype passed via the extended IntroContext factory
   * When IntroContext is constructed
   * Then ctx.drumArchetype() returns that exact archetype
   */
  @Test
  void given_drumArchetypePassedToContext_when_contextCreated_then_drumArchetypePreserved() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.6);

    for (DrumGrooveArchetype archetype : DrumGrooveArchetype.values()) {
      IntroContext ctx = IntroContext.of(
          sentiment, C_MAJOR, "driving",
          TICKS_PER_BEAT, BEATS_PER_BAR,
          60, archetype);

      assertEquals(archetype, ctx.drumArchetype(),
          "drumArchetype must be preserved exactly when passed via extended factory; "
              + "expected=" + archetype + " got=" + ctx.drumArchetype());
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario: Template pool produces variation within same sentiment tier
  // Given same sentiment + archetype, 20 intros generated → at least 2 distinct patterns each
  // ---------------------------------------------------------------------------

  /**
   * Given the same sentiment and archetype (same arousal tier)
   * When 20 entry templates are drawn from IntroTemplatePool
   * Then at least 2 distinct template names appear, proving variation
   */
  @Test
  void given_sameSentimentTier_when_20EntryTemplatesDrawn_then_atLeast2DistinctPatterns() {
    // Test each arousal tier
    double[][] tiersAndArousal = {
        {0.9, 0},  // HIGH tier
        {0.6, 1},  // MID tier
        {0.3, 2}   // LOW tier
    };

    Random rng = new Random(42L); // fixed seed for reproducibility

    for (double[] pair : tiersAndArousal) {
      double arousal = pair[0];
      SentimentProfile sentiment = SentimentProfile.fromVA(0.6, arousal);
      IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", TICKS_PER_BEAT, BEATS_PER_BAR);

      Set<String> entryNames  = new HashSet<>();
      Set<String> guitarNames = new HashSet<>();
      Set<String> drumNames   = new HashSet<>();

      for (int i = 0; i < 20; i++) {
        entryNames.add(IntroTemplatePool.drawEntry(ctx, rng).name());
        guitarNames.add(IntroTemplatePool.drawGuitar(ctx, rng).name());
        drumNames.add(IntroTemplatePool.drawDrum(ctx, rng).name());
      }

      assertTrue(entryNames.size() >= 2,
          "Entry templates must vary: arousal=" + arousal + " produced only "
              + entryNames.size() + " distinct name(s)");
      assertTrue(guitarNames.size() >= 2,
          "Guitar templates must vary: arousal=" + arousal + " produced only "
              + guitarNames.size() + " distinct name(s)");
      assertTrue(drumNames.size() >= 2,
          "Drum templates must vary: arousal=" + arousal + " produced only "
              + drumNames.size() + " distinct name(s)");
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario: All generated intros score >= 30.0
  // Given any sentiment, IntroGenerator returns a track with score >= 30.0
  // ---------------------------------------------------------------------------

  /**
   * Given a range of sentiment profiles
   * When IntroGenerator.generate() is called
   * Then the returned IntroTrack has score >= 30.0
   */
  @Test
  void given_anySentiment_when_introGenerated_then_scoreAtLeast30() {
    // Sample a spread of (valence, arousal) pairs covering all bar-count tiers
    double[][] sentiments = {
        {0.8, 0.9},  // high arousal → barCount=2
        {0.6, 0.6},  // mid arousal  → barCount=3
        {0.5, 0.3},  // low arousal  → barCount=4
        {0.2, 0.8},  // negative valence, high arousal
        {0.9, 0.44}, // positive valence, just-below-mid arousal → barCount=4
    };

    IntroGenerator generator = new IntroGenerator();

    for (double[] sv : sentiments) {
      double valence = sv[0];
      double arousal = sv[1];
      SentimentProfile sentiment = SentimentProfile.fromVA(valence, arousal);
      IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", TICKS_PER_BEAT, BEATS_PER_BAR);

      IntroTrack track = generator.generate(ctx);

      assertNotNull(track,
          "IntroGenerator must return a non-null track for valence=" + valence
              + " arousal=" + arousal);
      assertTrue(track.score() >= 30.0,
          "Generated intro score must be >= 30.0; got " + track.score()
              + " for valence=" + valence + " arousal=" + arousal);
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario: Variable-length intro correct in MIDI and MusicXML exports
  // All intro notes within [0, offsetTicks); sentence notes at or after offsetTicks
  // ---------------------------------------------------------------------------

  /**
   * Given intros of 2, 3, and 4 bars
   * When the intro is generated
   * Then all guitar, bass, and drum events fall within [0, offsetTicks)
   * and offsetTicks equals barCount * beatsPerBar * ticksPerBeat
   */
  @Test
  void given_variableLengthIntro_when_generated_then_allEventsWithinOffsetAndOffsetMatchesBarCount() {
    double[][] arousalAndExpectedBars = {
        {0.9, 2},   // high → 2 bars
        {0.6, 3},   // mid  → 3 bars
        {0.3, 4}    // low  → 4 bars
    };

    IntroGenerator generator = new IntroGenerator();

    for (double[] pair : arousalAndExpectedBars) {
      double arousal      = pair[0];
      int    expectedBars = (int) pair[1];

      SentimentProfile sentiment = SentimentProfile.fromVA(0.6, arousal);
      IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", TICKS_PER_BEAT, BEATS_PER_BAR);

      assertEquals(expectedBars, ctx.barCount(),
          "Expected barCount=" + expectedBars + " for arousal=" + arousal);

      long expectedOffset = (long) expectedBars * BEATS_PER_BAR * TICKS_PER_BEAT;
      assertEquals(expectedOffset, ctx.offsetTicks(),
          "offsetTicks must equal barCount * beatsPerBar * ticksPerBeat");

      IntroTrack track = generator.generate(ctx);

      // IntroTrack propagates the same offsetTicks from the context.
      assertEquals(expectedOffset, track.offsetTicks(),
          "IntroTrack.offsetTicks must match ctx.offsetTicks()");

      long offsetTicks = track.offsetTicks();

      // All guitar events must be within [0, offsetTicks).
      track.guitarEvents().forEach(cn ->
          assertTrue(cn.note().startTick() >= 0 && cn.note().startTick() < offsetTicks,
              "Guitar event tick " + cn.note().startTick()
                  + " must be in [0, " + offsetTicks + ") for barCount=" + expectedBars));

      // All bass events must be within [0, offsetTicks).
      track.bassEvents().forEach(cn ->
          assertTrue(cn.note().startTick() >= 0 && cn.note().startTick() < offsetTicks,
              "Bass event tick " + cn.note().startTick()
                  + " must be in [0, " + offsetTicks + ") for barCount=" + expectedBars));

      // All drum events must be within [0, offsetTicks).
      track.drumEvents().forEach(e ->
          assertTrue(e.startTick() >= 0 && e.startTick() < offsetTicks,
              "Drum event tick " + e.startTick()
                  + " must be in [0, " + offsetTicks + ") for barCount=" + expectedBars));
    }
  }
}
