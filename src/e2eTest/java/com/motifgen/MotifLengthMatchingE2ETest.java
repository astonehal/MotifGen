package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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

  private static List<Integer> motifIntervals(Motif motif) {
    List<Note> n = motif.getNotes();
    List<Integer> out = new java.util.ArrayList<>();
    for (int i = 1; i < n.size(); i++) {
      out.add(n.get(i).pitch() - n.get(i - 1).pitch());
    }
    return out;
  }
}
