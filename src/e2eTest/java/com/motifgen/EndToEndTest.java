package com.motifgen;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests that run the full MotifGen pipeline:
 * load MIDI -> generate sentences -> score with catchiness algorithm -> export.
 */
class EndToEndTest {

  @TempDir
  static Path tempDir;

  private static File testMidiFile;

  @BeforeAll
  static void createTestMidi() throws Exception {
    int ticksPerBeat = 480;
    Sequence seq = new Sequence(Sequence.PPQ, ticksPerBeat);
    Track track = seq.createTrack();

    // Time signature: 4/4
    MetaMessage timeSig = new MetaMessage();
    timeSig.setMessage(0x58, new byte[]{4, 2, 24, 8}, 4);
    track.add(new MidiEvent(timeSig, 0));

    // Tempo: 120 BPM
    MetaMessage tempo = new MetaMessage();
    int mpq = 500000;
    tempo.setMessage(0x51, new byte[]{
        (byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq}, 3);
    track.add(new MidiEvent(tempo, 0));

    // 4-bar C major melody: stepwise + small leaps (catchy pattern)
    int[] pitches = {
        60, 62, 64, 65,  // Bar 1: C D E F
        67, 65, 64, 62,  // Bar 2: G F E D
        60, 62, 64, 67,  // Bar 3: C D E G
        65, 64, 62, 60   // Bar 4: F E D C
    };

    int velocity = 90;
    long tick = 0;
    for (int pitch : pitches) {
      ShortMessage noteOn = new ShortMessage();
      noteOn.setMessage(ShortMessage.NOTE_ON, 0, pitch, velocity);
      track.add(new MidiEvent(noteOn, tick));

      ShortMessage noteOff = new ShortMessage();
      noteOff.setMessage(ShortMessage.NOTE_OFF, 0, pitch, 0);
      track.add(new MidiEvent(noteOff, tick + ticksPerBeat));

      tick += ticksPerBeat;
    }

    testMidiFile = tempDir.resolve("test_motif.mid").toFile();
    MidiSystem.write(seq, 1, testMidiFile);
  }

  @Test
  void fullPipelineProducesOutputFiles() throws Exception {
    String outputDir = tempDir.resolve("output").toString();
    new File(outputDir).mkdirs();

    MotifGen.run(testMidiFile.getAbsolutePath(), outputDir, 120);

    // Verify output MIDI files were created
    File[] outputs = new File(outputDir).listFiles((dir, name) -> name.endsWith(".mid"));
    assertNotNull(outputs);
    assertTrue(outputs.length >= 1, "Should produce at least one output MIDI file");
  }

  @Test
  void fullPipelineScoresAreOnZeroToHundredScale() throws Exception {
    Motif motif = MotifLoader.load(testMidiFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator();
    List<Sentence> candidates = generator.generate(motif);

    SentenceScorer scorer = new SentenceScorer();
    List<Sentence> ranked = scorer.scoreAndRank(candidates);

    assertFalse(ranked.isEmpty(), "Should generate candidates");

    for (Sentence s : ranked) {
      assertTrue(s.getScore() >= 0, "Score should be >= 0, got: " + s.getScore());
      assertTrue(s.getScore() <= 100, "Score should be <= 100, got: " + s.getScore());
    }
  }

  @Test
  void fullPipelineBreakdownHasNewFactors() throws Exception {
    Motif motif = MotifLoader.load(testMidiFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator();
    List<Sentence> candidates = generator.generate(motif);

    SentenceScorer scorer = new SentenceScorer();
    List<Sentence> ranked = scorer.scoreAndRank(candidates);
    Sentence best = ranked.getFirst();

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(best);

    // All six catchiness factors should be present and in range
    assertTrue(bd.repetition() >= 0 && bd.repetition() <= 1,
        "Repetition out of range: " + bd.repetition());
    assertTrue(bd.contourPredictability() >= 0 && bd.contourPredictability() <= 1,
        "Contour predictability out of range: " + bd.contourPredictability());
    assertTrue(bd.pitchRangeCompactness() >= 0 && bd.pitchRangeCompactness() <= 1,
        "Pitch range compactness out of range: " + bd.pitchRangeCompactness());
    assertTrue(bd.rhythmicSimplicity() >= 0 && bd.rhythmicSimplicity() <= 1,
        "Rhythmic simplicity out of range: " + bd.rhythmicSimplicity());
    assertTrue(bd.internalConventionality() >= 0 && bd.internalConventionality() <= 1,
        "Internal conventionality out of range: " + bd.internalConventionality());
    assertTrue(bd.hookProminence() >= 0 && bd.hookProminence() <= 1,
        "Hook prominence out of range: " + bd.hookProminence());

    // Total should be weighted sum * 100
    double expectedTotal = (bd.repetition() * 0.25
        + bd.contourPredictability() * 0.15
        + bd.pitchRangeCompactness() * 0.15
        + bd.rhythmicSimplicity() * 0.15
        + bd.internalConventionality() * 0.15
        + bd.hookProminence() * 0.15) * 100;
    assertEquals(expectedTotal, bd.total(), 0.01);
  }

  @Test
  void fullPipelineBandLabelsMatchScores() throws Exception {
    Motif motif = MotifLoader.load(testMidiFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator();
    List<Sentence> candidates = generator.generate(motif);

    SentenceScorer scorer = new SentenceScorer();
    List<Sentence> ranked = scorer.scoreAndRank(candidates);

    for (Sentence s : ranked) {
      String label = SentenceScorer.bandLabel(s.getScore());
      assertNotNull(label, "Band label should not be null");
      assertFalse(label.isEmpty(), "Band label should not be empty");

      double score = s.getScore();
      if (score >= 90) {
        assertEquals("Rare - suspiciously sticky", label);
      } else if (score >= 75) {
        assertEquals("Hook driven. Earworm.", label);
      } else if (score >= 55) {
        assertEquals("Solid pop/folk territory - memorable on repeated listens", label);
      } else if (score >= 30) {
        assertEquals("Coherent but unremarkable", label);
      } else {
        assertEquals("Atonal, meandering - Hard to remember", label);
      }
    }
  }

  @Test
  void fullPipelineRankedBestFirst() throws Exception {
    Motif motif = MotifLoader.load(testMidiFile.getAbsolutePath(), 4);
    SentenceGenerator generator = new SentenceGenerator();
    List<Sentence> candidates = generator.generate(motif);

    SentenceScorer scorer = new SentenceScorer();
    List<Sentence> ranked = scorer.scoreAndRank(candidates);

    for (int i = 1; i < ranked.size(); i++) {
      assertTrue(ranked.get(i - 1).getScore() >= ranked.get(i).getScore(),
          "Sentences should be ranked best-first");
    }
  }

  @Test
  void fullPipelineMusicXmlExport() throws Exception {
    String outputDir = tempDir.resolve("xml_output").toString();
    new File(outputDir).mkdirs();

    MotifGen.run(testMidiFile.getAbsolutePath(), outputDir, 120, MotifGen.OutputFormat.BOTH);

    File[] midiOutputs = new File(outputDir).listFiles((dir, name) -> name.endsWith(".mid"));
    File[] xmlOutputs = new File(outputDir).listFiles((dir, name) -> name.endsWith(".musicxml"));

    assertNotNull(midiOutputs);
    assertNotNull(xmlOutputs);
    assertTrue(midiOutputs.length >= 1, "Should produce MIDI output");
    assertTrue(xmlOutputs.length >= 1, "Should produce MusicXML output");
  }
}
