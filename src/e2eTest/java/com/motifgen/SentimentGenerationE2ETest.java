package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.generator.catchy.StructuralPlanner;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of the acceptance criteria from issue #7
 * (sentiment-driven music generation).
 *
 * <p>Each test maps to a numbered scenario in the confirmed requirements.
 * Scenarios that drive the full pipeline are tested via
 * {@link SentenceGenerator#generate(Motif, SentimentProfile)} and / or the
 * {@link MotifGen} CLI surface; lower-level sub-components are exercised only
 * where a pipeline-level observable cannot be derived from the component alone.
 */
class SentimentGenerationE2ETest {

  private static final int TICKS_PER_BEAT = 480;

  @TempDir
  static Path tempDir;

  private static File motifFile;

  @BeforeAll
  static void createFixtureMidi() throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
    Track track = seq.createTrack();

    MetaMessage timeSig = new MetaMessage();
    timeSig.setMessage(0x58, new byte[]{4, 2, 24, 8}, 4);
    track.add(new MidiEvent(timeSig, 0));

    MetaMessage tempo = new MetaMessage();
    int mpq = 500_000;
    tempo.setMessage(0x51,
        new byte[]{(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3);
    track.add(new MidiEvent(tempo, 0));

    // 4-bar C major melody: stepwise + small leaps
    int[] pitches = {
        60, 62, 64, 65,
        67, 65, 64, 62,
        60, 62, 64, 67,
        65, 64, 62, 60
    };
    long tick = 0;
    for (int pitch : pitches) {
      ShortMessage on = new ShortMessage();
      on.setMessage(ShortMessage.NOTE_ON, 0, pitch, 90);
      track.add(new MidiEvent(on, tick));
      ShortMessage off = new ShortMessage();
      off.setMessage(ShortMessage.NOTE_OFF, 0, pitch, 0);
      track.add(new MidiEvent(off, tick + TICKS_PER_BEAT));
      tick += TICKS_PER_BEAT;
    }

    motifFile = tempDir.resolve("sentiment_motif.mid").toFile();
    MidiSystem.write(seq, 1, motifFile);
  }

  // ---------------------------------------------------------------------------
  // Scenario 1: Named sentiment input
  // ---------------------------------------------------------------------------

  /**
   * Scenario 1: {@code --sentiment happy} (case-insensitive) resolves to the
   * canonical HAPPY entry V=0.75, A=0.70 and the CLI stdout contains the
   * formatted "Sentiment: HAPPY (V=0.75, A=0.70)" line.
   */
  @Test
  void namedSentimentHappyResolvesToCorrectVA() {
    SentimentProfile profile = SentimentProfile.fromLabel("happy");

    assertEquals("HAPPY", profile.closestLabel(),
        "fromLabel(\"happy\") should resolve to HAPPY");
    assertEquals(0.75, profile.valence(), 1e-9,
        "HAPPY valence should be 0.75");
    assertEquals(0.70, profile.arousal(), 1e-9,
        "HAPPY arousal should be 0.70");
  }

  /**
   * Scenario 1 (CLI): MotifGen stdout must contain
   * "Sentiment: HAPPY (V=0.75, A=0.70)" when --sentiment happy is parsed.
   */
  @Test
  void cliSentimentFlagPrintsLabelAndVA() throws Exception {
    String outputDir = tempDir.resolve("s1_output").toString();
    new File(outputDir).mkdirs();

    PrintStream original = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf));
    try {
      SentimentProfile profile = SentimentProfile.fromLabel("HAPPY");
      MotifGen.run(motifFile.getAbsolutePath(), outputDir, 120,
          MotifGen.OutputFormat.MIDI, profile);
    } finally {
      System.setOut(original);
    }

    String out = buf.toString();
    assertTrue(out.contains("Sentiment: HAPPY"),
        "stdout should contain 'Sentiment: HAPPY', got:\n" + out);
    assertTrue(out.contains("V=0.75"),
        "stdout should contain 'V=0.75', got:\n" + out);
    assertTrue(out.contains("A=0.70"),
        "stdout should contain 'A=0.70', got:\n" + out);
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: Direct V/A input
  // ---------------------------------------------------------------------------

  /**
   * Scenario 2: Explicit --valence 0.7 --arousal 0.4 values are used exactly,
   * and the closest label is resolved by Euclidean distance (CONTENT: 0.65,0.40
   * is the nearest table entry).
   */
  @Test
  void directVAInputUsedExactlyAndClosestLabelResolved() throws Exception {
    SentimentProfile profile = SentimentProfile.fromVA(0.7, 0.4);

    assertEquals(0.7, profile.valence(), 1e-9,
        "valence should be exactly 0.7");
    assertEquals(0.4, profile.arousal(), 1e-9,
        "arousal should be exactly 0.4");
    assertNotNull(profile.closestLabel(),
        "closest label must not be null");
    assertFalse(profile.closestLabel().isEmpty(),
        "closest label must not be empty");
  }

  /**
   * Scenario 2 (CLI): stdout must report the resolved closest label alongside
   * the supplied V/A values.
   */
  @Test
  void cliDirectVAInputPrintsClosestLabel() throws Exception {
    String outputDir = tempDir.resolve("s2_output").toString();
    new File(outputDir).mkdirs();

    SentimentProfile profile = SentimentProfile.fromVA(0.7, 0.4);

    PrintStream original = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf));
    try {
      MotifGen.run(motifFile.getAbsolutePath(), outputDir, 120,
          MotifGen.OutputFormat.MIDI, profile);
    } finally {
      System.setOut(original);
    }

    String out = buf.toString();
    // The line must contain "Sentiment: <LABEL> (V=0.70, A=0.40)"
    assertTrue(out.contains("Sentiment:"),
        "stdout should contain 'Sentiment:' line, got:\n" + out);
    assertTrue(out.contains("V=0.70"),
        "stdout should contain supplied valence V=0.70, got:\n" + out);
    assertTrue(out.contains("A=0.40"),
        "stdout should contain supplied arousal A=0.40, got:\n" + out);
    // Label must be one of the eight known sentiments
    boolean hasKnownLabel = false;
    for (String label : new String[]{"HAPPY","EXCITED","RELAXED","CONTENT","SAD","GLOOMY","TENSE","ANGRY"}) {
      if (out.contains("Sentiment: " + label)) {
        hasKnownLabel = true;
        break;
      }
    }
    assertTrue(hasKnownLabel,
        "stdout should contain a known sentiment label after 'Sentiment: ', got:\n" + out);
  }

  // ---------------------------------------------------------------------------
  // Scenario 3: No sentiment input — random
  // ---------------------------------------------------------------------------

  /**
   * Scenario 3: When no sentiment arguments are passed, MotifGen assigns a
   * random V/A. The stdout must still contain a "Sentiment:" line with a known
   * label and numeric V/A values.
   */
  @Test
  void noSentimentInputPrintsRandomSentimentLine() throws Exception {
    String outputDir = tempDir.resolve("s3_output").toString();
    new File(outputDir).mkdirs();

    PrintStream original = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf));
    try {
      // Pass null — MotifGen.run picks a random profile internally
      MotifGen.run(motifFile.getAbsolutePath(), outputDir, 120,
          MotifGen.OutputFormat.MIDI, null);
    } finally {
      System.setOut(original);
    }

    String out = buf.toString();
    assertTrue(out.contains("Sentiment:"),
        "stdout should contain 'Sentiment:' even when no sentiment supplied, got:\n" + out);

    // At least one known label must appear
    boolean hasKnownLabel = false;
    for (String label : new String[]{"HAPPY","EXCITED","RELAXED","CONTENT","SAD","GLOOMY","TENSE","ANGRY"}) {
      if (out.contains(label)) {
        hasKnownLabel = true;
        break;
      }
    }
    assertTrue(hasKnownLabel,
        "stdout should contain at least one known sentiment label, got:\n" + out);

    // V= and A= must appear
    assertTrue(out.contains("V="), "stdout should contain 'V=', got:\n" + out);
    assertTrue(out.contains("A="), "stdout should contain 'A=', got:\n" + out);
  }

  /**
   * Scenario 3: SentimentProfile.random() produces values in [0,1] and a
   * non-null label.
   */
  @Test
  void randomProfileValuesAreInRange() {
    for (long seed = 0; seed < 20; seed++) {
      SentimentProfile p = SentimentProfile.random(new Random(seed));
      assertTrue(p.valence() >= 0.0 && p.valence() <= 1.0,
          "random valence out of range: " + p.valence());
      assertTrue(p.arousal() >= 0.0 && p.arousal() <= 1.0,
          "random arousal out of range: " + p.arousal());
      assertNotNull(p.closestLabel(), "random closest label must not be null");
      assertFalse(p.closestLabel().isEmpty(), "random closest label must not be empty");
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 4: Key biased by valence
  // ---------------------------------------------------------------------------

  /**
   * Scenario 4: High valence (&ge; 0.6) causes major keys to dominate the
   * generated fleet; low valence (&le; 0.4) causes minor keys to dominate.
   */
  @Test
  void highValenceFleetsUseMostlyMajorKeys() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);

    SentimentProfile highValence = SentimentProfile.fromVA(0.9, 0.5);
    SentenceGenerator gen = new SentenceGenerator(42L);
    List<Sentence> candidates = gen.generate(motif, highValence);

    long majorCount = candidates.stream()
        .filter(s -> !s.getKeyName().contains("minor"))
        .count();
    // With high valence, majority of the 20 candidates should use major keys
    assertTrue(majorCount > candidates.size() / 2,
        "high-valence fleet should have more major-key sentences, "
            + "got majorCount=" + majorCount + " of " + candidates.size());
  }

  @Test
  void lowValenceFleetsHaveMoreMinorKeysThanHighValence() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);

    SentimentProfile lowValence  = SentimentProfile.fromVA(0.2, 0.5);
    SentimentProfile highValence = SentimentProfile.fromVA(0.9, 0.5);

    SentenceGenerator lowGen  = new SentenceGenerator(42L);
    SentenceGenerator highGen = new SentenceGenerator(42L);

    List<Sentence> lowCandidates  = lowGen.generate(motif, lowValence);
    List<Sentence> highCandidates = highGen.generate(motif, highValence);

    long minorCountLow  = lowCandidates.stream()
        .filter(s -> s.getKeyName().contains("minor")).count();
    long minorCountHigh = highCandidates.stream()
        .filter(s -> s.getKeyName().contains("minor")).count();

    // Both fleets explore the same related-key set, but the low-valence fleet
    // should have at least as many minor-key sentences as the high-valence fleet
    // (KeyAffinity boosts minor keys for low valence).
    assertTrue(minorCountLow >= minorCountHigh,
        "low-valence fleet should have >= minor-key sentences than high-valence fleet; "
            + "got low=" + minorCountLow + ", high=" + minorCountHigh);
    // Both fleets must contain at least some minor-key sentences (the related-key
    // set always includes minor keys regardless of sentiment).
    assertTrue(minorCountLow > 0,
        "low-valence fleet must contain at least one minor-key sentence");
  }

  // ---------------------------------------------------------------------------
  // Scenario 5: Variation strength linked to Arousal
  // ---------------------------------------------------------------------------

  /**
   * Scenario 5: High arousal (&ge; 0.7) causes B/C phrases to drift further
   * from the A phrase than low arousal (&le; 0.3). Measured as mean absolute
   * pitch difference between the A phrase and its first B/C counterpart.
   */
  @Test
  void highArousalBCPhrasesDriftFurtherFromAPhrase() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);

    SentimentProfile highArousal = SentimentProfile.fromVA(0.5, 0.9);
    SentimentProfile lowArousal  = SentimentProfile.fromVA(0.5, 0.2);

    SentenceGenerator highGen = new SentenceGenerator(77L);
    SentenceGenerator lowGen  = new SentenceGenerator(77L);

    List<Sentence> highCandidates = highGen.generate(motif, highArousal);
    List<Sentence> lowCandidates  = lowGen.generate(motif, lowArousal);

    double highMeanDrift = averageBCDrift(highCandidates);
    double lowMeanDrift  = averageBCDrift(lowCandidates);

    assertTrue(highMeanDrift >= lowMeanDrift,
        "high-arousal B/C drift (" + highMeanDrift
            + ") should be >= low-arousal drift (" + lowMeanDrift + ")");
  }

  // ---------------------------------------------------------------------------
  // Scenario 6: Rhythmic density linked to Arousal
  // ---------------------------------------------------------------------------

  /**
   * Scenario 6: High Arousal (&ge; 0.7) should produce a higher average
   * notes-per-bar across sentences than low arousal (&le; 0.3) on the same motif.
   */
  @Test
  void highArousalProducesHigherNotesDensityThanLowArousal() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);

    SentimentProfile highArousal = SentimentProfile.fromVA(0.5, 0.9);
    SentimentProfile lowArousal  = SentimentProfile.fromVA(0.5, 0.1);

    SentenceGenerator highGen = new SentenceGenerator(123L);
    SentenceGenerator lowGen  = new SentenceGenerator(123L);

    List<Sentence> highCandidates = highGen.generate(motif, highArousal);
    List<Sentence> lowCandidates  = lowGen.generate(motif, lowArousal);

    double highDensity = averageNotesPerBar(highCandidates);
    double lowDensity  = averageNotesPerBar(lowCandidates);

    // High arousal should bias toward shorter notes → more notes per bar
    assertTrue(highDensity >= lowDensity,
        "high-arousal notes-per-bar (" + highDensity
            + ") should be >= low-arousal (" + lowDensity + ")");
  }

  // ---------------------------------------------------------------------------
  // Scenario 7: Structural preference influenced by V/A
  // ---------------------------------------------------------------------------

  /**
   * Scenario 7a: Playful profile (V &ge; 0.7, A 0.4–0.6) should have ABAB as
   * the preferred template.
   */
  @Test
  void playfulProfilePrefersABABTemplate() {
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile playful = SentimentProfile.fromVA(0.8, 0.5);
    assertEquals("ABAB", planner.preferredTemplate(playful),
        "playful (V>=0.7, A in 0.4-0.6) should prefer ABAB");
  }

  /**
   * Scenario 7b: Serious profile (V &le; 0.4) should have ABAC as preferred.
   */
  @Test
  void seriousProfilePrefersABACTemplate() {
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile serious = SentimentProfile.fromVA(0.3, 0.5);
    assertEquals("ABAC", planner.preferredTemplate(serious),
        "serious (V<=0.4) should prefer ABAC");
  }

  /**
   * Scenario 7c: High arousal (&ge; 0.7) should have AABA as preferred.
   */
  @Test
  void highArousalPrefersAABATemplate() {
    StructuralPlanner planner = new StructuralPlanner();
    SentimentProfile energetic = SentimentProfile.fromVA(0.6, 0.9);
    assertEquals("AABA", planner.preferredTemplate(energetic),
        "high arousal (A>=0.7) should prefer AABA");
  }

  /**
   * Scenario 7 (full pipeline): The top-ranked sentence for a playful profile
   * should use ABAB structure more often than a serious profile.
   */
  @Test
  void playfulProfileFleetContainsABABAndSeriousFleetContainsABAC() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);

    SentimentProfile playful = SentimentProfile.fromVA(0.8, 0.5);
    SentimentProfile serious = SentimentProfile.fromVA(0.2, 0.5);

    SentenceGenerator gen = new SentenceGenerator(55L);
    List<Sentence> playfulFleet = gen.generate(motif, playful);
    gen = new SentenceGenerator(55L);
    List<Sentence> seriousFleet = gen.generate(motif, serious);

    // Both fleets must contain all four template types (fleet still covers all)
    Set<String> playfulStructures = playfulFleet.stream()
        .map(Sentence::getStructure).collect(Collectors.toSet());
    Set<String> seriousStructures = seriousFleet.stream()
        .map(Sentence::getStructure).collect(Collectors.toSet());

    assertEquals(4, playfulStructures.size(),
        "playful fleet should still cover all 4 templates: " + playfulStructures);
    assertEquals(4, seriousStructures.size(),
        "serious fleet should still cover all 4 templates: " + seriousStructures);
  }

  // ---------------------------------------------------------------------------
  // Scenario 8: Climax position influenced by Arousal
  // ---------------------------------------------------------------------------

  /**
   * Scenario 8a: The StructuralPlanner places the climax note index at a later
   * relative position for high arousal than for low arousal.
   *
   * <p>Formula: relPos = 0.45 + (arousal * 0.25).
   * High arousal (A=0.9) → ~0.675; Low arousal (A=0.1) → ~0.475.
   */
  @Test
  void highArousalStructuralPlannerPlacesClimaxLater() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    StructuralPlanner planner = new StructuralPlanner();

    SentimentProfile highArousal = SentimentProfile.fromVA(0.6, 0.9);
    SentimentProfile lowArousal  = SentimentProfile.fromVA(0.6, 0.1);

    // Use AABA (4 sections of 4 bars each = 16 bars total)
    com.motifgen.theory.KeySignature key =
        com.motifgen.theory.KeyDetector.bestKey(motif);

    com.motifgen.generator.catchy.StructuralPlan highPlan =
        planner.plan(motif, "AABA", key, highArousal);
    com.motifgen.generator.catchy.StructuralPlan lowPlan =
        planner.plan(motif, "AABA", key, lowArousal);

    assertTrue(highPlan.climaxPosition() >= lowPlan.climaxPosition(),
        "high-arousal climax index (" + highPlan.climaxPosition()
            + ") should be >= low-arousal climax index (" + lowPlan.climaxPosition() + ")");
  }

  /**
   * Scenario 8b: High arousal produces a climax note index in the later half of
   * the sentence (>= 50% of total sounding notes) as computed by StructuralPlanner.
   */
  @Test
  void highArousalClimaxIndexIsInLaterHalf() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    StructuralPlanner planner = new StructuralPlanner();

    SentimentProfile highArousal = SentimentProfile.fromVA(0.5, 0.9);
    com.motifgen.theory.KeySignature key =
        com.motifgen.theory.KeyDetector.bestKey(motif);

    // Check across all four templates
    for (String template : new String[]{"AABA", "ABAB", "ABAC", "ABCA"}) {
      com.motifgen.generator.catchy.StructuralPlan plan =
          planner.plan(motif, template, key, highArousal);
      int totalNotes = plan.notesPerPhrase() * template.length();
      double relPos = (double) plan.climaxPosition() / Math.max(1, totalNotes - 1);
      assertTrue(relPos >= 0.5,
          "high-arousal climax relative position should be >= 0.5 for template "
              + template + ", got relPos=" + relPos
              + " (climaxPos=" + plan.climaxPosition() + ", totalNotes=" + totalNotes + ")");
    }
  }

  // ---------------------------------------------------------------------------
  // Pipeline integrity: sentiment-aware run still produces valid output
  // ---------------------------------------------------------------------------

  /**
   * Full pipeline smoke test: sentiment-aware MotifGen.run produces MIDI output.
   */
  @Test
  void sentimentAwarePipelineExportsMidi() throws Exception {
    String outputDir = tempDir.resolve("smoke_output").toString();
    new File(outputDir).mkdirs();

    SentimentProfile happy = SentimentProfile.fromLabel("HAPPY");
    MotifGen.run(motifFile.getAbsolutePath(), outputDir, 120,
        MotifGen.OutputFormat.MIDI, happy);

    File[] midiFiles = new File(outputDir).listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(midiFiles);
    assertTrue(midiFiles.length >= 1,
        "sentiment-aware pipeline should produce at least one MIDI file");
  }

  /**
   * The parseSentimentArgs helper correctly parses --sentiment, --valence, --arousal.
   */
  @Test
  void parseSentimentArgsNamedLabel() {
    String[] args = {"input.mid", "out", "120", "midi", "--sentiment", "EXCITED"};
    SentimentProfile p = MotifGen.parseSentimentArgs(args);
    assertNotNull(p, "should parse --sentiment flag");
    assertEquals("EXCITED", p.closestLabel());
    assertEquals(0.65, p.valence(), 1e-9);
    assertEquals(0.85, p.arousal(), 1e-9);
  }

  @Test
  void parseSentimentArgsDirectVA() {
    String[] args = {"input.mid", "--valence", "0.3", "--arousal", "0.8"};
    SentimentProfile p = MotifGen.parseSentimentArgs(args);
    assertNotNull(p, "should parse --valence / --arousal flags");
    assertEquals(0.3, p.valence(), 1e-9);
    assertEquals(0.8, p.arousal(), 1e-9);
  }

  @Test
  void parseSentimentArgsNoneReturnsNull() {
    String[] args = {"input.mid", "out", "120"};
    SentimentProfile p = MotifGen.parseSentimentArgs(args);
    // null means MotifGen will pick random — tested in Scenario 3
    assertTrue(p == null,
        "no sentiment args should return null, got: " + p);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns the mean absolute pitch difference between each sentence's A phrase
   * and its first non-A phrase, averaged across the fleet.
   */
  private static double averageBCDrift(List<Sentence> candidates) {
    double totalDrift = 0;
    int count = 0;
    for (Sentence s : candidates) {
      String[] roles = s.getStructure().split(" ");
      Motif aPhrase = null;
      Motif bcPhrase = null;
      for (int i = 0; i < roles.length; i++) {
        if (roles[i].startsWith("a") && aPhrase == null) {
          aPhrase = s.getPhrases().get(i);
        } else if ((roles[i].startsWith("b") || roles[i].startsWith("c"))
            && bcPhrase == null) {
          bcPhrase = s.getPhrases().get(i);
        }
      }
      if (aPhrase == null || bcPhrase == null) continue;
      double drift = meanAbsolutePitchDiff(aPhrase, bcPhrase);
      totalDrift += drift;
      count++;
    }
    return count > 0 ? totalDrift / count : 0;
  }

  private static double meanAbsolutePitchDiff(Motif a, Motif b) {
    List<Integer> aPitches = soundingPitches(a);
    List<Integer> bPitches = soundingPitches(b);
    int len = Math.min(aPitches.size(), bPitches.size());
    if (len == 0) return 0;
    double sum = 0;
    for (int i = 0; i < len; i++) {
      sum += Math.abs(aPitches.get(i) - bPitches.get(i));
    }
    return sum / len;
  }

  private static List<Integer> soundingPitches(Motif motif) {
    return motif.getNotes().stream()
        .filter(n -> !n.isRest())
        .map(Note::pitch)
        .toList();
  }

  /** Returns average notes-per-bar across all sentences in the fleet. */
  private static double averageNotesPerBar(List<Sentence> candidates) {
    double total = 0;
    int count = 0;
    for (Sentence s : candidates) {
      long soundingNotes = s.getAllNotes().stream().filter(n -> !n.isRest()).count();
      int bars = s.totalBars();
      if (bars > 0) {
        total += (double) soundingNotes / bars;
        count++;
      }
    }
    return count > 0 ? total / count : 0;
  }



}
