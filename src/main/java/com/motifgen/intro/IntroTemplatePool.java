package com.motifgen.intro;

import java.util.List;
import java.util.Random;

/**
 * Pools of named templates for entry ordering, guitar patterns, and drum sub-patterns.
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
   * @param name        human-readable identifier
   * @param beatOffsets beat offsets (0-indexed within a bar) for chord strums
   * @param riff        if {@code true}, use riff mode instead of chord strums
   * @param octaveShift semitone shift applied to the tonic root in riff mode
   */
  public record GuitarTemplate(String name, int[] beatOffsets, boolean riff, int octaveShift) {}

  /**
   * Describes the drum sub-pattern for the launch-fill bar.
   *
   * @param name           human-readable identifier
   * @param twoBeatsGroove if {@code true} → 2 beats groove + 2 beats fill;
   *                       if {@code false} → 1 beat groove + 3 beats fill
   */
  public record DrumSubTemplate(String name, boolean twoBeatsGroove) {}

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

  private static final List<GuitarTemplate> HIGH_GUITAR = List.of(
      new GuitarTemplate("high_riff_0",    new int[]{0, 2},       true,   0),
      new GuitarTemplate("high_riff_up12", new int[]{0, 2},       true,  12),
      new GuitarTemplate("high_chord_all", new int[]{0, 1, 2, 3}, false,  0),
      new GuitarTemplate("high_chord_alt", new int[]{0, 2, 3},    false,  0)
  );

  private static final List<GuitarTemplate> MID_GUITAR = List.of(
      new GuitarTemplate("mid_chord_12",   new int[]{0, 2},    false,  0),
      new GuitarTemplate("mid_chord_13",   new int[]{0, 1, 3}, false,  0),
      new GuitarTemplate("mid_riff_0",     new int[]{0, 1},    true,   0),
      new GuitarTemplate("mid_riff_dn12",  new int[]{0, 1},    true,  -12)
  );

  private static final List<GuitarTemplate> LOW_GUITAR = List.of(
      new GuitarTemplate("low_chord_1",    new int[]{0},       false,  0),
      new GuitarTemplate("low_chord_13",   new int[]{0, 2},    false,  0),
      new GuitarTemplate("low_riff_0",     new int[]{0},       true,   0),
      new GuitarTemplate("low_chord_2",    new int[]{0, 1},    false,  0)
  );

  // -----------------------------------------------------------------------
  // Drum sub-template pools
  // -----------------------------------------------------------------------

  private static final List<DrumSubTemplate> HIGH_DRUM = List.of(
      new DrumSubTemplate("high_two_beats_A", true),
      new DrumSubTemplate("high_two_beats_B", true),
      new DrumSubTemplate("high_one_beat_A",  false),
      new DrumSubTemplate("high_one_beat_B",  false)
  );

  private static final List<DrumSubTemplate> MID_DRUM = List.of(
      new DrumSubTemplate("mid_two_beats_A", true),
      new DrumSubTemplate("mid_one_beat_A",  false),
      new DrumSubTemplate("mid_two_beats_B", true),
      new DrumSubTemplate("mid_one_beat_B",  false)
  );

  private static final List<DrumSubTemplate> LOW_DRUM = List.of(
      new DrumSubTemplate("low_one_beat_A",  false),
      new DrumSubTemplate("low_two_beats_A", true),
      new DrumSubTemplate("low_one_beat_B",  false),
      new DrumSubTemplate("low_two_beats_B", true)
  );

  // -----------------------------------------------------------------------
  // Draw methods
  // -----------------------------------------------------------------------

  /**
   * Draws one {@link EntryTemplate} at random from the list appropriate to the context's arousal.
   *
   * @param ctx intro context (arousal drives tier selection)
   * @param rng random number generator
   * @return a randomly selected {@link EntryTemplate}
   */
  public static EntryTemplate drawEntry(IntroContext ctx, Random rng) {
    List<EntryTemplate> pool = selectPool(ctx, HIGH_ENTRY, MID_ENTRY, LOW_ENTRY);
    return pool.get(rng.nextInt(pool.size()));
  }

  /**
   * Draws one {@link GuitarTemplate} at random from the list appropriate to the context's arousal.
   *
   * @param ctx intro context
   * @param rng random number generator
   * @return a randomly selected {@link GuitarTemplate}
   */
  public static GuitarTemplate drawGuitar(IntroContext ctx, Random rng) {
    List<GuitarTemplate> pool = selectPool(ctx, HIGH_GUITAR, MID_GUITAR, LOW_GUITAR);
    return pool.get(rng.nextInt(pool.size()));
  }

  /**
   * Draws one {@link DrumSubTemplate} at random from the list appropriate to the context's arousal.
   *
   * @param ctx intro context
   * @param rng random number generator
   * @return a randomly selected {@link DrumSubTemplate}
   */
  public static DrumSubTemplate drawDrum(IntroContext ctx, Random rng) {
    List<DrumSubTemplate> pool = selectPool(ctx, HIGH_DRUM, MID_DRUM, LOW_DRUM);
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
