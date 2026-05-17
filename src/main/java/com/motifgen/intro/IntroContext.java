package com.motifgen.intro;

import com.motifgen.guitar.backing.DrumGrooveArchetype;
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
 * <p>The {@link #barCount()} is derived from arousal:
 * <ul>
 *   <li>{@code arousal > 0.75} → 2 bars</li>
 *   <li>{@code 0.45 <= arousal <= 0.75} → 3 bars</li>
 *   <li>{@code arousal < 0.45} → 4 bars</li>
 * </ul>
 *
 * @param sentiment      source sentiment (valence + arousal)
 * @param key            key signature for the intro
 * @param archetype      groove archetype string, lower-case (e.g. "driving", "ballad", "folk")
 * @param vampChords     1- or 2-element array of chord root MIDI note numbers for the vamp
 * @param ticksPerBeat   MIDI ticks per quarter-note (PPQ), e.g. 480
 * @param beatsPerBar    time-signature numerator, e.g. 4 for 4/4
 * @param riffScore      pre-computed riff-eligibility score (1 or 3)
 * @param barCount       variable intro length (2, 3, or 4) derived from arousal
 * @param vampTonicMidi  MIDI note number of the vamp tonic, normalised to guitar register [40,76]
 * @param drumArchetype  groove archetype for the drum builder
 */
public record IntroContext(
    SentimentProfile sentiment,
    KeySignature key,
    String archetype,
    int[] vampChords,
    int ticksPerBeat,
    int beatsPerBar,
    int riffScore,
    int barCount,
    int vampTonicMidi,
    DrumGrooveArchetype drumArchetype) {

  /** Archetypes that support riff mode when arousal is also sufficient. */
  private static final Set<String> RIFF_ARCHETYPES = Set.of("driving", "power", "funk");

  /** Arousal threshold above which riff mode is eligible. */
  private static final double RIFF_AROUSAL_THRESHOLD = 0.55;

  /** Arousal threshold above which the high-energy I–I–I–I vamp is used. */
  private static final double HIGH_AROUSAL_VAMP_THRESHOLD = 0.75;

  /** Arousal threshold above which barCount = 2. */
  private static final double HIGH_AROUSAL_BAR_THRESHOLD = 0.75;

  /** Arousal threshold below which barCount = 4. */
  private static final double LOW_AROUSAL_BAR_THRESHOLD = 0.45;

  /** MIDI semitone offset for a perfect fourth (IV chord root above tonic). */
  private static final int FOURTH_OFFSET = 5;

  /**
   * Primary factory: derives vamp, riffScore, barCount, vampTonicMidi, and drumArchetype
   * automatically from the supplied sentiment and archetype.
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
    int bc = computeBarCount(sentiment.arousal());
    int tonicMidi = normaliseToGuitarRegister(key.root());
    DrumGrooveArchetype drumArch = archetypeFromString(arc);
    return new IntroContext(sentiment, key, arc, vamp, ticksPerBeat, beatsPerBar,
        rs, bc, tonicMidi, drumArch);
  }

  /**
   * Extended factory overload that accepts an explicit first chord root (from the backing track)
   * and drum archetype (from sentence analysis) for sentence-aware intro generation.
   *
   * @param sentiment      source sentiment
   * @param key            key signature
   * @param archetype      groove archetype (case-insensitive)
   * @param ticksPerBeat   MIDI PPQ
   * @param beatsPerBar    beats per bar
   * @param firstChordRoot MIDI note of the first chord root in the sentence's backing track
   * @param drumArch       drum groove archetype derived from the sentence's drum track
   * @return fully populated {@link IntroContext}
   */
  public static IntroContext of(
      SentimentProfile sentiment,
      KeySignature key,
      String archetype,
      int ticksPerBeat,
      int beatsPerBar,
      int firstChordRoot,
      DrumGrooveArchetype drumArch) {
    String arc = archetype == null ? "" : archetype.toLowerCase();
    int[] vamp = buildVamp(firstChordRoot, sentiment.arousal());
    int rs = computeRiffScore(arc, sentiment.arousal());
    int bc = computeBarCount(sentiment.arousal());
    int tonicMidi = normaliseToGuitarRegister(firstChordRoot);
    DrumGrooveArchetype resolvedArch = drumArch != null ? drumArch : archetypeFromString(arc);
    return new IntroContext(sentiment, key, arc, vamp, ticksPerBeat, beatsPerBar,
        rs, bc, tonicMidi, resolvedArch);
  }

  /** Convenience overload using 4/4 time and 480 PPQ. */
  public static IntroContext of(SentimentProfile sentiment, KeySignature key, String archetype) {
    return of(sentiment, key, archetype, 480, 4);
  }

  /**
   * Returns the total offset in ticks that the variable-length intro adds before the sentence.
   *
   * @return {@code barCount * beatsPerBar * ticksPerBeat}
   */
  public long offsetTicks() {
    return (long) barCount * beatsPerBar * ticksPerBeat;
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

  private static int computeBarCount(double arousal) {
    if (arousal > HIGH_AROUSAL_BAR_THRESHOLD) return 2;
    if (arousal >= LOW_AROUSAL_BAR_THRESHOLD) return 3;
    return 4;
  }

  /**
   * Maps a lowercase archetype string to a {@link DrumGrooveArchetype}.
   * Defaults to {@link DrumGrooveArchetype#DRIVING} for unknown values.
   */
  static DrumGrooveArchetype archetypeFromString(String arc) {
    return switch (arc == null ? "" : arc) {
      case "driving" -> DrumGrooveArchetype.DRIVING;
      case "folk"    -> DrumGrooveArchetype.FOLK;
      case "ballad"  -> DrumGrooveArchetype.BALLAD;
      case "funk"    -> DrumGrooveArchetype.FUNK;
      case "reggae"  -> DrumGrooveArchetype.REGGAE;
      case "power"   -> DrumGrooveArchetype.POWER;
      default        -> DrumGrooveArchetype.DRIVING;
    };
  }

  /**
   * Transposes a MIDI note number into the guitar register [40, 76] (E2–E5),
   * preserving the original pitch class (midi % 12).
   */
  private static int normaliseToGuitarRegister(int midi) {
    int pitchClass = midi % 12;
    // Start at a mid-register octave: C4=60, so use octave 4 base = 60.
    // Find the nearest note with the same pitch class at or around midi 52.
    int pitch = pitchClass + 48; // octave 3 base (C3=48)
    while (pitch > 76) pitch -= 12;
    while (pitch < 40) pitch += 12;
    return pitch;
  }
}
