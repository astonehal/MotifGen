package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure motif transformations used by {@link PhraseSeeder} to seed each phrase
 * of a sentence from the input motif. Every transform preserves the original
 * note start ticks, durations and velocities; only pitches change.
 *
 * <p>Supported operations: {@code identity}, {@code diatonicTranspose},
 * {@code invert}, {@code retrograde}.
 */
public final class MotifTransformer {

  /** Returns the motif unchanged. */
  public Motif identity(Motif motif) {
    return motif;
  }

  /**
   * Diatonically transposes every sounding pitch by {@code scaleSteps} positions
   * within {@code key}. Pitches that are not members of {@code key} are first
   * snapped to the nearest in-key pitch, then transposed. Rests are preserved.
   */
  public Motif diatonicTranspose(Motif motif, int scaleSteps, KeySignature key) {
    List<Note> out = new ArrayList<>();
    for (Note n : motif.getNotes()) {
      if (n.isRest()) {
        out.add(n);
      } else {
        int newPitch = shiftBySteps(n.pitch(), scaleSteps, key);
        out.add(new Note(newPitch, n.startTick(), n.durationTicks(), n.velocity()));
      }
    }
    return new Motif(out, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
  }

  /**
   * Mirrors every sounding pitch around {@code pivotPitch}. Rests are preserved.
   * The result is clamped to the MIDI range {@code [0, 127]}.
   */
  public Motif invert(Motif motif, int pivotPitch) {
    List<Note> out = new ArrayList<>();
    for (Note n : motif.getNotes()) {
      if (n.isRest()) {
        out.add(n);
      } else {
        int newPitch = Math.max(0, Math.min(127, 2 * pivotPitch - n.pitch()));
        out.add(new Note(newPitch, n.startTick(), n.durationTicks(), n.velocity()));
      }
    }
    return new Motif(out, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
  }

  /**
   * Reverses the pitch sequence across the motif's existing rhythmic slots.
   * Note start ticks and durations are preserved; only pitches are reordered.
   * Rests stay in place and are skipped during reversal.
   */
  public Motif retrograde(Motif motif) {
    List<Integer> pitched = new ArrayList<>();
    for (Note n : motif.getNotes()) {
      if (!n.isRest()) pitched.add(n.pitch());
    }
    Collections.reverse(pitched);

    List<Note> out = new ArrayList<>();
    int idx = 0;
    for (Note n : motif.getNotes()) {
      if (n.isRest()) {
        out.add(n);
      } else {
        out.add(new Note(pitched.get(idx++), n.startTick(),
            n.durationTicks(), n.velocity()));
      }
    }
    return new Motif(out, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
  }

  private static int shiftBySteps(int pitch, int scaleSteps, KeySignature key) {
    int p = pitch;
    if (!key.containsPitchClass(((p % 12) + 12) % 12)) {
      p = nearestInKey(p, key);
    }
    if (scaleSteps == 0) return p;

    int direction = scaleSteps > 0 ? 1 : -1;
    int remaining = Math.abs(scaleSteps);
    while (remaining > 0) {
      int next = direction == 1 ? nextScaleUp(p, key) : nextScaleDown(p, key);
      if (next == p) break;
      p = next;
      remaining--;
    }
    return p;
  }

  private static int nextScaleUp(int pitch, KeySignature key) {
    for (int p = pitch + 1; p <= 127; p++) {
      if (key.containsPitchClass(((p % 12) + 12) % 12)) return p;
    }
    return pitch;
  }

  private static int nextScaleDown(int pitch, KeySignature key) {
    for (int p = pitch - 1; p >= 0; p--) {
      if (key.containsPitchClass(((p % 12) + 12) % 12)) return p;
    }
    return pitch;
  }

  private static int nearestInKey(int pitch, KeySignature key) {
    int clamped = Math.max(0, Math.min(127, pitch));
    for (int d = 0; d <= 6; d++) {
      int up = clamped + d;
      if (up <= 127 && key.containsPitchClass(((up % 12) + 12) % 12)) return up;
      int down = clamped - d;
      if (down >= 0 && key.containsPitchClass(((down % 12) + 12) % 12)) return down;
    }
    return clamped;
  }
}
