package com.motifgen.scoring;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates 5 different motifs, scores their best sentences,
 * and exports MIDI files for listening.
 */
public class CatchinessDemo {

  private static final int TPB = 480; // ticks per beat
  private static final int BPB = 4;   // beats per bar
  private static final int TEMPO = 120;

  public static void main(String[] args) throws Exception {
    SentenceScorer scorer = new SentenceScorer();
    SentenceGenerator generator = new SentenceGenerator();

    String outputBase = "output/demo";
    new File(outputBase).mkdirs();

    System.out.println("=== Catchiness Scoring Validation ===");
    System.out.println("Output directory: " + new File(outputBase).getAbsolutePath() + "\n");

    // Motif 1: "Twinkle Twinkle" style - very catchy, simple, repetitive
    runMotif(scorer, generator, outputBase, "1_twinkle",
        "Motif 1: Twinkle Twinkle style (simple, repetitive, compact)",
        new int[]{60, 60, 67, 67, 69, 69, 67, -1, 65, 65, 64, 64, 62, 62, 60, -1},
        allQuarters(16));

    // Motif 2: "Ode to Joy" style - stepwise, singable, classic
    runMotif(scorer, generator, outputBase, "2_ode_to_joy",
        "Motif 2: Ode to Joy style (stepwise, melodic, balanced)",
        new int[]{64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62, -1},
        allQuarters(16));

    // Motif 3: Atonal / chromatic - should score low
    runMotif(scorer, generator, outputBase, "3_chromatic",
        "Motif 3: Chromatic / atonal (disjointed, unpredictable)",
        new int[]{60, 73, 49, 78, 52, 85, 44, 71, 61, 74, 50, 79, 53, 86, 45, 72},
        allQuarters(16));

    // Motif 4: Pop hook - repetitive short pattern with rhythm variation
    long q = TPB;
    long e = TPB / 2;
    long h = TPB * 2;
    runMotif(scorer, generator, outputBase, "4_pop_hook",
        "Motif 4: Pop hook (short repeated pattern, rhythmic drive)",
        new int[]{60, 64, 67, 65, 60, 64, 67, 65, 60, 64, 67, 72, 71, 67, 65, 60},
        new long[]{e, e, e, e, e, e, e, e, e, e, q, e, e, q, q, h});

    // Motif 5: Wide-ranging virtuoso - large leaps, complex rhythm
    runMotif(scorer, generator, outputBase, "5_virtuoso",
        "Motif 5: Virtuoso (wide leaps, complex rhythm, low catchiness expected)",
        new int[]{48, 72, 55, 84, 60, 36, 79, 43, 67, 48, 75, 52, 88, 41, 65, 50},
        new long[]{e, q, e, e, h, e, q, e, e, q, e, h, e, q, e, q});

    System.out.println("\n=== End Validation ===");
    System.out.println("Listen to the MIDI files in: " + new File(outputBase).getAbsolutePath());
  }

  private static void runMotif(SentenceScorer scorer, SentenceGenerator generator,
      String outputBase, String filePrefix, String description,
      int[] pitches, long[] durations) throws Exception {
    System.out.println("--- " + description + " ---");

    // Build motif
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < pitches.length; i++) {
      long dur = i < durations.length ? durations[i] : TPB;
      if (pitches[i] == -1) {
        notes.add(new Note(Note.REST, tick, dur, 0));
      } else {
        notes.add(new Note(pitches[i], tick, dur, 90));
      }
      tick += dur;
    }
    Motif motif = new Motif(notes, 4, BPB, TPB);

    // Export the input motif as MIDI too
    Sentence inputSentence = new Sentence(
        List.of(motif, motif, motif, motif), "input", "C major", 0);
    File inputFile = new File(outputBase, filePrefix + "_INPUT.mid");
    MidiExporter.export(inputSentence, inputFile, TEMPO);
    System.out.println("  Input motif exported: " + inputFile.getName());

    // Generate and score
    List<Sentence> candidates = generator.generate(motif);
    List<Sentence> ranked = scorer.scoreAndRank(candidates);

    if (ranked.isEmpty()) {
      System.out.println("  No candidates generated.\n");
      return;
    }

    // Export top sentence
    Sentence best = ranked.getFirst();
    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(best);

    File bestFile = new File(outputBase, filePrefix + "_BEST.mid");
    MidiExporter.export(best, bestFile, TEMPO);

    System.out.printf("  Best candidate exported: %s%n", bestFile.getName());
    System.out.printf("  Key: %s | Structure: %s%n", best.getKeyName(), best.getStructure());
    System.out.printf("    Repetition:              %.3f  (weight: 0.25)%n", bd.repetition());
    System.out.printf("    Contour Predictability:  %.3f  (weight: 0.15)%n", bd.contourPredictability());
    System.out.printf("    Pitch Range Compactness: %.3f  (weight: 0.15)%n", bd.pitchRangeCompactness());
    System.out.printf("    Rhythmic Simplicity:     %.3f  (weight: 0.15)%n", bd.rhythmicSimplicity());
    System.out.printf("    Internal Conventionality:%.3f  (weight: 0.15)%n", bd.internalConventionality());
    System.out.printf("    Hook Prominence:         %.3f  (weight: 0.15)%n", bd.hookProminence());
    System.out.printf("    TOTAL: %.1f / 100  [%s]%n%n", bd.total(), SentenceScorer.bandLabel(bd.total()));
  }

  private static long[] allQuarters(int count) {
    long[] d = new long[count];
    for (int i = 0; i < count; i++) d[i] = TPB;
    return d;
  }
}
