package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures the melodic peak sits at the planned climax position. If the note
 * at the target index is not already the global maximum, it is transposed
 * upward (staying in key) to exceed the current maximum. MIDI range and
 * singability are respected by preferring the lowest valid pitch that works.
 */
public final class ClimaxPlacer {

  private static final int MIN_LIFT_SEMITONES = 3;
  private static final int MAX_PITCH = 100;

  public Motif enforceClimax(Motif motif, int climaxIndex, KeySignature key) {
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

    int liftedPitch = findInKeyPitchAbove(currentMax + MIN_LIFT_SEMITONES, key);

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
