package com.motifgen.intro;

import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.model.Note;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the bass part for the 4-bar intro.
 *
 * <p>Three density tiers driven by arousal:
 * <ul>
 *   <li><b>Low</b> ({@code arousal < 0.4}): one whole-note root per bar.</li>
 *   <li><b>Mid</b> ({@code 0.4 ≤ arousal ≤ 0.75}): root on beat 1, fifth on beat 3.</li>
 *   <li><b>High</b> ({@code arousal > 0.75}): eighth-note groove (root/fifth alternating).</li>
 * </ul>
 *
 * <p>Density escalates bar-by-bar: bar 1 uses the tier below the actual tier (or stays at
 * low if already low), and each subsequent bar steps up one tier until the target tier is
 * reached.
 */
public final class IntroBassBuilder implements IntroInstrumentBuilder<ChanneledNote> {

  private static final int INTRO_BARS = 4;
  private static final int BASS_VELOCITY = 80;
  private static final double LOW_AROUSAL = 0.4;
  private static final double HIGH_AROUSAL = 0.75;
  private static final int FIFTH_SEMITONES = 7;

  @Override
  public List<ChanneledNote> build(IntroContext ctx, int entryBar) {
    if (entryBar > INTRO_BARS) {
      return List.of();
    }
    List<ChanneledNote> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    double arousal = ctx.sentiment().arousal();
    int tonic = bassRoot(ctx.vampChords()[0]);

    // Determine target density tier for this arousal level.
    int targetTier = densityTier(arousal);

    for (int bar = 0; bar < INTRO_BARS; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      // Escalate: bar index within active bars (0-based from entryBar).
      int activeBars = bar - (entryBar - 1);
      // Tier starts 1 below target in bar 0, ramps up, capped at target.
      int tier = Math.min(targetTier, Math.max(0, targetTier - (INTRO_BARS - 1 - activeBars)));

      addBassBar(events, ctx, tonic, barStart, barTicks, ppq, tier);
    }
    return List.copyOf(events);
  }

  // ---------- private helpers ----------

  private static int densityTier(double arousal) {
    if (arousal > HIGH_AROUSAL) return 2;
    if (arousal >= LOW_AROUSAL) return 1;
    return 0;
  }

  private void addBassBar(List<ChanneledNote> events, IntroContext ctx, int root,
      long barStart, long barTicks, int ppq, int tier) {
    switch (tier) {
      case 0 -> addWholeNote(events, root, barStart, barTicks);
      case 1 -> addRootFifth(events, root, barStart, ppq);
      default -> addEighthGroove(events, root, barStart, ppq);
    }
  }

  private void addWholeNote(List<ChanneledNote> events, int root, long barStart, long barTicks) {
    Note note = new Note(root, barStart, barTicks - 10L, BASS_VELOCITY);
    events.add(new ChanneledNote(note, BassTrack.BASS_CHANNEL));
  }

  private void addRootFifth(List<ChanneledNote> events, int root, long barStart, int ppq) {
    // Root on beat 1, fifth on beat 3.
    Note rootNote = new Note(root, barStart, (long) ppq * 2 - 10L, BASS_VELOCITY);
    events.add(new ChanneledNote(rootNote, BassTrack.BASS_CHANNEL));
    int fifth = Math.min(127, root + FIFTH_SEMITONES);
    Note fifthNote = new Note(fifth, barStart + (long) ppq * 2, (long) ppq * 2 - 10L,
        BASS_VELOCITY - 5);
    events.add(new ChanneledNote(fifthNote, BassTrack.BASS_CHANNEL));
  }

  private void addEighthGroove(List<ChanneledNote> events, int root, long barStart, int ppq) {
    // Alternating root/fifth on each eighth note.
    long eighth = ppq / 2L;
    int fifth = Math.min(127, root + FIFTH_SEMITONES);
    for (int i = 0; i < 8; i++) {
      int pitch = (i % 2 == 0) ? root : fifth;
      long start = barStart + i * eighth;
      Note note = new Note(pitch, start, eighth - 10L, BASS_VELOCITY - (i % 2 == 0 ? 0 : 8));
      events.add(new ChanneledNote(note, BassTrack.BASS_CHANNEL));
    }
  }

  /**
   * Normalises a MIDI root to a bass-appropriate register [28, 52] (E1–E3).
   */
  private static int bassRoot(int midi) {
    int pitch = midi % 12 + 40; // start near E2
    while (pitch > 52) pitch -= 12;
    while (pitch < 28) pitch += 12;
    return pitch;
  }
}
