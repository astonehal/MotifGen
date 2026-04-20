package com.motifgen.scoring;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SentenceScorerTest {

  private SentenceScorer scorer;
  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR = 4;

  @BeforeEach
  void setUp() {
    scorer = new SentenceScorer();
  }

  // --- Helper methods ---

  private Motif makeMotif(int[] pitches, long[] durations) {
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int i = 0; i < pitches.length; i++) {
      long dur = i < durations.length ? durations[i] : TICKS_PER_BEAT;
      notes.add(new Note(pitches[i], tick, dur, 90));
      tick += dur;
    }
    return new Motif(notes, 4, BEATS_PER_BAR, TICKS_PER_BEAT);
  }

  private Motif makeMotif(int[] pitches) {
    long[] durations = new long[pitches.length];
    for (int i = 0; i < pitches.length; i++) {
      durations[i] = TICKS_PER_BEAT;
    }
    return makeMotif(pitches, durations);
  }

  private Sentence makeSentence(Motif a, Motif b, Motif c, Motif d) {
    return new Sentence(List.of(a, b, c, d), "a b c d", "C major", 0);
  }

  // --- Score scale tests ---

  @Test
  void scoreIsOnZeroToHundredScale() {
    int[] pitches = {60, 62, 64, 65, 67, 69, 67, 65,
        64, 62, 60, 64, 67, 65, 64, 60};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    Sentence scored = scorer.score(sentence);

    assertTrue(scored.getScore() >= 0, "Score should be >= 0");
    assertTrue(scored.getScore() <= 100, "Score should be <= 100");
  }

  @Test
  void breakdownHasSixFactorsAndTotal() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.repetition() >= 0 && bd.repetition() <= 1);
    assertTrue(bd.contourPredictability() >= 0 && bd.contourPredictability() <= 1);
    assertTrue(bd.pitchRangeCompactness() >= 0 && bd.pitchRangeCompactness() <= 1);
    assertTrue(bd.rhythmicSimplicity() >= 0 && bd.rhythmicSimplicity() <= 1);
    assertTrue(bd.internalConventionality() >= 0 && bd.internalConventionality() <= 1);
    assertTrue(bd.hookProminence() >= 0 && bd.hookProminence() <= 1);
    assertTrue(bd.total() >= 0 && bd.total() <= 100);
  }

  // --- Repetition tests ---

  @Test
  void identicalPhrasesScoreHighOnRepetition() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.repetition() >= 0.8,
        "Identical phrases should score high on repetition, got: " + bd.repetition());
  }

  @Test
  void completelyDifferentPhrasesScoreLowOnRepetition() {
    Motif a = makeMotif(new int[]{60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60});
    Motif b = makeMotif(new int[]{72, 70, 68, 66, 74, 76, 78, 80,
        73, 71, 69, 67, 75, 77, 79, 81});
    Motif c = makeMotif(new int[]{48, 50, 52, 53, 55, 53, 52, 50,
        48, 50, 52, 53, 55, 53, 52, 50});
    Motif d = makeMotif(new int[]{84, 82, 80, 78, 86, 88, 90, 92,
        85, 83, 81, 79, 87, 89, 91, 93});

    Sentence sentence = makeSentence(a, b, c, d);
    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.repetition() < 0.5,
        "Very different phrases should score low on repetition, got: " + bd.repetition());
  }

  // --- Pitch range compactness tests ---

  @Test
  void compactRangeScoresHigh() {
    // All within one octave (C4 to B4 = 11 semitones)
    int[] pitches = {60, 62, 64, 65, 67, 69, 71, 67,
        65, 64, 62, 60, 64, 67, 65, 62};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.pitchRangeCompactness() > 0.8,
        "Octave range should score > 0.8, got: " + bd.pitchRangeCompactness());
  }

  @Test
  void wideRangeScoresLow() {
    // Spanning 3+ octaves
    int[] pitches = {36, 72, 84, 48, 60, 96, 36, 84,
        48, 72, 96, 36, 60, 84, 48, 72};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.pitchRangeCompactness() < 0.3,
        "3+ octave range should score low, got: " + bd.pitchRangeCompactness());
  }

  // --- Rhythmic simplicity tests ---

  @Test
  void uniformDurationsScoreHighOnSimplicity() {
    // All quarter notes
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.rhythmicSimplicity() >= 0.8,
        "Uniform durations should score high on simplicity, got: " + bd.rhythmicSimplicity());
  }

  @Test
  void mixedDurationsScoreLowerOnSimplicity() {
    long q = TICKS_PER_BEAT;
    long e = q / 2;
    long s = q / 4;
    long h = q * 2;
    long[] durations = {q, e, s, h, e, q, s, e, h, q, s, e, q, h, s, e};
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60};
    Motif m = makeMotif(pitches, durations);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.rhythmicSimplicity() < 0.7,
        "Mixed durations should score lower on simplicity, got: " + bd.rhythmicSimplicity());
  }

  // --- Contour predictability tests ---

  @Test
  void archShapeWithGapFillScoresHigh() {
    // Rising first half, falling second half, with leaps followed by steps back
    int[] pitches = {60, 62, 64, 65, 67, 69, 71, 72,
        71, 69, 67, 65, 64, 62, 61, 60};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.contourPredictability() > 0.5,
        "Arch-shaped melody should score reasonably on contour predictability, got: "
            + bd.contourPredictability());
  }

  // --- Internal conventionality tests ---

  @Test
  void stepwiseMelodyScoresHighOnConventionality() {
    // Mostly stepwise motion (seconds)
    int[] pitches = {60, 62, 64, 65, 64, 62, 60, 62,
        64, 65, 67, 65, 64, 62, 60, 62};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.internalConventionality() > 0.6,
        "Stepwise melody should score high on conventionality, got: "
            + bd.internalConventionality());
  }

  // --- Hook prominence tests ---

  @Test
  void repeatedShortPatternScoresHighOnHook() {
    // Repeating 4-note hook pattern throughout
    int[] pitches = {60, 64, 67, 65, 60, 64, 67, 65,
        60, 64, 67, 65, 60, 64, 67, 65};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertTrue(bd.hookProminence() > 0.5,
        "Repeating 4-note pattern should score high on hook prominence, got: "
            + bd.hookProminence());
  }

  // --- Score band tests ---

  @Test
  void scoreBandLabels() {
    assertEquals("Atonal, meandering - Hard to remember",
        SentenceScorer.bandLabel(15));
    assertEquals("Coherent but unremarkable",
        SentenceScorer.bandLabel(40));
    assertEquals("Solid pop/folk territory - memorable on repeated listens",
        SentenceScorer.bandLabel(65));
    assertEquals("Hook driven. Earworm.",
        SentenceScorer.bandLabel(80));
    assertEquals("Rare - suspiciously sticky",
        SentenceScorer.bandLabel(95));
  }

  // --- Weighted combination test ---

  @Test
  void totalIsWeightedSumOfFactorsTimesHundred() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60};
    Motif m = makeMotif(pitches);
    Sentence sentence = makeSentence(m, m, m, m);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    double expectedTotal = (bd.repetition() * 0.25
        + bd.contourPredictability() * 0.15
        + bd.pitchRangeCompactness() * 0.15
        + bd.rhythmicSimplicity() * 0.15
        + bd.internalConventionality() * 0.15
        + bd.hookProminence() * 0.15) * 100;

    assertEquals(expectedTotal, bd.total(), 0.01,
        "Total should be weighted sum × 100");
  }

  // --- scoreAndRank test ---

  @Test
  void scoreAndRankReturnsSortedBestFirst() {
    Motif good = makeMotif(new int[]{60, 62, 64, 65, 67, 65, 64, 62,
        60, 62, 64, 65, 67, 65, 64, 60});
    Motif bad = makeMotif(new int[]{36, 72, 84, 48, 96, 37, 85, 49,
        73, 38, 86, 50, 97, 39, 74, 51});

    Sentence goodSentence = makeSentence(good, good, good, good);
    Sentence badSentence = makeSentence(bad, bad, bad, bad);

    List<Sentence> ranked = scorer.scoreAndRank(List.of(badSentence, goodSentence));

    assertTrue(ranked.get(0).getScore() >= ranked.get(1).getScore(),
        "First result should have highest score");
  }

  // --- Empty notes edge case ---

  @Test
  void emptyNotesReturnZeroScore() {
    Motif empty = new Motif(List.of(), 4, BEATS_PER_BAR, TICKS_PER_BEAT);
    Sentence sentence = makeSentence(empty, empty, empty, empty);

    SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);

    assertEquals(0, bd.total(), 0.01);
  }
}
