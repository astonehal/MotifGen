package com.motifgen.guitar.backing;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade that generates the best bass guitar track by evaluating 5 candidates.
 *
 * <h3>Candidate matrix (5 combinations)</h3>
 * <ol>
 *   <li>offset=0,   style=NONE</li>
 *   <li>offset=+12, style=DIATONIC</li>
 *   <li>offset=-12, style=CHROMATIC</li>
 *   <li>offset=0,   style=CHROMATIC</li>
 *   <li>offset=+12, style=NONE</li>
 * </ol>
 *
 * <p>Each candidate is scored by {@link BassLineScorer}; the highest-scoring
 * line is converted to a {@link BassTrack} on MIDI channel 2 (0-indexed),
 * program 34.
 */
public final class BassTrackGenerator {

  private static final int[] OFFSETS = {0, 12, -12, 0, 12};
  private static final BassVoiceLeading.ApproachStyle[] STYLES = {
      BassVoiceLeading.ApproachStyle.NONE,
      BassVoiceLeading.ApproachStyle.DIATONIC,
      BassVoiceLeading.ApproachStyle.CHROMATIC,
      BassVoiceLeading.ApproachStyle.CHROMATIC,
      BassVoiceLeading.ApproachStyle.NONE
  };

  private BassTrackGenerator() {}

  /**
   * Generates the best bass track for the given chord progression using the
   * supplied groove archetype.
   *
   * @param slots     chord-slot progression
   * @param ppq       ticks per quarter note
   * @param archetype bass groove archetype
   * @return the highest-scoring {@link BassTrack}
   */
  public static BassTrack generate(List<ChordSlot> slots, int ppq, BassGrooveArchetype archetype) {
    BassLine best = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (int i = 0; i < 5; i++) {
      try {
        BassLine candidate = buildCandidate(slots, ppq, archetype, OFFSETS[i], STYLES[i]);
        double s = BassLineScorer.score(candidate, slots, ppq);
        BassLine scored = new BassLine(candidate.notes(), s, archetype);
        if (s > bestScore) {
          bestScore = s;
          best = scored;
        }
      } catch (Exception ignored) {
        // Skip invalid candidates
      }
    }

    if (best == null || best.notes().isEmpty()) {
      return new BassTrack(List.of(), BassTrack.BASS_PROGRAM, 0.0);
    }
    return toTrack(best);
  }

  /**
   * Generates the best bass track using a default DRIVING archetype.
   *
   * @param slots chord-slot progression
   * @param ppq   ticks per quarter note
   * @return the highest-scoring {@link BassTrack}
   */
  public static BassTrack generate(List<ChordSlot> slots, int ppq) {
    return generate(slots, ppq, BassGrooveArchetype.DRIVING);
  }

  /**
   * Convenience facade: derives chord slots from the sentence, selects the
   * groove archetype from the sentiment profile, and returns the best bass track.
   *
   * <p>Chord slots are derived from the melody notes using
   * {@link HarmonyApproach#FUNCTIONAL_DIATONIC}; one slot per bar.
   * The groove archetype is chosen by mapping the sentiment's strum archetype
   * via {@link BassGrooveArchetype#fromStrumArchetype}.
   *
   * @param sentence  the melody sentence (supplies notes, key, structure, ppq)
   * @param profile   sentiment profile (drives groove archetype selection)
   * @param tempoBpm  playback tempo (passed to strum archetype selection)
   * @return the highest-scoring {@link BassTrack}
   */
  public static BassTrack generate(Sentence sentence, SentimentProfile profile, int tempoBpm) {
    int ppq = sentence.getPhrases().isEmpty()
        ? 480
        : sentence.getPhrases().getFirst().getTicksPerBeat();

    KeySignature key = keyFromSentence(sentence);
    long totalTicks = (long) ppq * 4 * sentence.totalBars();
    int numSlots = Math.max(4, sentence.totalBars());

    List<ChordSlot> slots = HarmonyApproach.FUNCTIONAL_DIATONIC.generateChords(
        sentence.getAllNotes(), key, profile, totalTicks, numSlots);

    StrumPattern.Archetype strumArchetype = pickStrumArchetype(profile);
    BassGrooveArchetype archetype = BassGrooveArchetype.fromStrumArchetype(strumArchetype);

    return generate(slots, ppq, archetype);
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Derives a {@link StrumPattern.Archetype} from the sentiment profile using
   * the same arousal/valence thresholds as {@link StrumPattern}'s internal
   * {@code pickArchetype} (minus voicing-type overrides, which don't apply here).
   */
  private static StrumPattern.Archetype pickStrumArchetype(SentimentProfile profile) {
    double arousal = profile.arousal();
    double valence = profile.valence();
    if (arousal > 0.75) return valence > 0.5 ? StrumPattern.Archetype.DRIVING : StrumPattern.Archetype.FUNK;
    if (arousal > 0.5)  return valence > 0.5 ? StrumPattern.Archetype.FOLK    : StrumPattern.Archetype.REGGAE;
    return StrumPattern.Archetype.BALLAD;
  }

  private static KeySignature keyFromSentence(Sentence sentence) {
    String keyName = sentence.getKeyName();
    if (keyName == null || keyName.isBlank()) return KeySignature.major(0);
    boolean minor = keyName.toLowerCase().contains("minor");
    String rootName = keyName.split("\\s+")[0];
    int root = noteNameToMidi(rootName);
    return minor ? KeySignature.minor(root) : KeySignature.major(root);
  }

  private static int noteNameToMidi(String name) {
    return switch (name.toUpperCase()) {
      case "C"        -> 0;
      case "C#", "DB" -> 1;
      case "D"        -> 2;
      case "D#", "EB" -> 3;
      case "E"        -> 4;
      case "F"        -> 5;
      case "F#", "GB" -> 6;
      case "G"        -> 7;
      case "G#", "AB" -> 8;
      case "A"        -> 9;
      case "A#", "BB" -> 10;
      case "B"        -> 11;
      default         -> 0;
    };
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private static BassLine buildCandidate(
      List<ChordSlot> slots,
      int ppq,
      BassGrooveArchetype archetype,
      int octaveOffset,
      BassVoiceLeading.ApproachStyle style) {

    // 1. Harmonic skeleton
    List<BassNote> skeleton = BassHarmonicSkeleton.derive(slots, ppq, octaveOffset);

    // 2. Apply rhythmic elaboration (expand each chord slot into pattern beats)
    List<BassNote> rhythmic = applyRhythm(skeleton, archetype, ppq);

    // 3. Voice-leading approach notes
    List<BassNote> voiced = BassVoiceLeading.apply(rhythmic, slots, ppq, style);

    // 4. Playability DP
    List<BassNote> optimised = BassPlayabilityOptimiser.optimise(voiced);

    return new BassLine(optimised, 0.0, archetype);
  }

  /**
   * Expands skeleton notes (one per chord slot) into per-beat notes
   * following the archetype's rhythm pattern.
   */
  private static List<BassNote> applyRhythm(
      List<BassNote> skeleton, BassGrooveArchetype archetype, int ppq) {

    boolean[] pattern = BassRhythmPattern.forArchetype(archetype);
    long slotTicks = ppq / 2L; // eighth-note grid
    long barTicks  = slotTicks * pattern.length;

    List<BassNote> result = new ArrayList<>();
    for (BassNote skel : skeleton) {
      long chordStart    = skel.startTick();
      long chordDuration = skel.durationTicks();

      for (long barOffset = 0; barOffset < chordDuration; barOffset += barTicks) {
        for (int slot = 0; slot < pattern.length; slot++) {
          if (!pattern[slot]) continue;
          long offsetTick = barOffset + slot * slotTicks;
          if (offsetTick >= chordDuration) break;
          long noteTick     = chordStart + offsetTick;
          long noteDuration = Math.min(slotTicks, chordDuration - offsetTick);
          int velocity      = slot == 0 ? 90 : 75;
          result.add(new BassNote(skel.midi(), noteTick, noteDuration, velocity, 0, 0));
        }
      }
    }
    return result.isEmpty() ? skeleton : result;
  }

  /** Converts a scored BassLine into a BassTrack with channeled notes. */
  private static BassTrack toTrack(BassLine line) {
    List<ChanneledNote> channeled = new ArrayList<>();
    for (BassNote bn : line.notes()) {
      Note note = new Note(bn.midi(), bn.startTick(), bn.durationTicks(), bn.velocity());
      channeled.add(new ChanneledNote(note, BassTrack.BASS_CHANNEL));
    }
    return new BassTrack(channeled, BassTrack.BASS_PROGRAM, line.score());
  }
}
