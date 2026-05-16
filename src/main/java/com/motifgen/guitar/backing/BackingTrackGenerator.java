package com.motifgen.guitar.backing;

import com.motifgen.model.Sentence;
import com.motifgen.sentiment.SentimentProfile;

/**
 * Facade for generating a rhythm guitar backing track.
 *
 * <p>Delegates to {@link BackingTrackSelector} which evaluates all
 * {@link HarmonyApproach} × {@link VoicingType} combinations and returns
 * the highest-scoring {@link BackingTrack}.
 */
public final class BackingTrackGenerator {

  private BackingTrackGenerator() {}

  /**
   * Generates the best backing track for the given melody sentence and sentiment profile.
   *
   * @param sentence  the melody sentence (supplies melody notes, key, structure)
   * @param profile   sentiment profile (supplies valence and arousal)
   * @param tempoBpm  playback tempo in BPM (used for strum-pattern tempo gating)
   * @return the highest-scoring backing track
   */
  public static BackingTrack generate(Sentence sentence, SentimentProfile profile, int tempoBpm) {
    return BackingTrackSelector.select(sentence, profile, tempoBpm);
  }

  /** Backward-compatible overload defaulting to 120 BPM. */
  public static BackingTrack generate(Sentence sentence, SentimentProfile profile) {
    return BackingTrackSelector.select(sentence, profile);
  }
}
