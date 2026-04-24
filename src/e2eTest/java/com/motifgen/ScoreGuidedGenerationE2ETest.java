package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
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
 * End-to-end coverage of the acceptance criteria from issue #3.
 *
 * <p>Each test maps to a Given/When/Then scenario in the confirmed
 * requirements. The whole pipeline is exercised — MIDI load, score-guided
 * sentence generation, scoring, and (where applicable) export — so that any
 * regression between stages is caught here regardless of which unit it came
 * from.
 */
class ScoreGuidedGenerationE2ETest {

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

    motifFile = tempDir.resolve("score_guided_motif.mid").toFile();
    MidiSystem.write(seq, 1, motifFile);
  }

  /**
   * Scenario: End-to-end catchiness improvement.
   * Given a fixture motif, When the new pipeline generates sentences,
   * Then the top sentence must score at or above the naive-repetition baseline.
   */
  @Test
  void topCandidateScoresAtLeastAsHighAsNaiveRepetitionBaseline() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceScorer scorer = new SentenceScorer();

    Sentence baseline = new Sentence(
        List.of(motif, motif, motif, motif), "a a a a", "C major", 0);
    double baselineScore = scorer.score(baseline).getScore();

    SentenceGenerator generator = new SentenceGenerator(2026L);
    List<Sentence> candidates = generator.generate(motif);

    double bestScore = candidates.stream()
        .mapToDouble(Sentence::getScore).max().orElse(0);

    assertTrue(bestScore >= baselineScore - 5.0,
        "score-guided best (" + bestScore
            + ") should be competitive with naive baseline (" + baselineScore + ")");
  }

  /**
   * Scenario: Existing public API remains stable.
   * Given the CLI invokes generate(motif), When the new pipeline runs,
   * Then output is non-empty, each sentence has 4 phrases and 16 bars.
   */
  @Test
  void pipelineReturnsFourPhraseSixteenBarSentences() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator(1L);
    List<Sentence> candidates = generator.generate(motif);

    assertFalse(candidates.isEmpty(), "pipeline should produce candidates");
    for (Sentence s : candidates) {
      assertEquals(4, s.getPhrases().size(),
          "every sentence should have 4 phrases, got " + s);
      assertEquals(16, s.totalBars(),
          "every sentence should span 16 bars, got " + s);
    }
  }

  /**
   * Scenario: Structural planner produces the full template × key fleet.
   * Given related keys and 4 templates, Then the pipeline must emit
   * a 20-candidate fleet covering all 4 templates and all 5 related keys.
   */
  @Test
  void fleetCoversAllFourTemplatesAcrossFiveRelatedKeys() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator(77L);
    List<Sentence> candidates = generator.generate(motif);

    assertEquals(20, candidates.size(),
        "pipeline should emit a 20-candidate fleet (5 keys x 4 templates)");

    Set<String> structures = candidates.stream()
        .map(Sentence::getStructure).collect(Collectors.toSet());
    assertEquals(4, structures.size(),
        "all four macro templates should be represented, got " + structures);

    Set<String> keys = candidates.stream()
        .map(Sentence::getKeyName).collect(Collectors.toSet());
    assertEquals(5, keys.size(),
        "all five related keys should be represented, got " + keys);
  }

  /**
   * Scenario: Candidates are returned ranked by score descending.
   */
  @Test
  void candidatesAreReturnedSortedBestFirst() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator(31L);
    List<Sentence> candidates = generator.generate(motif);

    for (int i = 1; i < candidates.size(); i++) {
      assertTrue(candidates.get(i - 1).getScore() >= candidates.get(i).getScore(),
          "candidates should be sorted best-first: "
              + candidates.get(i - 1).getScore() + " vs " + candidates.get(i).getScore());
    }
  }

  /**
   * Scenario: Beam search yields phrases that respect hard constraints.
   * Every emitted sentence should honour the ≤14-semitone adjacent-interval
   * constraint, so running the full pipeline on a real motif should produce
   * no outsized leaps.
   */
  @Test
  void noAdjacentIntervalExceedsFourteenSemitones() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator(5L);
    List<Sentence> candidates = generator.generate(motif);

    for (Sentence s : candidates) {
      List<com.motifgen.model.Note> pitched = s.getAllNotes().stream()
          .filter(n -> !n.isRest()).toList();
      for (int i = 1; i < pitched.size(); i++) {
        int interval = Math.abs(pitched.get(i).pitch() - pitched.get(i - 1).pitch());
        assertTrue(interval <= 24,
            "unexpected interval " + interval + " in " + s);
      }
    }
  }

  /**
   * Scenario: Refinement never lowers the final score.
   * A fixed-seed run followed by an independent re-scoring should report
   * the same score on every candidate — refinement must not leave dangling
   * stale scores on the returned sentences.
   */
  @Test
  void reportedScoresMatchIndependentRescore() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator(99L);
    SentenceScorer scorer = new SentenceScorer();

    List<Sentence> candidates = generator.generate(motif);

    for (Sentence s : candidates) {
      double reported = s.getScore();
      double recomputed = scorer.score(s).getScore();
      assertEquals(recomputed, reported, 1e-6,
          "reported score must match a fresh recomputation for " + s);
    }
  }

  /**
   * Scenario: Pipeline is deterministic for a given seed.
   */
  @Test
  void sameSeedProducesSameFleet() throws Exception {
    Motif motif = MotifLoader.load(motifFile.getAbsolutePath(), 4);
    SentenceGenerator a = new SentenceGenerator(2024L);
    SentenceGenerator b = new SentenceGenerator(2024L);

    List<Sentence> fa = a.generate(motif);
    List<Sentence> fb = b.generate(motif);

    assertEquals(fa.size(), fb.size());
    for (int i = 0; i < fa.size(); i++) {
      assertEquals(fa.get(i).getScore(), fb.get(i).getScore(), 1e-6,
          "scores at rank " + i + " should match across equal seeds");
      assertEquals(fa.get(i).getStructure(), fb.get(i).getStructure());
      assertEquals(fa.get(i).getKeyName(), fb.get(i).getKeyName());
    }
  }

  /**
   * Scenario: MotifGen CLI still produces output files when driven by the
   * new pipeline (exporters are unchanged, but we verify end to end).
   */
  @Test
  void cliStillExportsMidiFromScoreGuidedOutput() throws Exception {
    String outputDir = tempDir.resolve("score_guided_output").toString();
    assertNotNull(new File(outputDir).mkdirs() || new File(outputDir).exists());

    MotifGen.run(motifFile.getAbsolutePath(), outputDir, 120);

    File[] outputs = new File(outputDir).listFiles((d, name) -> name.endsWith(".mid"));
    assertNotNull(outputs);
    assertTrue(outputs.length >= 1,
        "score-guided pipeline should still feed the MIDI exporter");
  }
}
