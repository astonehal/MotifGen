package com.motifgen.intro;

import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.model.Note;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the guitar part for the 4-bar intro.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Riff mode</b> ({@code riffScore >= 3}): tonic-arpeggio riff in bar 1; bar 2 is a
 *       rhythmic variation (offset by an eighth note); bars 3–4 repeat with slight transposition.
 *   </li>
 *   <li><b>Chord mode</b> ({@code riffScore < 2}): sparse voicings with density increasing each
 *       bar (1 strum/bar 1 → 2 strums/bar 2 → 3 strums/bar 3 → 4 strums/bar 4).
 *   </li>
 * </ul>
 */
public final class IntroGuitarBuilder implements IntroInstrumentBuilder<ChanneledNote> {

  /** Number of intro bars. */
  private static final int INTRO_BARS = 4;

  /** Velocity base for riff notes. */
  private static final int RIFF_VELOCITY = 90;

  /** Velocity base for chord strums. */
  private static final int CHORD_VELOCITY_BASE = 70;

  /** Velocity increment per bar for chord mode (density build). */
  private static final int CHORD_VELOCITY_INCREMENT = 5;

  @Override
  public List<ChanneledNote> build(IntroContext ctx, int entryBar) {
    if (entryBar > INTRO_BARS) {
      return List.of();
    }
    if (ctx.riffScore() >= 3) {
      return buildRiff(ctx, entryBar);
    }
    return buildChords(ctx, entryBar);
  }

  // ---------- riff mode ----------

  private List<ChanneledNote> buildRiff(IntroContext ctx, int entryBar) {
    List<ChanneledNote> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int tonic = ctx.vampChords()[0];
    // Ensure tonic is in guitar register (40-76 = E2-E5), target octave 4 starting at C4=60.
    int root = normaliseToRegister(tonic);

    for (int bar = 0; bar < INTRO_BARS; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      boolean isVariation = (bar == 1); // bar 2 = variation of bar 1
      long offset = isVariation ? ppq / 2L : 0L; // shift by eighth note for variation

      // Arpeggio: root, third, fifth, root+octave each a quarter note apart
      int[] intervals = {0, 4, 7, 12};
      for (int i = 0; i < intervals.length; i++) {
        long start = barStart + offset + (long) i * ppq;
        if (start >= (bar + 1) * barTicks) {
          break; // do not overflow into next bar
        }
        int pitch = Math.min(127, root + intervals[i]);
        int velocity = RIFF_VELOCITY - (bar > 1 ? 5 : 0); // slight dim in bars 3-4
        Note note = new Note(pitch, start, ppq - ppq / 8L, velocity);
        events.add(new ChanneledNote(note, BackingTrack.BACKING_CHANNEL));
      }
    }
    return List.copyOf(events);
  }

  // ---------- chord mode ----------

  private List<ChanneledNote> buildChords(IntroContext ctx, int entryBar) {
    List<ChanneledNote> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    int[] vamp = ctx.vampChords();
    int tonic = normaliseToRegister(vamp[0]);
    int fourth = vamp.length > 1 ? normaliseToRegister(vamp[1]) : tonic;

    for (int bar = 0; bar < INTRO_BARS; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      // Density: strums per bar = bar index + 1, capped at beatsPerBar
      int strums = Math.min(bar + 1, ctx.beatsPerBar());
      long spacing = barTicks / strums;
      // Use tonic for bars 1, 3, 4 and fourth for bar 2 (I–IV–I–I vamp)
      int root = (bar == 1 && vamp.length > 1) ? fourth : tonic;
      int velocity = CHORD_VELOCITY_BASE + bar * CHORD_VELOCITY_INCREMENT;

      for (int s = 0; s < strums; s++) {
        long start = barStart + s * spacing;
        long dur = spacing - ppq / 8L;
        if (dur <= 0) {
          dur = ppq / 4L;
        }
        Note note = new Note(root, start, dur, velocity);
        events.add(new ChanneledNote(note, BackingTrack.BACKING_CHANNEL));
        // Add fifth above for a fuller voicing
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
    int pitch = midi % 12 + 52; // start in octave around 4 (C4=60, but guitar is lower)
    while (pitch > 76) {
      pitch -= 12;
    }
    while (pitch < 40) {
      pitch += 12;
    }
    return pitch;
  }
}
