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
   * Generates the best backing track for the given melody sentence and
   * sentiment profile.
   *
   * @param sentence the melody sentence (supplies melody notes, key, structure)
   * @param profile  sentiment profile (supplies valence and arousal)
   * @return the highest-scoring backing track
   */
  public static BackingTrack generate(Sentence sentence, SentimentProfile profile) {
    return BackingTrackSelector.select(sentence, profile);
  }
}
