package com.motifgen.intro;

import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;

import java.util.List;

/**
 * Immutable record holding all generated intro content for a single candidate.
 *
 * <p>The {@link #offsetTicks()} value equals {@code 4 * beatsPerBar * ticksPerBeat} and must be
 * added to every sentence event tick when prepending this intro to a MIDI or MusicXML export.
 *
 * @param guitarEvents  guitar notes for the 4-bar intro (channel 1, program 25)
 * @param bassEvents    bass notes for the 4-bar intro (channel 2, program 34)
 * @param drumEvents    drum hits for the 4-bar intro (channel 9, GM percussion)
 * @param score         quality score in [0.0, 100.0] assigned by {@link IntroScorer}
 * @param offsetTicks   tick offset to prepend intro before the sentence
 */
public record IntroTrack(
    List<ChanneledNote> guitarEvents,
    List<ChanneledNote> bassEvents,
    List<DrumEvent> drumEvents,
    double score,
    long offsetTicks) {

  /**
   * Convenience factory that computes the offset from context rather than requiring callers to
   * pass it explicitly.
   *
   * @param guitarEvents guitar notes
   * @param bassEvents   bass notes
   * @param drumEvents   drum hits
   * @param score        scorer output
   * @param ctx          intro context (used for offset computation)
   * @return populated {@link IntroTrack}
   */
  public static IntroTrack of(
      List<ChanneledNote> guitarEvents,
      List<ChanneledNote> bassEvents,
      List<DrumEvent> drumEvents,
      double score,
      IntroContext ctx) {
    return new IntroTrack(guitarEvents, bassEvents, drumEvents, score, ctx.offsetTicks());
  }
}
