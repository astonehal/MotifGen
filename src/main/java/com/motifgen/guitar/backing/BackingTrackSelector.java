package com.motifgen.guitar.backing;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates all combinations of {@link HarmonyApproach} (5) ×
 * {@link VoicingType} (6) = 30 candidates and returns the {@link BackingTrack}
 * with the highest combined score.
 *
 * <p>Combined score formula: {@code 0.5 × consonance + 0.5 × catchiness}.
 */
public final class BackingTrackSelector {

  private static final int DEFAULT_PPQ   = 480;
  private static final int DEFAULT_TEMPO = 120;
  private static final int BEATS_PER_BAR = 4;

  private BackingTrackSelector() {}

  /**
   * Selects the best backing track from all approach × voicing combinations.
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

    BackingTrack best = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (HarmonyApproach approach : HarmonyApproach.values()) {
      for (VoicingType voicing : VoicingType.values()) {
        try {
          BackingTrack candidate = evaluate(
              sentence, profile, key, melodyNotes, approach, voicing, ppq,
              tempoBpm, sentenceTotalTicks);
          if (candidate.combinedScore() > bestScore) {
            bestScore = candidate.combinedScore();
            best = candidate;
          }
        } catch (Exception ignored) {
          // Skip invalid combinations (e.g. voicing produces no playable notes)
        }
      }
    }

    // Fallback: shouldn't happen, but guard against all-empty results
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

  private static BackingTrack evaluate(
      Sentence sentence,
      SentimentProfile profile,
      KeySignature key,
      List<Note> melodyNotes,
      HarmonyApproach approach,
      VoicingType voicing,
      int ppq,
      int tempoBpm,
      long sentenceTotalTicks) {

    // 1. Generate chord slots aligned to declared phrase boundaries
    List<ChordSlot> slots = approach.generateChords(melodyNotes, key, profile, sentenceTotalTicks);

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
   * start tick; each active slot produces one set of note events (one per chord
   * tone) with velocity scaled by slot position.
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
    // Extract root note name (first token)
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
