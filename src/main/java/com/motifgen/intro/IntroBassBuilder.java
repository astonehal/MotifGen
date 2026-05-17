package com.motifgen.intro;

import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.model.Note;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the bass part for a variable-length intro (2, 3, or 4 bars).
 *
 * <p>Three density tiers driven by arousal:
 * <ul>
 *   <li><b>Low</b> ({@code arousal < 0.4}): one whole-note root per bar.</li>
 *   <li><b>Mid</b> ({@code 0.4 ≤ arousal ≤ 0.75}): two-note pattern selected by
 *       {@link IntroTemplatePool.BassSubTemplate}.</li>
 *   <li><b>High</b> ({@code arousal > 0.75}): eighth-note groove selected by
 *       {@link IntroTemplatePool.BassSubTemplate}.</li>
 * </ul>
 *
 * <p>Density escalates bar-by-bar: the ramp formula is
 * {@code Math.min(targetTier, Math.max(0, targetTier - (barCount - 1 - activeBars)))} so that
 * the last active bar always reaches the target tier, regardless of bar count.
 *
 * <p>Mid-tier (tier 1) pattern types:
 * <ul>
 *   <li>{@code "ROOT_FIFTH"}: root (half-note) on beat 1, fifth (half-note) on beat 3.</li>
 *   <li>{@code "ROOT_OCTAVE"}: root on beat 1, root+12 on beat 3.</li>
 *   <li>{@code "WALKING_UP"}: root, maj2nd, maj3rd, fifth on beats 1–4 (quarter notes).</li>
 *   <li>{@code "SYNCOPATED"}: root on beat 1, fifth on beat 2.5, root on beat 3,
 *       fifth on beat 4.5.</li>
 * </ul>
 *
 * <p>High-tier (tier 2) pattern types:
 * <ul>
 *   <li>{@code "GROOVE_ROOT_FIFTH"}: alternating root/fifth on every eighth note.</li>
 *   <li>{@code "GROOVE_ROOT_OCT"}: alternating root/root+12 on every eighth note.</li>
 * </ul>
 */
public final class IntroBassBuilder implements IntroInstrumentBuilder<ChanneledNote> {

  private static final int BASS_VELOCITY = 80;
  private static final double LOW_AROUSAL = 0.4;
  private static final double HIGH_AROUSAL = 0.75;
  private static final int FIFTH_SEMITONES  = 7;
  private static final int SECOND_SEMITONES = 2;
  private static final int THIRD_SEMITONES  = 4;

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
    IntroTemplatePool.BassSubTemplate template = IntroTemplatePool.drawBass(ctx, new Random());

    for (int bar = 0; bar < introBars; bar++) {
      if (bar + 1 < entryBar) {
        continue;
      }
      long barStart = bar * barTicks;
      int activeBars = bar - (entryBar - 1);
      int tier = Math.min(targetTier, Math.max(0, targetTier - (introBars - 1 - activeBars)));

      addBassBar(events, tonic, barStart, barTicks, ppq, tier, template.patternType());
    }
    return List.copyOf(events);
  }

  // ---------- private helpers ----------

  private static int densityTier(double arousal) {
    if (arousal > HIGH_AROUSAL) return 2;
    if (arousal >= LOW_AROUSAL) return 1;
    return 0;
  }

  private void addBassBar(List<ChanneledNote> events, int root,
      long barStart, long barTicks, int ppq, int tier, String patternType) {
    switch (tier) {
      case 0 -> addWholeNote(events, root, barStart, barTicks);
      case 1 -> addMidPattern(events, root, barStart, barTicks, ppq, patternType);
      default -> addHighPattern(events, root, barStart, ppq, patternType);
    }
  }

  private void addWholeNote(List<ChanneledNote> events, int root, long barStart, long barTicks) {
    events.add(cn(root, barStart, barTicks - 10L, BASS_VELOCITY));
  }

  private void addMidPattern(List<ChanneledNote> events, int root,
      long barStart, long barTicks, int ppq, String patternType) {
    int fifth  = Math.min(127, root + FIFTH_SEMITONES);
    int second = Math.min(127, root + SECOND_SEMITONES);
    int third  = Math.min(127, root + THIRD_SEMITONES);
    long half  = (long) ppq * 2;

    switch (patternType) {
      case "ROOT_OCTAVE" -> {
        int oct = Math.min(127, root + 12);
        events.add(cn(root, barStart,        half - 10L, BASS_VELOCITY));
        events.add(cn(oct,  barStart + half, half - 10L, BASS_VELOCITY - 5));
      }
      case "WALKING_UP" -> {
        // root → maj2nd → maj3rd → fifth (quarter notes ascending)
        int[] walk = {root, second, third, fifth};
        for (int i = 0; i < walk.length; i++) {
          events.add(cn(walk[i], barStart + (long) i * ppq, (long) ppq - 10L, BASS_VELOCITY - i * 3));
        }
      }
      case "SYNCOPATED" -> {
        // root on 1, fifth on 2.5, root on 3, fifth on 4.5
        long eighth = ppq / 2L;
        events.add(cn(root,  barStart,                    (long) ppq - 10L, BASS_VELOCITY));
        events.add(cn(fifth, barStart + ppq + eighth,     (long) ppq - 10L, BASS_VELOCITY - 5));
        events.add(cn(root,  barStart + 2L * ppq,         (long) ppq - 10L, BASS_VELOCITY));
        events.add(cn(fifth, barStart + 3L * ppq + eighth,(long) ppq - 10L, BASS_VELOCITY - 5));
      }
      default -> { // "ROOT_FIFTH"
        events.add(cn(root,  barStart,        half - 10L, BASS_VELOCITY));
        events.add(cn(fifth, barStart + half, half - 10L, BASS_VELOCITY - 5));
      }
    }
  }

  private void addHighPattern(List<ChanneledNote> events, int root,
      long barStart, int ppq, String patternType) {
    long eighth = ppq / 2L;
    int upper = "GROOVE_ROOT_OCT".equals(patternType)
        ? Math.min(127, root + 12)
        : Math.min(127, root + FIFTH_SEMITONES);

    for (int i = 0; i < 8; i++) {
      int pitch = (i % 2 == 0) ? root : upper;
      long start = barStart + i * eighth;
      events.add(cn(pitch, start, eighth - 10L, BASS_VELOCITY - (i % 2 == 0 ? 0 : 8)));
    }
  }

  private static ChanneledNote cn(int pitch, long start, long dur, int velocity) {
    return new ChanneledNote(new Note(pitch, start, dur, velocity), BassTrack.BASS_CHANNEL);
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
