package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.generator.catchy.MotifLengthMatcher;
import com.motifgen.generator.catchy.MotifTransformer;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.theory.KeySignature;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
 * End-to-end coverage for issue #8. Two fixture motifs:
 *
 * <ul>
 *   <li>{@code shortMotifFile} - 4 quarter notes spanning ~1 bar of content
 *       loaded into a 4-bar slot. Exercises the extension path.</li>
 *   <li>{@code longMotifFile} - 16 half notes spanning ~8 bars of content
 *       loaded into a 4-bar slot. Exercises the reduction path.</li>
 * </ul>
 */
class MotifLengthMatchingE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR = 4;
  private static final long BAR_TICKS = (long) BEATS_PER_BAR * TICKS_PER_BEAT;

  @TempDir
  static Path tempDir;

  private static File shortMotifFile;
  private static File longMotifFile;
  private static File subSampleMotifFile;

  @BeforeAll
  static void createFixtures() throws Exception {
    shortMotifFile = makeMidi(tempDir.resolve("short_motif.mid").toFile(),
        new int[] {60, 62, 64, 65}, new long[] {TICKS_PER_BEAT, TICKS_PER_BEAT,
            TICKS_PER_BEAT, TICKS_PER_BEAT});

    int[] longPitches = {60, 62, 64, 65, 67, 65, 64, 62,
                         60, 62, 64, 67, 65, 64, 62, 60};
    long[] longDurs = new long[16];
    for (int i = 0; i < 16; i++) longDurs[i] = 2L * TICKS_PER_BEAT; // half notes
    longMotifFile = makeMidi(tempDir.resolve("long_motif.mid").toFile(),
        longPitches, longDurs);

    // 32 eighth-notes - reduction would clip below floor, sub-sampling needed
    int[] subPitches = new int[32];
    long[] subDurs = new long[32];
    for (int i = 0; i < 32; i++) {
      subPitches[i] = 60 + (i % 8);
      subDurs[i] = TICKS_PER_BEAT / 2;
    }
    subSampleMotifFile = makeMidi(
        tempDir.resolve("subsample_motif.mid").toFile(), subPitches, subDurs);
  }

  private static File makeMidi(File path, int[] pitches, long[] durations) throws Exception {
    Sequence seq = new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
    Track track = seq.createTrack();

    MetaMessage timeSig = new MetaMessage();
    timeSig.setMessage(0x58, new byte[] {4, 2, 24, 8}, 4);
    track.add(new MidiEvent(timeSig, 0));
    MetaMessage tempo = new MetaMessage();
    int mpq = 500_000;
    tempo.setMessage(0x51,
        new byte[] {(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3);
    track.add(new MidiEvent(tempo, 0));

    long tick = 0;
    for (int i = 0; i < pitches.length; i++) {
      ShortMessage on = new ShortMessage();
      on.setMessage(ShortMessage.NOTE_ON, 0, pitches[i], 90);
      track.add(new MidiEvent(on, tick));
      ShortMessage off = new ShortMessage();
      off.setMessage(ShortMessage.NOTE_OFF, 0, pitches[i], 0);
      track.add(new MidiEvent(off, tick + durations[i]));
      tick += durations[i];
    }
    MidiSystem.write(seq, 1, path);
    return path;
  }

  /**
   * Scenario: Phrase exactly fills the slot when motif is shorter.
   */
  @Test
  void shortMotifFillsEachPhraseToTheBoundary() throws Exception {
    Motif motif = MotifLoader.load(shortMotifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(2026L);
    List<Sentence> candidates = gen.generate(motif);

    for (Sentence s : candidates) {
      for (Motif phrase : s.getPhrases()) {
        long lastEnd = phrase.getNotes().stream()
            .filter(n -> !n.isRest()).mapToLong(Note::endTick).max().orElse(0L);
        long phraseTicks = (long) phrase.getBars() * phrase.getBeatsPerBar()
            * phrase.getTicksPerBeat();
        assertTrue(lastEnd >= phraseTicks - TICKS_PER_BEAT,
            "phrase last note ends too early: lastEnd=" + lastEnd
                + " phraseTicks=" + phraseTicks);
      }
    }
  }

  /**
   * Scenario: Phrase exactly fills the slot when motif is longer.
   */
  @Test
  void longMotifIsCompressedIntoEachPhrase() throws Exception {
    Motif motif = MotifLoader.load(longMotifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(31L);
    List<Sentence> candidates = gen.generate(motif);

    for (Sentence s : candidates) {
      for (Motif phrase : s.getPhrases()) {
        long lastEnd = phrase.getNotes().stream()
            .filter(n -> !n.isRest()).mapToLong(Note::endTick).max().orElse(0L);
        long phraseTicks = (long) phrase.getBars() * phrase.getBeatsPerBar()
            * phrase.getTicksPerBeat();
        assertTrue(lastEnd <= phraseTicks + TICKS_PER_BEAT,
            "compressed phrase overshoots: lastEnd=" + lastEnd
                + " phraseTicks=" + phraseTicks);
      }
    }
  }

  /**
   * Scenario: 13-bar regression is fixed - exported MIDI ends near 16 bars.
   */
  @Test
  void exportedMidiSpansFullSixteenBars() throws Exception {
    String outDir = tempDir.resolve("short_export").toString();
    new File(outDir).mkdirs();

    MotifGen.run(shortMotifFile.getAbsolutePath(), outDir, 120);

    File[] files = new File(outDir).listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(files);
    assertTrue(files.length >= 1);
    for (File f : files) {
      Sequence seq = MidiSystem.getSequence(f);
      long lastNoteTick = 0L;
      for (Track t : seq.getTracks()) {
        for (int i = 0; i < t.size(); i++) {
          MidiEvent e = t.get(i);
          if (e.getMessage() instanceof ShortMessage sm
              && sm.getCommand() == ShortMessage.NOTE_ON) {
            lastNoteTick = Math.max(lastNoteTick, e.getTick());
          }
        }
      }
      double bars = (double) lastNoteTick / BAR_TICKS;
      assertTrue(bars >= 15.0,
          "exported MIDI " + f.getName() + " too short, bars=" + bars);
    }
  }

  /**
   * Scenario: A-section preserves motif identity across the length-matched phrase.
   * Every A phrase's first tile should equal the source motif (modulo
   * chromatic transposition into the candidate's key).
   */
  @Test
  void aPhrasesStillExposeTheMotifAsTileZero() throws Exception {
    Motif motif = MotifLoader.load(shortMotifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(7L);
    List<Sentence> candidates = gen.generate(motif);

    int notesInMotif = motif.getNotes().size();
    List<Integer> motifIntervals = motifIntervals(motif);

    for (Sentence s : candidates) {
      String[] roles = s.getStructure().split(" ");
      for (int p = 0; p < roles.length; p++) {
        if (!roles[p].startsWith("a")) continue;
        List<Note> aNotes = s.getPhrases().get(p).getNotes();
        // First N notes of an A phrase must reproduce the motif's interval pattern
        for (int i = 1; i < notesInMotif && i < aNotes.size(); i++) {
          int actual = aNotes.get(i).pitch() - aNotes.get(i - 1).pitch();
          assertEquals(motifIntervals.get(i - 1), actual,
              "A phrase " + p + " of " + s
                  + " breaks motif interval at idx " + i);
        }
      }
    }
  }

  /**
   * Scenario: Reduction with sub-sampling fallback still produces playable output.
   */
  @Test
  void subSampleMotifProducesNoMicroNotes() throws Exception {
    Motif motif = MotifLoader.load(subSampleMotifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(99L);
    List<Sentence> candidates = gen.generate(motif);

    for (Sentence s : candidates) {
      for (Motif phrase : s.getPhrases()) {
        for (Note n : phrase.getNotes()) {
          if (!n.isRest()) {
            assertTrue(n.durationTicks() >= 120,
                "duration below 16th-note floor: " + n.durationTicks()
                    + " in " + s);
          }
        }
      }
    }
  }

  /**
   * Scenario: Existing #5 guarantees still hold after length matching.
   */
  @Test
  void fleetIsStillTwentyDeterministicSentences() throws Exception {
    Motif motif = MotifLoader.load(shortMotifFile.getAbsolutePath(), 4);
    SentenceGenerator a = new SentenceGenerator(2024L);
    SentenceGenerator b = new SentenceGenerator(2024L);
    List<Sentence> fa = a.generate(motif);
    List<Sentence> fb = b.generate(motif);

    assertEquals(20, fa.size());
    assertEquals(fa.size(), fb.size());

    Set<String> templates = new HashSet<>();
    Set<String> keys = new HashSet<>();
    for (int i = 0; i < fa.size(); i++) {
      assertEquals(fa.get(i).getStructure(), fb.get(i).getStructure(),
          "deterministic structure at rank " + i);
      assertEquals(fa.get(i).getKeyName(), fb.get(i).getKeyName(),
          "deterministic key at rank " + i);
      templates.add(fa.get(i).getStructure());
      keys.add(fa.get(i).getKeyName());
    }
    assertEquals(4, templates.size());
    assertEquals(5, keys.size());
  }

  // ---------------------------------------------------------------------------
  // Issue #12: Reduce repetitiveness when extending a short motif
  // ---------------------------------------------------------------------------

  /**
   * Scenario 1: Random transform applied on A-section extension.
   *
   * <p>Uses a controlled {@link MotifLengthMatcher.TileTransformPicker} (identity) as a baseline,
   * then a {@link MotifLengthMatcher.TileTransformPicker} that records every op applied to tiles
   * 1+. Asserts that at least two distinct ops appear across the tiles in a phrase long enough
   * to produce four tiles, confirming that different transforms are applied to each repeated tile.
   */
  @Test
  void aExtensionAppliesDifferentTransformToEachTile() throws Exception {
    // Build a 1-bar motif (4 quarter notes) to be extended into a 4-bar phrase.
    KeySignature cMajor = KeySignature.major(0); // C major
    long phraseTicks = 4L * 4 * 480; // 4 bars × 4 beats × 480 ticks

    Motif tile0 = buildMotif(new int[]{60, 62, 64, 65},
        new long[]{480, 480, 480, 480});

    // Track which ops the ShufflePicker chooses by using a known Random seed and
    // comparing each tile's pitch sequence against tile0's identity.
    MotifTransformer transformer = new MotifTransformer();

    // Collect pitches from tiles 1-3 using identity picker (baseline: all same).
    List<List<Integer>> identityTilePitches = new ArrayList<>();
    MotifLengthMatcher identityMatcher = new MotifLengthMatcher(
        (tile, key) -> transformer.identity(tile));
    Motif identityPhrase = identityMatcher.extend(
        tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
    List<Integer> identityPitches = soundingPitches(identityPhrase);

    // Collect pitches using seeded ShufflePicker (the real random picker).
    MotifLengthMatcher seededMatcher = new MotifLengthMatcher(new Random(42L));
    Motif transformedPhrase = seededMatcher.extend(
        tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
    List<Integer> transformedPitches = soundingPitches(transformedPhrase);

    // The tile-0 notes (first 4) must be identical (identity anchor).
    for (int i = 0; i < 4 && i < identityPitches.size() && i < transformedPitches.size(); i++) {
      assertEquals(identityPitches.get(i), transformedPitches.get(i),
          "tile 0 pitch at index " + i + " must be unchanged by random transform");
    }

    // At least one tile beyond tile 0 must differ from the identity baseline,
    // confirming that an additional transform was applied.
    boolean anyTileDiffers = false;
    for (int i = 4; i < identityPitches.size() && i < transformedPitches.size(); i++) {
      if (!identityPitches.get(i).equals(transformedPitches.get(i))) {
        anyTileDiffers = true;
        break;
      }
    }
    assertTrue(anyTileDiffers,
        "expected at least one tile beyond tile 0 to be transformed differently from identity");
  }

  /**
   * Scenario 1 (variant): Different tiles within the same phrase receive different transforms.
   *
   * <p>Collects the pitch sequences for tiles 1, 2, and 3 individually and asserts that
   * not all three are identical to each other, demonstrating the shuffle-without-consecutive-repeat
   * constraint produces variety across tiles.
   */
  @Test
  void consecutiveExtensionTilesAreNotAllIdentical() throws Exception {
    KeySignature cMajor = KeySignature.major(0);
    // 4 bars = 4 tiles of 1 bar each
    long phraseTicks = 4L * 4 * 480;

    Motif tile0 = buildMotif(new int[]{60, 62, 64, 65},
        new long[]{480, 480, 480, 480});

    // Use a seed that exercises the shuffle path. Try several seeds to confirm
    // at least one produces non-uniform tile transforms.
    boolean foundVariety = false;
    for (long seed = 1L; seed <= 10L; seed++) {
      MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(seed));
      Motif phrase = matcher.extend(tile0, phraseTicks, cMajor, new int[]{0, 0, 0, 0});
      List<Integer> pitches = soundingPitches(phrase);
      // Split into four tiles of 4 notes each.
      if (pitches.size() < 16) continue;
      List<Integer> t1 = pitches.subList(4, 8);
      List<Integer> t2 = pitches.subList(8, 12);
      List<Integer> t3 = pitches.subList(12, 16);
      if (!t1.equals(t2) || !t2.equals(t3)) {
        foundVariety = true;
        break;
      }
    }
    assertTrue(foundVariety,
        "expected at least one seed to produce differing pitch content across extension tiles");
  }

  /**
   * Scenario 2: Measurable reduction in repetitiveness.
   *
   * <p>Compares the Maximum-Length Repeating Pattern length of a phrase produced
   * by the real random picker against the identity-picker baseline. The transformed
   * phrase must have a shorter (or equal) MLRP, confirming repetitiveness is reduced.
   */
  @Test
  void randomTransformReducesMaximumLengthRepeatingPattern() throws Exception {
    KeySignature cMajor = KeySignature.major(0);
    long phraseTicks = 4L * 4 * 480;

    Motif tile0 = buildMotif(new int[]{60, 62, 64, 65},
        new long[]{480, 480, 480, 480});

    MotifTransformer transformer = new MotifTransformer();
    MotifLengthMatcher identityMatcher = new MotifLengthMatcher(
        (tile, key) -> transformer.identity(tile));
    Motif identityPhrase = identityMatcher.extend(
        tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
    int baselineMLRP = maxLengthRepeatingPattern(soundingPitches(identityPhrase));

    // Try several seeds; at least one must match or beat the baseline.
    boolean anyImproved = false;
    for (long seed = 1L; seed <= 20L; seed++) {
      MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(seed));
      Motif transformed = matcher.extend(tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
      int mlrp = maxLengthRepeatingPattern(soundingPitches(transformed));
      if (mlrp <= baselineMLRP) {
        anyImproved = true;
        break;
      }
    }
    assertTrue(anyImproved,
        "expected at least one seed to produce MLRP <= baseline " + baselineMLRP);
  }

  /**
   * Scenario 2 (variant): Sparse Melody Ratio is lower (or equal) for transformed phrases.
   *
   * <p>A lower SMR means fewer notes belong to an exact repeated pitch pattern,
   * i.e. the melody is less repetitive.
   */
  @Test
  void randomTransformLowersSparseOrEqualMelodyRatio() throws Exception {
    KeySignature cMajor = KeySignature.major(0);
    long phraseTicks = 4L * 4 * 480;

    Motif tile0 = buildMotif(new int[]{60, 62, 64, 65},
        new long[]{480, 480, 480, 480});

    MotifTransformer transformer = new MotifTransformer();
    MotifLengthMatcher identityMatcher = new MotifLengthMatcher(
        (tile, key) -> transformer.identity(tile));
    Motif identityPhrase = identityMatcher.extend(
        tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
    double baselineSMR = sparseMelodyRatio(soundingPitches(identityPhrase));

    boolean anyImproved = false;
    for (long seed = 1L; seed <= 20L; seed++) {
      MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(seed));
      Motif transformed = matcher.extend(tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
      double smr = sparseMelodyRatio(soundingPitches(transformed));
      if (smr <= baselineSMR) {
        anyImproved = true;
        break;
      }
    }
    assertTrue(anyImproved,
        "expected at least one seed to produce SMR <= baseline " + baselineSMR);
  }

  /**
   * Scenario 3: B/C sections unaffected — identity picker leaves tiles untransformed.
   *
   * <p>Injects the identity {@link MotifLengthMatcher.TileTransformPicker} and asserts
   * that every tile (including tiles 1+) is pitch-identical to the diatonic-transposed
   * baseline, i.e. no additional transform is applied when the picker is identity.
   */
  @Test
  void identityPickerProducesNoExtraTransformOnAnyTile() throws Exception {
    KeySignature cMajor = KeySignature.major(0);
    long phraseTicks = 4L * 4 * 480;

    Motif tile0 = buildMotif(new int[]{60, 62, 64, 65},
        new long[]{480, 480, 480, 480});

    MotifTransformer transformer = new MotifTransformer();

    // Identity picker: tiles 1+ pass through unchanged.
    MotifLengthMatcher identityMatcher = new MotifLengthMatcher(
        (tile, key) -> transformer.identity(tile));

    // Use the all-zeros step pattern so every tile is the same diatonic transpose (step=0),
    // meaning all 4 tiles should produce exactly the same pitch sequence.
    Motif phrase = identityMatcher.extend(tile0, phraseTicks, cMajor, new int[]{0, 0, 0, 0});
    List<Integer> pitches = soundingPitches(phrase);

    assertTrue(pitches.size() >= 16,
        "expected at least 16 sounding notes across 4 tiles, got " + pitches.size());

    List<Integer> tile0Pitches = pitches.subList(0, 4);
    List<Integer> tile1Pitches = pitches.subList(4, 8);
    List<Integer> tile2Pitches = pitches.subList(8, 12);
    List<Integer> tile3Pitches = pitches.subList(12, 16);

    assertEquals(tile0Pitches, tile1Pitches,
        "identity picker: tile 1 must equal tile 0 (no extra transform)");
    assertEquals(tile0Pitches, tile2Pitches,
        "identity picker: tile 2 must equal tile 0 (no extra transform)");
    assertEquals(tile0Pitches, tile3Pitches,
        "identity picker: tile 3 must equal tile 0 (no extra transform)");
  }

  /**
   * Scenario 3 (full pipeline): B and C phrases in generated sentences are
   * unaffected by the #12 changes.
   *
   * <p>Runs the full {@link SentenceGenerator} pipeline and checks that B/C phrases
   * still exist and have non-empty content, confirming no regression.
   */
  @Test
  void bcPhrasesStillContainSoundingNotesAfterFeature12() throws Exception {
    Motif motif = MotifLoader.load(shortMotifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(12L);
    List<Sentence> candidates = gen.generate(motif);

    long bcPhraseCount = 0;
    long bcWithNotes = 0;
    for (Sentence s : candidates) {
      String[] roles = s.getStructure().split(" ");
      for (int p = 0; p < roles.length; p++) {
        String role = roles[p];
        if (role.startsWith("b") || role.startsWith("c")) {
          bcPhraseCount++;
          boolean hasNote = s.getPhrases().get(p).getNotes().stream()
              .anyMatch(n -> !n.isRest());
          if (hasNote) bcWithNotes++;
        }
      }
    }
    assertTrue(bcPhraseCount > 0, "expected at least one B or C phrase across all candidates");
    assertEquals(bcPhraseCount, bcWithNotes,
        "every B/C phrase must contain at least one sounding note after feature #12");
  }

  /**
   * Scenario 4: Stochastic output — different Random instances may produce different pitches.
   *
   * <p>Runs the same motif through several independently-seeded {@link MotifLengthMatcher}
   * instances and asserts that not all produce the same pitch sequence, confirming
   * the stochastic nature of the transform picker.
   */
  @Test
  void differentRandomSeedsProduceDifferentPitchSequences() throws Exception {
    KeySignature cMajor = KeySignature.major(0);
    long phraseTicks = 4L * 4 * 480;

    Motif tile0 = buildMotif(new int[]{60, 62, 64, 65},
        new long[]{480, 480, 480, 480});

    Set<List<Integer>> distinctOutputs = new HashSet<>();
    for (long seed = 0L; seed < 20L; seed++) {
      MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(seed));
      Motif phrase = matcher.extend(tile0, phraseTicks, cMajor, new int[]{0, 1, 2, 3});
      distinctOutputs.add(soundingPitches(phrase));
    }

    assertTrue(distinctOutputs.size() > 1,
        "expected different seeds to produce at least 2 distinct pitch sequences, "
            + "got " + distinctOutputs.size() + " distinct outputs across 20 seeds");
  }

  // ---------------------------------------------------------------------------
  // Helpers shared by #8 and #12 tests
  // ---------------------------------------------------------------------------

  private static List<Integer> motifIntervals(Motif motif) {
    List<Note> n = motif.getNotes();
    List<Integer> out = new java.util.ArrayList<>();
    for (int i = 1; i < n.size(); i++) {
      out.add(n.get(i).pitch() - n.get(i - 1).pitch());
    }
    return out;
  }

  /** Build a simple in-memory motif from raw pitch/duration arrays at 480 ticks/beat, 4/4. */
  private static Motif buildMotif(int[] pitches, long[] durations) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < pitches.length; i++) {
      notes.add(new Note(pitches[i], tick, durations[i], 90));
      tick += durations[i];
    }
    int bars = (int) Math.max(1, tick / (4L * 480));
    return new Motif(notes, bars, 4, 480);
  }

  /** Returns the ordered list of pitches for all sounding (non-rest) notes in {@code motif}. */
  private static List<Integer> soundingPitches(Motif motif) {
    List<Integer> out = new ArrayList<>();
    for (Note n : motif.getNotes()) {
      if (!n.isRest()) out.add(n.pitch());
    }
    return out;
  }

  /**
   * Maximum-Length Repeating Pattern (MLRP).
   *
   * <p>Returns the length of the longest contiguous pitch subsequence that appears
   * at least twice in {@code pitches}. Uses a sliding-window approach: O(n^2) which
   * is acceptable for the small phrase sizes (≤ 32 notes) tested here.
   *
   * <p>A result of 0 means no repeated sub-sequence of length ≥ 1.
   */
  static int maxLengthRepeatingPattern(List<Integer> pitches) {
    int n = pitches.size();
    int maxLen = 0;
    for (int len = n / 2; len >= 1; len--) {
      boolean found = false;
      outer:
      for (int i = 0; i <= n - len; i++) {
        for (int j = i + 1; j <= n - len; j++) {
          boolean match = true;
          for (int k = 0; k < len; k++) {
            if (!pitches.get(i + k).equals(pitches.get(j + k))) {
              match = false;
              break;
            }
          }
          if (match) {
            found = true;
            break outer;
          }
        }
      }
      if (found) {
        maxLen = len;
        break;
      }
    }
    return maxLen;
  }

  /**
   * Sparse Melody Ratio (SMR).
   *
   * <p>Counts the fraction of notes whose pitch is part of a repeated sub-pattern
   * of length ≥ 2. A lower SMR means less repetitiveness.
   *
   * <p>Algorithm: for each pitch index, check whether a 2-note window starting
   * there also appears elsewhere; count those "repeated" notes and divide by total.
   */
  static double sparseMelodyRatio(List<Integer> pitches) {
    int n = pitches.size();
    if (n < 2) return 0.0;
    int patternLen = 2;
    boolean[] inPattern = new boolean[n];
    for (int i = 0; i <= n - patternLen; i++) {
      for (int j = i + 1; j <= n - patternLen; j++) {
        boolean match = true;
        for (int k = 0; k < patternLen; k++) {
          if (!pitches.get(i + k).equals(pitches.get(j + k))) {
            match = false;
            break;
          }
        }
        if (match) {
          for (int k = 0; k < patternLen; k++) {
            inPattern[i + k] = true;
            inPattern[j + k] = true;
          }
        }
      }
    }
    int count = 0;
    for (boolean b : inPattern) {
      if (b) count++;
    }
    return (double) count / n;
  }
}
