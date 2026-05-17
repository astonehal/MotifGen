package com.motifgen.intro;

import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.model.Note;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the bass part for a variable-length intro (2, 3, or 4 bars).
 *
 * <p>Three density tiers driven by arousal:
 * <ul>
 *   <li><b>Low</b> ({@code arousal < 0.4}): one whole-note root per bar.</li>
 *   <li><b>Mid</b> ({@code 0.4 ≤ arousal ≤ 0.75}): root on beat 1, fifth on beat 3.</li>
 *   <li><b>High</b> ({@code arousal > 0.75}): eighth-note groove (root/fifth alternating).</li>
 * </ul>
 *
 * <p>Density escalates bar-by-bar: the ramp formula is
 * {@code Math.min(targetTier, Math.max(0, targetTier - (barCount - 1 - activeBars)))} so that
 * the last active bar always reaches the target tier, regardless of bar count.
 */
public final class IntroBassBuilder implements IntroInstrumentBuilder<ChanneledNote> {

  private static final int BASS_VELOCITY = 80;
  private static final double LOW_AROUSAL = 0.4;
  private static final double HIGH_AROUSAL = 0.75;
  private static final int FIFTH_SEMITONES = 7;

  @Override
  public List<ChanneledNote> build(IntroContext ctx, int entryBar) {
    int introBars = ctx.barCount();
    if (entryBar > introBars) {
      return List.of();
    }
    List<ChanneledNote> events = new ArrayList<>();
    int ppq = ctx.ticksPerBeat();
    long barTicks = (long) ctx.beatsPerBar() * ppq;
    double arousal = ctx.sentiment().arousal();
    int tonic = bassRoot(ctx.vampTonicMidi());

    int targetTier = densityTier(arousal);

    for (int bar = 0; bar < introBars; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      int activeBars = bar - (entryBar - 1);
      int tier = Math.min(targetTier, Math.max(0, targetTier - (introBars - 1 - activeBars)));

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
    Note rootNote = new Note(root, barStart, (long) ppq * 2 - 10L, BASS_VELOCITY);
    events.add(new ChanneledNote(rootNote, BassTrack.BASS_CHANNEL));
    int fifth = Math.min(127, root + FIFTH_SEMITONES);
    Note fifthNote = new Note(fifth, barStart + (long) ppq * 2, (long) ppq * 2 - 10L,
        BASS_VELOCITY - 5);
    events.add(new ChanneledNote(fifthNote, BassTrack.BASS_CHANNEL));
  }

  private void addEighthGroove(List<ChanneledNote> events, int root, long barStart, int ppq) {
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
    int pitch = midi % 12 + 40;
    while (pitch > 52) pitch -= 12;
    while (pitch < 28) pitch += 12;
    return pitch;
  }
}
