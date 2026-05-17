package com.motifgen.intro;

import java.util.List;

/**
 * Strategy interface for building one instrument's events across a 4-bar intro.
 *
 * <p>Each implementation is responsible for respecting the {@code entryBar} constraint: no events
 * should be produced before the given 1-indexed bar number.
 *
 * @param <T> the event type produced (e.g. {@link com.motifgen.guitar.backing.ChanneledNote} or
 *            {@link com.motifgen.guitar.backing.DrumEvent})
 */
public interface IntroInstrumentBuilder<T> {

  /**
   * Builds the list of events for this instrument's contribution to the 4-bar intro.
   *
   * @param ctx      intro context (sentiment, key, archetype, vamp, timing)
   * @param entryBar 1-indexed bar at which this instrument first plays (1–4)
   * @return ordered list of events; never {@code null}, may be empty if {@code entryBar > 4}
   */
  List<T> build(IntroContext ctx, int entryBar);
}
