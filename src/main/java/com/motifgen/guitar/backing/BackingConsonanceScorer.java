package com.motifgen.guitar.backing;

import com.motifgen.model.Note;

import java.util.List;
import java.util.Map;

/**
 * Scores the consonance of a voiced backing track against a melody.
 *
 * <p>For each (chord note, melody note, beat-strength) triple the interval
 * class is looked up in {@link #CONSONANCE_TABLE}; the resulting values are
 * weighted by beat strength and normalised to 0–100.
 */
public final class BackingConsonanceScorer {

  /**
   * Consonance table keyed by interval class in semitones (0–11).
   * Values from the acceptance criteria.
   */
  static final Map<Integer, Double> CONSONANCE_TABLE = Map.ofEntries(
      Map.entry(0,  1.0),
      Map.entry(1,  0.1),
      Map.entry(2,  0.4),
      Map.entry(3,  0.8),
      Map.entry(4,  0.9),
      Map.entry(5,  0.7),
      Map.entry(6,  0.1),
      Map.entry(7,  1.0),
      Map.entry(8,  0.7),
      Map.entry(9,  0.8),
      Map.entry(10, 0.4),
      Map.entry(11, 0.1)
  );

  private BackingConsonanceScorer() {}

  /**
   * Computes a normalised consonance score (0–100) for the voiced chords
   * against the melody.
   *
   * @param voicedChords  voiced chords from the backing track
   * @param melodyNotes   all melody notes in time order
   * @param ppq           ticks per quarter note (used to derive beat strength)
   * @return consonance score 0–100
   */
  public static double score(
      List<VoicedChord> voicedChords, List<Note> melodyNotes, int ppq) {
    if (voicedChords.isEmpty() || melodyNotes.isEmpty()) return 0.0;

    double weightedSum = 0.0;
    double totalWeight = 0.0;

    for (VoicedChord vc : voicedChords) {
      // Find melody notes that sound during this chord
      List<Note> concurrent = concurrentMelodyNotes(vc, melodyNotes);
      if (concurrent.isEmpty()) continue;

      for (Note chordNote : vc.notes()) {
        if (chordNote.isRest()) continue;
        for (Note melodyNote : concurrent) {
          if (melodyNote.isRest()) continue;
          double beatStrength = beatStrength(melodyNote.startTick(), ppq);
          int interval = intervalClass(chordNote.pitch(), melodyNote.pitch());
          double consonance = CONSONANCE_TABLE.getOrDefault(interval, 0.5);
          weightedSum += consonance * beatStrength;
          totalWeight += beatStrength;
        }
      }
    }

    if (totalWeight == 0.0) return 50.0; // no overlap — neutral score
    double raw = weightedSum / totalWeight; // 0–1
    return Math.max(0.0, Math.min(100.0, raw * 100.0));
  }

  /**
   * Computes a normalised consonance score (0–100) that includes bass notes.
   *
   * <p>Guitar (voiced chord) notes contribute weight 1.0 per beat; bass notes
   * contribute weight 0.5 per beat.  The scoring logic is otherwise identical
   * to {@link #score(List, List, int)}.
   *
   * @param voicedChords  voiced guitar chords
   * @param melodyNotes   melody notes in time order
   * @param bassNotes     bass notes from {@link BassTrack}
   * @param ppq           ticks per quarter note
   * @return consonance score 0–100
   */
  public static double scoreWithBass(
      List<VoicedChord> voicedChords,
      List<Note> melodyNotes,
      List<BassNote> bassNotes,
      int ppq) {

    if (melodyNotes.isEmpty()) return 0.0;

    double weightedSum = 0.0;
    double totalWeight = 0.0;

    // Guitar notes — weight 1.0
    for (VoicedChord vc : voicedChords) {
      List<Note> concurrent = concurrentMelodyNotes(vc, melodyNotes);
      if (concurrent.isEmpty()) continue;
      for (Note chordNote : vc.notes()) {
        if (chordNote.isRest()) continue;
        for (Note melodyNote : concurrent) {
          if (melodyNote.isRest()) continue;
          double beatStrength = beatStrength(melodyNote.startTick(), ppq);
          int interval = intervalClass(chordNote.pitch(), melodyNote.pitch());
          double consonance = CONSONANCE_TABLE.getOrDefault(interval, 0.5);
          weightedSum += consonance * beatStrength * 1.0;
          totalWeight += beatStrength * 1.0;
        }
      }
    }

    // Bass notes — weight 0.5
    for (BassNote bassNote : bassNotes) {
      List<Note> concurrent = melodyNotes.stream()
          .filter(n -> !n.isRest()
              && n.startTick() < bassNote.startTick() + bassNote.durationTicks()
              && n.endTick() > bassNote.startTick())
          .toList();
      for (Note melodyNote : concurrent) {
        if (melodyNote.isRest()) continue;
        double beatStrength = beatStrength(melodyNote.startTick(), ppq);
        int interval = intervalClass(bassNote.midi(), melodyNote.pitch());
        double consonance = CONSONANCE_TABLE.getOrDefault(interval, 0.5);
        weightedSum += consonance * beatStrength * 0.5;
        totalWeight += beatStrength * 0.5;
      }
    }

    if (totalWeight == 0.0) return 50.0;
    double raw = weightedSum / totalWeight;
    return Math.max(0.0, Math.min(100.0, raw * 100.0));
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  /** Returns melody notes that overlap with the voiced chord's time span. */
  private static List<Note> concurrentMelodyNotes(VoicedChord vc, List<Note> melody) {
    if (vc.notes().isEmpty()) return List.of();
    long chordStart = vc.startTick();
    // Determine chord end from the notes themselves
    long chordEnd = vc.notes().stream().mapToLong(Note::endTick).max().orElse(chordStart + 1);
    return melody.stream()
        .filter(n -> !n.isRest()
            && n.startTick() < chordEnd
            && n.endTick() > chordStart)
        .toList();
  }

  /**
   * Beat strength: beat 1 (tick % ticksPerBar == 0) → 1.0; beat 3 → 0.75;
   * beats 2 and 4 → 0.5; off-beats → 0.25.
   */
  private static double beatStrength(long tick, int ppq) {
    long ticksPerBar = ppq * 4L;
    long posInBar = tick % ticksPerBar;
    if (posInBar == 0)         return 1.0;
    if (posInBar == ppq * 2L)  return 0.75;
    if (posInBar % ppq == 0)   return 0.5;
    return 0.25;
  }

  /** Interval class: absolute semitone distance mod 12. */
  private static int intervalClass(int pitch1, int pitch2) {
    return Math.abs(pitch1 - pitch2) % 12;
  }
}
