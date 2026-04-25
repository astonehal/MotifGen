package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * End-to-end coverage for issue #5 (motif-faithful seeding). Each test maps
 * directly to a Gherkin scenario from the confirmed requirements. Fixtures use
 * a deliberately rhythmically-varied motif so behaviour around rhythm
 * preservation and rhythm variety is observable.
 */
class MotifFaithfulSeedingE2ETest {

  private static final int TICKS_PER_BEAT = 480;

  @TempDir
  static Path tempDir;

  private static File motifFile;

  @BeforeAll
  static void createFixtureMidi() throws Exception {
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

    // C major motif with deliberately varied rhythm: quarter, eighth, eighth,
    // quarter, half, quarter, eighth, eighth, quarter, half, quarter, quarter,
    // half, quarter, eighth, eighth.
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
                     60, 62, 64, 67, 65, 64, 62, 60};
    long[] durations = {
        TICKS_PER_BEAT, TICKS_PER_BEAT / 2, TICKS_PER_BEAT / 2, TICKS_PER_BEAT,
        TICKS_PER_BEAT * 2L, TICKS_PER_BEAT, TICKS_PER_BEAT / 2, TICKS_PER_BEAT / 2,
        TICKS_PER_BEAT, TICKS_PER_BEAT * 2L, TICKS_PER_BEAT, TICKS_PER_BEAT,
        TICKS_PER_BEAT * 2L, TICKS_PER_BEAT, TICKS_PER_BEAT / 2, TICKS_PER_BEAT / 2
    };

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

    motifFile = tempDir.resolve("motif_faithful_fixture.mid").toFile();
    MidiSystem.write(seq, 1, motifFile);
  }

  /**
   * Scenario: Motif is audibly present as exact repetitions in every A phrase.
   * Each A phrase must equal the motif's interval pattern exactly. Pitches
   * may differ by a constant offset because the motif is chromatically
   * transposed into the candidate's key.
   */
  @Test
  void everyAPhraseIsTheMotifTransposedIntoTheCandidateKey() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(2026L);
    List<Sentence> candidates = gen.generate(motif);

    List<Integer> motifPitches = motif.getNotes().stream().map(Note::pitch).toList();

    for (Sentence s : candidates) {
      String[] roles = s.getStructure().split(" ");
      List<com.motifgen.model.Motif> phrases = s.getPhrases();
      for (int p = 0; p < roles.length; p++) {
        if (!roles[p].startsWith("a")) continue;
        List<Integer> aPitches = phrases.get(p).getNotes().stream()
            .map(Note::pitch).toList();
        for (int i = 1; i < motifPitches.size(); i++) {
          int motifInterval = motifPitches.get(i) - motifPitches.get(i - 1);
          int aInterval = aPitches.get(i) - aPitches.get(i - 1);
          assertEquals(motifInterval, aInterval,
              "A phrase " + p + " of " + s + " breaks motif interval at idx " + i);
        }
      }
    }
  }

  /**
   * Scenario: Motif rhythm is preserved in every A phrase.
   */
  @Test
  void everyAPhraseHasIdenticalDurationSequenceAsMotif() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(31L);
    List<Sentence> candidates = gen.generate(motif);

    List<Long> motifDurations = motif.getNotes().stream()
        .map(Note::durationTicks).toList();

    for (Sentence s : candidates) {
      String[] roles = s.getStructure().split(" ");
      for (int p = 0; p < roles.length; p++) {
        if (!roles[p].startsWith("a")) continue;
        List<Long> aDurations = s.getPhrases().get(p).getNotes().stream()
            .map(Note::durationTicks).toList();
        assertEquals(motifDurations, aDurations,
            "A phrase " + p + " of " + s + " has a different duration sequence");
      }
    }
  }

  /**
   * Scenario: Final phrase resolves onto tonic.
   */
  @Test
  void everyCandidateEndsOnTonic() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(7L);
    List<Sentence> candidates = gen.generate(motif);

    for (Sentence s : candidates) {
      List<Note> all = s.getAllNotes();
      Note last = null;
      for (int i = all.size() - 1; i >= 0; i--) {
        if (!all.get(i).isRest()) {
          last = all.get(i);
          break;
        }
      }
      assertNotNull(last, "candidate has no sounding notes: " + s);

      int expectedTonicPc = expectedTonicPc(s.getKeyName());
      assertEquals(expectedTonicPc, ((last.pitch() % 12) + 12) % 12,
          "candidate " + s + " does not end on tonic");
    }
  }

  /**
   * Scenario: Rhythmic variety contributes to the final score - the fleet
   * should now contain at least some candidates with non-uniform rhythm.
   */
  @Test
  void fleetContainsCandidatesWithMultipleDistinctDurations() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(99L);
    List<Sentence> candidates = gen.generate(motif);

    long varied = candidates.stream().filter(MotifFaithfulSeedingE2ETest::hasMultipleDurations)
        .count();
    assertTrue(varied >= candidates.size() / 2,
        "expected at least half of candidates to have multiple distinct durations, got "
            + varied + " of " + candidates.size());
  }

  /**
   * Scenario: Existing #3 structural guarantees still hold under the new
   * pipeline.
   */
  @Test
  void fleetCoversAllFourTemplatesAcrossFiveRelatedKeys() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(5L);
    List<Sentence> candidates = gen.generate(motif);

    assertEquals(20, candidates.size());

    Set<String> structures = new HashSet<>();
    Set<String> keys = new HashSet<>();
    for (Sentence s : candidates) {
      structures.add(s.getStructure());
      keys.add(s.getKeyName());
      assertEquals(4, s.getPhrases().size());
      assertEquals(16, s.totalBars());
    }
    assertEquals(4, structures.size());
    assertEquals(5, keys.size());
  }

  @Test
  void candidatesAreReturnedSortedBestFirst() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(13L);
    List<Sentence> candidates = gen.generate(motif);

    for (int i = 1; i < candidates.size(); i++) {
      assertTrue(candidates.get(i - 1).getScore() >= candidates.get(i).getScore(),
          "candidates should be best-first at " + i);
    }
  }

  @Test
  void sameSeedProducesSameFleet() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator a = new SentenceGenerator(2024L);
    SentenceGenerator b = new SentenceGenerator(2024L);

    List<Sentence> fa = a.generate(motif);
    List<Sentence> fb = b.generate(motif);

    assertEquals(fa.size(), fb.size());
    for (int i = 0; i < fa.size(); i++) {
      assertEquals(fa.get(i).getScore(), fb.get(i).getScore(), 1e-6);
      assertEquals(fa.get(i).getStructure(), fb.get(i).getStructure());
      assertEquals(fa.get(i).getKeyName(), fb.get(i).getKeyName());
    }
  }

  @Test
  void reportedScoresMatchFreshRecompute() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator gen = new SentenceGenerator(77L);
    SentenceScorer scorer = new SentenceScorer();

    List<Sentence> candidates = gen.generate(motif);
    for (Sentence s : candidates) {
      double recomputed = scorer.score(s).getScore();
      assertEquals(recomputed, s.getScore(), 1e-6,
          "reported score must match independent recomputation for " + s);
    }
  }

  @Test
  void cliExportsMidiFromMotifFaithfulOutput() throws Exception {
    String outputDir = tempDir.resolve("motif_faithful_output").toString();
    new File(outputDir).mkdirs();

    MotifGen.run(motifFile.getAbsolutePath(), outputDir, 120);

    File[] outputs = new File(outputDir).listFiles((d, name) -> name.endsWith(".mid"));
    assertNotNull(outputs);
    assertTrue(outputs.length >= 1,
        "motif-faithful pipeline should still feed the MIDI exporter");
  }

  // --- helpers ---

  private static boolean hasMultipleDurations(Sentence s) {
    Set<Long> seen = new HashSet<>();
    for (com.motifgen.model.Motif phrase : s.getPhrases()) {
      for (Note n : phrase.getNotes()) {
        if (!n.isRest()) seen.add(n.durationTicks());
        if (seen.size() >= 2) return true;
      }
    }
    return false;
  }

  private static int expectedTonicPc(String keyName) {
    Map<String, Integer> map = new HashMap<>();
    String[] sharps = {"C", "C#", "D", "D#", "E", "F", "F#",
        "G", "G#", "A", "A#", "B"};
    for (int i = 0; i < sharps.length; i++) {
      map.put(sharps[i] + " major", i);
      map.put(sharps[i] + " minor", i);
    }
    String[] flats = {"C", "Db", "D", "Eb", "E", "F", "Gb",
        "G", "Ab", "A", "Bb", "B"};
    for (int i = 0; i < flats.length; i++) {
      map.put(flats[i] + " major", i);
      map.put(flats[i] + " minor", i);
    }
    Integer pc = map.get(keyName);
    assertNotNull(pc, "unknown key name: " + keyName);
    return pc;
  }
}
