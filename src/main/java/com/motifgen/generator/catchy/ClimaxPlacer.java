package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures the melodic peak sits at the planned climax position. If the note
 * at the target index is not already the global maximum, it is transposed
 * upward (staying in key) to exceed the current maximum.
 *
 * <p>When a {@link SentimentProfile} is provided, the lift in semitones is
 * scaled by arousal: {@code liftSemitones = 1 + (int)(arousal * 6)}, clamped
 * to [1, 7] (Scenario 8).
 */
public final class ClimaxPlacer {

  private static final int DEFAULT_LIFT_SEMITONES = 3;
  private static final int MIN_LIFT_SEMITONES     = 1;
  private static final int MAX_LIFT_SEMITONES     = 7;
  private static final int MAX_PITCH              = 100;

  /** Backward-compatible overload — uses the default lift of 3 semitones. */
  public Motif enforceClimax(Motif motif, int climaxIndex, KeySignature key) {
    return enforceClimaxInternal(motif, climaxIndex, key, DEFAULT_LIFT_SEMITONES);
  }

  /**
   * Sentiment-aware overload. Lift semitones = 1 + (int)(arousal * 6), clamped
   * to [1, 7].
   */
  public Motif enforceClimax(Motif motif, int climaxIndex, KeySignature key,
      SentimentProfile profile) {
    int lift = 1 + (int) (profile.arousal() * 6);
    lift = Math.max(MIN_LIFT_SEMITONES, Math.min(MAX_LIFT_SEMITONES, lift));
    return enforceClimaxInternal(motif, climaxIndex, key, lift);
  }

  private Motif enforceClimaxInternal(Motif motif, int climaxIndex, KeySignature key,
      int liftSemitones) {
    List<Note> notes = motif.getNotes();
    int[] soundingIndices = soundingIndices(notes);

    if (climaxIndex < 0 || climaxIndex >= soundingIndices.length) {
      return motif;
    }

    int currentMax = Integer.MIN_VALUE;
    for (int idx : soundingIndices) {
      currentMax = Math.max(currentMax, notes.get(idx).pitch());
    }

    int targetNoteIndex = soundingIndices[climaxIndex];
    Note target = notes.get(targetNoteIndex);
    if (target.pitch() == currentMax) {
      int peaks = 0;
      for (int idx : soundingIndices) {
        if (notes.get(idx).pitch() == currentMax) peaks++;
      }
      if (peaks == 1) return motif;
    }

    int liftedPitch = findInKeyPitchAbove(currentMax + liftSemitones, key);

    List<Note> updated = new ArrayList<>(notes);
    updated.set(targetNoteIndex, new Note(liftedPitch, target.startTick(),
        target.durationTicks(), target.velocity()));

    return new Motif(updated, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
  }

  private static int[] soundingIndices(List<Note> notes) {
    List<Integer> idxs = new ArrayList<>();
    for (int i = 0; i < notes.size(); i++) {
      if (!notes.get(i).isRest()) idxs.add(i);
    }
    int[] arr = new int[idxs.size()];
    for (int i = 0; i < idxs.size(); i++) arr[i] = idxs.get(i);
    return arr;
  }

  private static int findInKeyPitchAbove(int floor, KeySignature key) {
    int candidate = Math.min(MAX_PITCH, Math.max(0, floor));
    for (int p = candidate; p <= MAX_PITCH; p++) {
      if (key.containsPitchClass(((p % 12) + 12) % 12)) {
        return p;
      }
    }
    for (int p = Math.min(candidate, MAX_PITCH); p >= 0; p--) {
      if (key.containsPitchClass(((p % 12) + 12) % 12)) {
        return p;
      }
    }
    return Math.max(0, Math.min(MAX_PITCH, floor));
  }
}
