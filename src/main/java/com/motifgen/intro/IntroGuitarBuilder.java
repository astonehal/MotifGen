package com.motifgen.intro;

import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.model.Note;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the guitar part for a variable-length intro (2, 3, or 4 bars).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Riff mode</b> ({@code riffScore >= 3} or template.riff()): tonic-arpeggio riff in
 *       bar 1; bar 2 is a rhythmic variation (offset by an eighth note); further bars repeat
 *       with slight transposition. The tonic octave is shifted by {@code template.octaveShift()},
 *       and the melodic shape is controlled by {@code template.riffIntervals()}.
 *   </li>
 *   <li><b>Chord mode</b>: sparse voicings with beat positions driven by
 *       {@code template.chordBeatsPerBar()} for every bar (index {@code i} = bar {@code i},
 *       clamped to the last entry for extra bars).
 *   </li>
 * </ul>
 */
public final class IntroGuitarBuilder implements IntroInstrumentBuilder<ChanneledNote> {

  /** Velocity base for riff notes. */
  private static final int RIFF_VELOCITY = 90;

  /** Velocity base for chord strums. */
  private static final int CHORD_VELOCITY_BASE = 70;

  /** Velocity increment per bar for chord mode (density build). */
  private static final int CHORD_VELOCITY_INCREMENT = 5;

  /**
   * Fallback chord beat-position table used when a template supplies no {@code chordBeatsPerBar}.
   *
   * <pre>
   *  Bar 1: beat 1 only
   *  Bar 2: beats 1, 3
   *  Bar 3: beats 1, 2, 3
   *  Bar 4: all 4 beats (full density)
   * </pre>
   */
  private static final int[][] DEFAULT_BEAT_POSITIONS = {
      {0},
      {0, 2},
      {0, 1, 2},
      {0, 1, 2, 3},
  };

  /** Fallback riff intervals (root/3rd/5th/oct) when a template supplies none. */
  private static final int[] DEFAULT_RIFF_INTERVALS = {0, 4, 7, 12};

  @Override
  public List<ChanneledNote> build(IntroContext ctx, int entryBar) {
    int introBars = ctx.barCount();
    if (entryBar > introBars) {
      return List.of();
    }
    IntroTemplatePool.GuitarTemplate template =
        IntroTemplatePool.drawGuitar(ctx, new Random());
    if (ctx.riffScore() >= 3 || template.riff()) {
      return buildRiff(ctx, entryBar, template);
    }
    return buildChords(ctx, entryBar, template);
  }

  // ---------- riff mode ----------

  private List<ChanneledNote> buildRiff(
      IntroContext ctx, int entryBar, IntroTemplatePool.GuitarTemplate template) {
    List<ChanneledNote> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int tonic = ctx.vampTonicMidi();
    int root = clampToRegister(tonic + template.octaveShift());
    int introBars = ctx.barCount();
    int[] intervals = template.riffIntervals() != null
        ? template.riffIntervals() : DEFAULT_RIFF_INTERVALS;

    for (int bar = 0; bar < introBars; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      boolean isVariation = (bar == 1); // bar 2 = variation of bar 1
      long offset = isVariation ? ppq / 2L : 0L; // shift by eighth note for variation

      for (int i = 0; i < intervals.length; i++) {
        long start = barStart + offset + (long) i * ppq;
        if (start >= (bar + 1) * barTicks) {
          break; // do not overflow into next bar
        }
        int pitch = Math.min(127, root + intervals[i]);
        int velocity = RIFF_VELOCITY - (bar > 1 ? 5 : 0); // slight dim in later bars
        Note note = new Note(pitch, start, ppq - ppq / 8L, velocity);
        events.add(new ChanneledNote(note, BackingTrack.BACKING_CHANNEL));
      }
    }
    return List.copyOf(events);
  }

  // ---------- chord mode ----------

  private List<ChanneledNote> buildChords(
      IntroContext ctx, int entryBar, IntroTemplatePool.GuitarTemplate template) {
    List<ChanneledNote> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int[] vamp = ctx.vampChords();
    int tonic = normaliseToRegister(ctx.vampTonicMidi());
    int fourth = vamp.length > 1 ? normaliseToRegister(vamp[1]) : tonic;
    int introBars = ctx.barCount();
    int[][] perBarBeats = template.chordBeatsPerBar() != null && template.chordBeatsPerBar().length > 0
        ? template.chordBeatsPerBar() : DEFAULT_BEAT_POSITIONS;

    for (int bar = 0; bar < introBars; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      int idx = Math.min(bar, perBarBeats.length - 1);
      int[] beatOffsets = perBarBeats[idx];

      // Use tonic for bars 1, 3, 4 and fourth for bar 2 (I–IV–I–I vamp)
      int root = (bar == 1 && vamp.length > 1) ? fourth : tonic;
      int velocity = CHORD_VELOCITY_BASE + bar * CHORD_VELOCITY_INCREMENT;
      long dur = ppq - ppq / 8L;

      for (int beatOffset : beatOffsets) {
        long start = barStart + (long) beatOffset * ppq;
        if (start >= (bar + 1) * barTicks) break; // clamp to bar
        Note note = new Note(root, start, dur, velocity);
        events.add(new ChanneledNote(note, BackingTrack.BACKING_CHANNEL));
        int fifth = Math.min(127, root + 7);
        Note fifthNote = new Note(fifth, start, dur, velocity - 5);
        events.add(new ChanneledNote(fifthNote, BackingTrack.BACKING_CHANNEL));
      }
    }
    return List.copyOf(events);
  }

  // ---------- helpers ----------

  /**
   * Transposes a MIDI note number into the guitar register [40, 76] (E2–E5) by shifting octaves.
   */
  private static int normaliseToRegister(int midi) {
    int pitch = midi % 12 + 52;
    while (pitch > 76) pitch -= 12;
    while (pitch < 40) pitch += 12;
    return pitch;
  }

  /** Clamps a shifted pitch back into the guitar register [40, 76]. */
  private static int clampToRegister(int midi) {
    int pitch = midi;
    while (pitch > 76) pitch -= 12;
    while (pitch < 40) pitch += 12;
    return pitch;
  }
}
