package com.motifgen.intro;

import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.Set;

/**
 * Immutable value object capturing all inputs required by the intro generation pipeline.
 *
 * <p>The chord vamp is chosen based on arousal:
 * <ul>
 *   <li>High arousal ({@code > 0.75}): I–I–I–I (tonic pedal)</li>
 *   <li>Otherwise: I–IV–I–I</li>
 * </ul>
 *
 * <p>The {@link #riffScore()} is pre-computed from the archetype and arousal: it is 3 when the
 * archetype is in {@code {DRIVING, POWER, FUNK}} and arousal {@code > 0.55}, otherwise 1.
 *
 * @param sentiment     source sentiment (valence + arousal)
 * @param key           key signature for the intro
 * @param archetype     groove archetype string, lower-case (e.g. "driving", "ballad", "folk")
 * @param vampChords    1- or 2-element array of chord root MIDI note numbers for the vamp
 * @param ticksPerBeat  MIDI ticks per quarter-note (PPQ), e.g. 480
 * @param beatsPerBar   time-signature numerator, e.g. 4 for 4/4
 * @param riffScore     pre-computed riff-eligibility score (1 or 3)
 */
public record IntroContext(
    SentimentProfile sentiment,
    KeySignature key,
    String archetype,
    int[] vampChords,
    int ticksPerBeat,
    int beatsPerBar,
    int riffScore) {

  /** Archetypes that support riff mode when arousal is also sufficient. */
  private static final Set<String> RIFF_ARCHETYPES = Set.of("driving", "power", "funk");

  /** Arousal threshold above which riff mode is eligible. */
  private static final double RIFF_AROUSAL_THRESHOLD = 0.55;

  /** Arousal threshold above which the high-energy I–I–I–I vamp is used. */
  private static final double HIGH_AROUSAL_VAMP_THRESHOLD = 0.75;

  /** MIDI semitone offset for a perfect fourth (IV chord root above tonic). */
  private static final int FOURTH_OFFSET = 5;

  /**
   * Primary factory: derives vamp and riffScore automatically from the supplied sentiment and
   * archetype so callers do not need to compute them manually.
   *
   * @param sentiment    source sentiment
   * @param key          key signature
   * @param archetype    groove archetype (case-insensitive)
   * @param ticksPerBeat MIDI PPQ
   * @param beatsPerBar  beats per bar
   * @return fully populated {@link IntroContext}
   */
  public static IntroContext of(
      SentimentProfile sentiment,
      KeySignature key,
      String archetype,
      int ticksPerBeat,
      int beatsPerBar) {
    String arc = archetype == null ? "" : archetype.toLowerCase();
    int[] vamp = buildVamp(key.root(), sentiment.arousal());
    int rs = computeRiffScore(arc, sentiment.arousal());
    return new IntroContext(sentiment, key, arc, vamp, ticksPerBeat, beatsPerBar, rs);
  }

  /** Convenience overload using 4/4 time and 480 PPQ. */
  public static IntroContext of(SentimentProfile sentiment, KeySignature key, String archetype) {
    return of(sentiment, key, archetype, 480, 4);
  }

  /**
   * Returns the total offset in ticks that the 4-bar intro adds before the sentence begins.
   *
   * @return {@code 4 * beatsPerBar * ticksPerBeat}
   */
  public long offsetTicks() {
    return 4L * beatsPerBar * ticksPerBeat;
  }

  // ---------- private helpers ----------

  private static int[] buildVamp(int tonicMidi, double arousal) {
    if (arousal > HIGH_AROUSAL_VAMP_THRESHOLD) {
      return new int[]{tonicMidi};
    }
    // I–IV–I–I: return [tonic, fourth]
    return new int[]{tonicMidi, (tonicMidi + FOURTH_OFFSET) % 12 + (tonicMidi / 12) * 12};
  }

  private static int computeRiffScore(String arc, double arousal) {
    return (RIFF_ARCHETYPES.contains(arc) && arousal > RIFF_AROUSAL_THRESHOLD) ? 3 : 1;
  }
}
