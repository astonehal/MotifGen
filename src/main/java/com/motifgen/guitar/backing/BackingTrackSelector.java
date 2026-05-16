package com.motifgen.guitar.backing;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates all five {@link HarmonyApproach} values against a sentiment-driven
 * {@link VoicingType} and returns the {@link BackingTrack} with the highest
 * combined score.
 *
 * <p>Voicing is selected once from the {@link SentimentProfile} (not through
 * scoring) to prevent low-complexity voicings (e.g. power chords) from
 * dominating the consonance metric. The five harmony approaches then compete
 * through the combined score formula: {@code 0.5 × consonance + 0.5 × catchiness}.
 */
public final class BackingTrackSelector {

  private static final int DEFAULT_PPQ   = 480;
  private static final int DEFAULT_TEMPO = 120;
  private static final int BEATS_PER_BAR = 4;

  private BackingTrackSelector() {}

  /**
   * Selects the best backing track.
   *
   * <p>The voicing type is derived from the sentiment profile; the five
   * harmony approaches then compete via combined score.
   *
   * @param sentence  the melody sentence
   * @param profile   sentiment profile
   * @param tempoBpm  playback tempo in BPM (used for strum-pattern tempo gating)
   * @return the highest-scoring {@link BackingTrack}
   */
  public static BackingTrack select(Sentence sentence, SentimentProfile profile, int tempoBpm) {
    int ppq = ppqFromSentence(sentence);
    KeySignature key = keyFromSentence(sentence);
    List<Note> melodyNotes = sentence.getAllNotes();
    long sentenceTotalTicks = (long) ppq * BEATS_PER_BAR * sentence.totalBars();

    // One chord change per bar; minimum 4 slots so short sentences still vary.
    int numSlots = Math.max(4, sentence.totalBars());

    // Voicing chosen from sentiment — not through scoring — so that simple
    // voicings (POWER) don't dominate the consonance calculation.
    VoicingType voicing = selectVoicingBySentiment(profile);

    BackingTrack best = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (HarmonyApproach approach : HarmonyApproach.values()) {
      try {
        BackingTrack candidate = evaluate(
            sentence, profile, key, melodyNotes, approach, voicing, ppq,
            tempoBpm, sentenceTotalTicks, numSlots);
        if (candidate.combinedScore() > bestScore) {
          bestScore = candidate.combinedScore();
          best = candidate;
        }
      } catch (Exception ignored) {
        // Skip invalid combinations (e.g. voicing produces no playable notes)
      }
    }

    if (best == null) {
      best = new BackingTrack(List.of(), BackingTrack.GUITAR_PROGRAM, 0.0);
    }
    return best;
  }

  /** Backward-compatible overload defaulting to 120 BPM. */
  public static BackingTrack select(Sentence sentence, SentimentProfile profile) {
    return select(sentence, profile, DEFAULT_TEMPO);
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  /**
   * Maps a sentiment profile to an appropriate voicing type.
   *
   * <ul>
   *   <li>TENSE / ANGRY (high arousal, low valence) → POWER</li>
   *   <li>EXCITED (high arousal, positive valence) → BARRE</li>
   *   <li>HAPPY (medium-high arousal, positive valence) → OPEN</li>
   *   <li>CONTENT / medium energy → TRIAD</li>
   *   <li>RELAXED / CONTENT (low arousal, positive) → JAZZ</li>
   *   <li>SAD / GLOOMY (low valence) → SHELL</li>
   * </ul>
   */
  private static VoicingType selectVoicingBySentiment(SentimentProfile profile) {
    double arousal = profile.arousal();
    double valence = profile.valence();
    if (arousal >= 0.75 && valence < 0.4) return VoicingType.POWER;  // TENSE, ANGRY
    if (arousal >= 0.75)                   return VoicingType.BARRE;  // EXCITED
    if (arousal >= 0.5  && valence >= 0.6) return VoicingType.OPEN;   // HAPPY
    if (arousal >= 0.5)                    return VoicingType.TRIAD;  // medium energy
    if (valence >= 0.5)                    return VoicingType.JAZZ;   // RELAXED, CONTENT
    if (valence < 0.35)                    return VoicingType.SHELL;  // SAD, GLOOMY
    return VoicingType.OPEN;
  }

  private static BackingTrack evaluate(
      Sentence sentence,
      SentimentProfile profile,
      KeySignature key,
      List<Note> melodyNotes,
      HarmonyApproach approach,
      VoicingType voicing,
      int ppq,
      int tempoBpm,
      long sentenceTotalTicks,
      int numSlots) {

    // 1. Generate chord slots aligned to declared phrase boundaries
    List<ChordSlot> slots = approach.generateChords(
        melodyNotes, key, profile, sentenceTotalTicks, numSlots);

    // 2. Plan rhythm density
    RhythmDensityPlan plan = RhythmDensityPlanner.plan(profile, "A", sentence);

    // 3. Build strum pattern using actual playback tempo
    boolean[] strumPat = StrumPattern.forSentiment(profile, voicing, tempoBpm, plan);

    // 4. Voice chords
    List<VoicedChord> voiced = GuitarChordVoicer.voice(slots, voicing, ppq);
    if (voiced.isEmpty()) {
      return new BackingTrack(List.of(), BackingTrack.GUITAR_PROGRAM, 0.0);
    }

    // 5. Score
    double consonance = BackingConsonanceScorer.score(voiced, melodyNotes, ppq);
    double catchiness  = BackingCatchinessScorer.score(sentence, voiced, strumPat, ppq);
    double combined    = 0.5 * consonance + 0.5 * catchiness;

    // 6. Build ChanneledNote list from voiced chords
    List<ChanneledNote> channeledNotes = buildChanneledNotes(voiced, strumPat, ppq);

    return new BackingTrack(channeledNotes, BackingTrack.GUITAR_PROGRAM, combined);
  }

  /**
   * Expands voiced chords + strum pattern into concrete {@link ChanneledNote} events.
   *
   * <p>For each voiced chord the strum pattern is applied relative to the chord's
   * start tick; the pattern loops for the full chord duration (one repeat per bar).
   */
  static List<ChanneledNote> buildChanneledNotes(
      List<VoicedChord> voiced, boolean[] strumPat, int ppq) {

    List<ChanneledNote> result = new ArrayList<>();
    if (voiced.isEmpty() || strumPat.length == 0) return result;

    long slotTicks = (long) ppq / 2; // eighth-note grid (one strum slot = one eighth note)
    int patLen = strumPat.length;
    long patternDurationTicks = (long) patLen * slotTicks; // one bar worth of pattern

    for (VoicedChord vc : voiced) {
      long chordStart = vc.startTick();
      // Use the declared slot duration stored on the chord notes rather than endTick,
      // so the pattern loops for the full chord span regardless of trailing-note offsets.
      long chordDuration = vc.notes().isEmpty()
          ? ppq * 4L
          : vc.notes().get(0).durationTicks();

      // Repeat the strum pattern for the entire chord duration (one repeat per bar).
      for (long barOffset = 0; barOffset < chordDuration; barOffset += patternDurationTicks) {
        for (int slot = 0; slot < patLen; slot++) {
          if (!strumPat[slot]) continue;
          long offsetTick = barOffset + (long) slot * slotTicks;
          if (offsetTick >= chordDuration) break;
          long noteTick = chordStart + offsetTick;
          long noteDuration = Math.min(slotTicks, chordDuration - offsetTick);
          int velocity = slot == 0 ? 80 : 64; // accent beat 1 of every bar

          for (Note n : vc.notes()) {
            if (n.isRest()) continue;
            result.add(new ChanneledNote(
                new Note(n.pitch(), noteTick, noteDuration, velocity),
                BackingTrack.BACKING_CHANNEL));
          }
        }
      }
    }
    return result;
  }

  private static int ppqFromSentence(Sentence sentence) {
    if (!sentence.getPhrases().isEmpty()) {
      return sentence.getPhrases().getFirst().getTicksPerBeat();
    }
    return DEFAULT_PPQ;
  }

  private static KeySignature keyFromSentence(Sentence sentence) {
    String keyName = sentence.getKeyName();
    if (keyName == null || keyName.isBlank()) return KeySignature.major(0);
    String lower = keyName.toLowerCase();
    boolean minor = lower.contains("minor");
    String rootName = keyName.split("\\s+")[0];
    int root = noteNameToMidi(rootName);
    return minor ? KeySignature.minor(root) : KeySignature.major(root);
  }

  private static int noteNameToMidi(String name) {
    return switch (name.toUpperCase()) {
      case "C"  -> 0;
      case "C#", "DB" -> 1;
      case "D"  -> 2;
      case "D#", "EB" -> 3;
      case "E"  -> 4;
      case "F"  -> 5;
      case "F#", "GB" -> 6;
      case "G"  -> 7;
      case "G#", "AB" -> 8;
      case "A"  -> 9;
      case "A#", "BB" -> 10;
      case "B"  -> 11;
      default   -> 0;
    };
  }
}
