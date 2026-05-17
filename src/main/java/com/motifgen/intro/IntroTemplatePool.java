package com.motifgen.intro;

import java.util.List;
import java.util.Random;

/**
 * Pools of named templates for entry ordering, guitar patterns, drum sub-patterns, and bass
 * patterns.
 *
 * <p>Three lists for each category (HIGH / MID / LOW arousal), each containing 4 entries.
 * The draw methods select a list appropriate to the context's arousal level, then pick one
 * element at random using the supplied {@link Random}.
 *
 * <p>Records are package-private inner types so that builders and tests can reference them
 * without an extra import hierarchy.
 */
public final class IntroTemplatePool {

  private IntroTemplatePool() {}

  // -----------------------------------------------------------------------
  // Inner record types
  // -----------------------------------------------------------------------

  /**
   * Describes which bar each instrument enters (1-indexed, before clamping to barCount).
   *
   * @param name      human-readable identifier
   * @param guitarBar bar number at which guitar enters
   * @param bassBar   bar number at which bass enters
   * @param drumsBar  bar number at which drums enter
   */
  public record EntryTemplate(String name, int guitarBar, int bassBar, int drumsBar) {}

  /**
   * Describes how the guitar part is voiced.
   *
   * @param name            human-readable identifier
   * @param riff            if {@code true}, use riff mode; otherwise chord strum mode
   * @param octaveShift     semitone shift applied to the tonic root in riff mode
   * @param riffIntervals   semitone intervals above the tonic for each riff note (riff mode only)
   * @param chordBeatsPerBar beat offsets (0-indexed) per bar for chord strum mode;
   *                        index {@code i} is used for bar {@code i}, clamped to last entry
   */
  public record GuitarTemplate(
      String name, boolean riff, int octaveShift,
      int[] riffIntervals, int[][] chordBeatsPerBar) {}

  /**
   * Describes the drum sub-pattern applied to both groove bars and the launch-fill bar.
   *
   * @param name           human-readable identifier
   * @param twoBeatsGroove if {@code true} → 2 beats groove + 2 beats fill in the launch bar;
   *                       if {@code false} → 1 beat groove + 3 beats fill
   * @param grooveType     hi-hat pattern used in groove bars:
   *                       {@code "EIGHTH"} every eighth note,
   *                       {@code "QUARTER"} every quarter note,
   *                       {@code "SIXTEENTH"} every sixteenth note,
   *                       {@code "OFF_BEAT"} open hi-hat on the "and" of each beat
   */
  public record DrumSubTemplate(String name, boolean twoBeatsGroove, String grooveType) {}

  /**
   * Describes the bass line pattern for the intro.
   *
   * @param name        human-readable identifier
   * @param patternType one of {@code "ROOT_FIFTH"}, {@code "ROOT_OCTAVE"},
   *                    {@code "WALKING_UP"}, {@code "SYNCOPATED"},
   *                    {@code "GROOVE_ROOT_FIFTH"}, {@code "GROOVE_ROOT_OCT"},
   *                    {@code "WHOLE_NOTE"}
   */
  public record BassSubTemplate(String name, String patternType) {}

  // -----------------------------------------------------------------------
  // Entry template pools
  // -----------------------------------------------------------------------

  private static final List<EntryTemplate> HIGH_ENTRY = List.of(
      new EntryTemplate("high_all_bar1",     1, 1, 1),
      new EntryTemplate("high_guitar_bass1", 1, 1, 2),
      new EntryTemplate("high_guitar1_rest2",1, 2, 1),
      new EntryTemplate("high_drums_lead",   2, 2, 1)
  );

  private static final List<EntryTemplate> MID_ENTRY = List.of(
      new EntryTemplate("mid_guitar1_rest2", 1, 2, 2),
      new EntryTemplate("mid_guitar1_bass2", 1, 2, 3),
      new EntryTemplate("mid_drums1",        2, 3, 1),
      new EntryTemplate("mid_guitar_bass1",  1, 1, 3)
  );

  private static final List<EntryTemplate> LOW_ENTRY = List.of(
      new EntryTemplate("low_guitar1_stagger",  1, 2, 3),
      new EntryTemplate("low_guitar1_stagger2", 1, 3, 2),
      new EntryTemplate("low_drums1",           2, 3, 1),
      new EntryTemplate("low_all_late",         2, 3, 3)
  );

  // -----------------------------------------------------------------------
  // Guitar template pools
  // -----------------------------------------------------------------------

  // Chord build-up patterns: each array is the beat offsets for one bar (0-indexed within bar).
  // Index 0 = bar 1, index 1 = bar 2, etc.  Builder clamps to last entry if bar >= array length.

  private static final List<GuitarTemplate> HIGH_GUITAR = List.of(
      // Riff: major arpeggio ascending (root/3rd/5th/oct)
      new GuitarTemplate("high_riff_major",  true,   0, new int[]{0, 4, 7, 12}, null),
      // Riff: power-chord shape (root/5th/oct/5th), up an octave
      new GuitarTemplate("high_riff_power",  true,  12, new int[]{0, 7, 12, 7}, null),
      // Chord: aggressive build – starts beat 1, reaches all 4 beats by bar 3
      new GuitarTemplate("high_chord_dense", false,  0, null,
          new int[][]{{0}, {0, 2}, {0, 1, 2}, {0, 1, 2, 3}}),
      // Chord: syncopated – opens on beats 1+4, then fills in
      new GuitarTemplate("high_chord_synco", false,  0, null,
          new int[][]{{0, 3}, {0, 1, 3}, {0, 1, 2, 3}, {0, 1, 2, 3}})
  );

  private static final List<GuitarTemplate> MID_GUITAR = List.of(
      // Chord: classic sparse→dense build
      new GuitarTemplate("mid_chord_sparse",  false,  0, null,
          new int[][]{{0}, {0, 2}, {0, 1, 2}, {0, 1, 2, 3}}),
      // Chord: starts on beat 3 (offbeat opener), then fills in
      new GuitarTemplate("mid_chord_offbeat", false,  0, null,
          new int[][]{{2}, {0, 2}, {0, 1, 2}, {0, 1, 2, 3}}),
      // Riff: major arpeggio
      new GuitarTemplate("mid_riff_major",    true,   0, new int[]{0, 4, 7, 12}, null),
      // Riff: quartal feel (root/4th/5th/oct), down an octave
      new GuitarTemplate("mid_riff_quartal",  true, -12, new int[]{0, 5, 7, 12}, null)
  );

  private static final List<GuitarTemplate> LOW_GUITAR = List.of(
      // Chord: very slow build – just beat 1 for two bars, then opens up
      new GuitarTemplate("low_chord_patient", false,  0, null,
          new int[][]{{0}, {0}, {0, 2}, {0, 1, 2}}),
      // Chord: gentle sparse→dense
      new GuitarTemplate("low_chord_sparse",  false,  0, null,
          new int[][]{{0}, {0, 2}, {0, 1, 2}, {0, 1, 2, 3}}),
      // Riff: minor-feel arpeggio (root/m3/5th/oct)
      new GuitarTemplate("low_riff_minor",    true,   0, new int[]{0, 3, 7, 12}, null),
      // Riff: pentatonic shape (root/4th/5th/m7)
      new GuitarTemplate("low_riff_pent",     true,   0, new int[]{0, 5, 7, 10}, null)
  );

  // -----------------------------------------------------------------------
  // Drum sub-template pools
  // -----------------------------------------------------------------------

  private static final List<DrumSubTemplate> HIGH_DRUM = List.of(
      new DrumSubTemplate("high_fill2_eighth",   true,  "EIGHTH"),
      new DrumSubTemplate("high_fill2_sixteen",  true,  "SIXTEENTH"),
      new DrumSubTemplate("high_fill1_eighth",   false, "EIGHTH"),
      new DrumSubTemplate("high_fill1_sixteen",  false, "SIXTEENTH")
  );

  private static final List<DrumSubTemplate> MID_DRUM = List.of(
      new DrumSubTemplate("mid_fill2_eighth",    true,  "EIGHTH"),
      new DrumSubTemplate("mid_fill2_quarter",   true,  "QUARTER"),
      new DrumSubTemplate("mid_fill1_eighth",    false, "EIGHTH"),
      new DrumSubTemplate("mid_fill1_offbeat",   false, "OFF_BEAT")
  );

  private static final List<DrumSubTemplate> LOW_DRUM = List.of(
      new DrumSubTemplate("low_fill1_quarter",   false, "QUARTER"),
      new DrumSubTemplate("low_fill2_quarter",   true,  "QUARTER"),
      new DrumSubTemplate("low_fill1_offbeat",   false, "OFF_BEAT"),
      new DrumSubTemplate("low_fill2_offbeat",   true,  "OFF_BEAT")
  );

  // -----------------------------------------------------------------------
  // Bass sub-template pools
  // -----------------------------------------------------------------------

  private static final List<BassSubTemplate> HIGH_BASS = List.of(
      new BassSubTemplate("high_bass_rf",  "GROOVE_ROOT_FIFTH"),
      new BassSubTemplate("high_bass_ro",  "GROOVE_ROOT_OCT"),
      new BassSubTemplate("high_bass_rf2", "GROOVE_ROOT_FIFTH"),
      new BassSubTemplate("high_bass_ro2", "GROOVE_ROOT_OCT")
  );

  private static final List<BassSubTemplate> MID_BASS = List.of(
      new BassSubTemplate("mid_bass_root_fifth", "ROOT_FIFTH"),
      new BassSubTemplate("mid_bass_root_oct",   "ROOT_OCTAVE"),
      new BassSubTemplate("mid_bass_walking",    "WALKING_UP"),
      new BassSubTemplate("mid_bass_synco",      "SYNCOPATED")
  );

  private static final List<BassSubTemplate> LOW_BASS = List.of(
      new BassSubTemplate("low_bass_a", "WHOLE_NOTE"),
      new BassSubTemplate("low_bass_b", "WHOLE_NOTE"),
      new BassSubTemplate("low_bass_c", "WHOLE_NOTE"),
      new BassSubTemplate("low_bass_d", "WHOLE_NOTE")
  );

  // -----------------------------------------------------------------------
  // Draw methods
  // -----------------------------------------------------------------------

  /**
   * Draws one {@link EntryTemplate} at random from the list appropriate to the context's arousal.
   */
  public static EntryTemplate drawEntry(IntroContext ctx, Random rng) {
    List<EntryTemplate> pool = selectPool(ctx, HIGH_ENTRY, MID_ENTRY, LOW_ENTRY);
    return pool.get(rng.nextInt(pool.size()));
  }

  /**
   * Draws one {@link GuitarTemplate} at random from the list appropriate to the context's arousal.
   */
  public static GuitarTemplate drawGuitar(IntroContext ctx, Random rng) {
    List<GuitarTemplate> pool = selectPool(ctx, HIGH_GUITAR, MID_GUITAR, LOW_GUITAR);
    return pool.get(rng.nextInt(pool.size()));
  }

  /**
   * Draws one {@link DrumSubTemplate} at random from the list appropriate to the context's arousal.
   */
  public static DrumSubTemplate drawDrum(IntroContext ctx, Random rng) {
    List<DrumSubTemplate> pool = selectPool(ctx, HIGH_DRUM, MID_DRUM, LOW_DRUM);
    return pool.get(rng.nextInt(pool.size()));
  }

  /**
   * Draws one {@link BassSubTemplate} at random from the list appropriate to the context's arousal.
   */
  public static BassSubTemplate drawBass(IntroContext ctx, Random rng) {
    List<BassSubTemplate> pool = selectPool(ctx, HIGH_BASS, MID_BASS, LOW_BASS);
    return pool.get(rng.nextInt(pool.size()));
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  private static <T> List<T> selectPool(
      IntroContext ctx, List<T> high, List<T> mid, List<T> low) {
    double arousal = ctx.sentiment().arousal();
    if (arousal > 0.75) return high;
    if (arousal >= 0.45) return mid;
    return low;
  }
}
