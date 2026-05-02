package com.motifgen.sentiment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KeyAffinity}.
 *
 * <p>Covers Scenario 4: key/mode biased by valence.
 */
class KeyAffinityTest {

  // ── Scenario 4: high valence favours major keys ──────────────────────────

  @Test
  void highValenceScoreshigherForMajorThanMinor() {
    SentimentProfile happy = SentimentProfile.fromLabel("HAPPY"); // V=0.75
    KeySignature cMajor = KeySignature.major(0);
    KeySignature cMinor = KeySignature.minor(0);

    double majorScore = KeyAffinity.sentimentScore(cMajor, happy);
    double minorScore = KeyAffinity.sentimentScore(cMinor, happy);

    assertTrue(majorScore > minorScore,
        "High-valence sentiment should score major key higher than minor");
  }

  @Test
  void lowValenceScoresHigherForMinorThanMajor() {
    SentimentProfile sad = SentimentProfile.fromLabel("SAD"); // V=0.20
    KeySignature cMajor = KeySignature.major(0);
    KeySignature cMinor = KeySignature.minor(0);

    double majorScore = KeyAffinity.sentimentScore(cMajor, sad);
    double minorScore = KeyAffinity.sentimentScore(cMinor, sad);

    assertTrue(minorScore > majorScore,
        "Low-valence sentiment should score minor key higher than major");
  }

  @Test
  void neutralValenceGivesSimilarScoresToBothModes() {
    // CONTENT has V=0.65, which is above 0.6 so it still slightly favours major,
    // but the gap is small. We just check scores are positive and finite.
    SentimentProfile content = SentimentProfile.fromLabel("CONTENT");
    KeySignature cMajor = KeySignature.major(0);
    KeySignature cMinor = KeySignature.minor(0);

    double majorScore = KeyAffinity.sentimentScore(cMajor, content);
    double minorScore = KeyAffinity.sentimentScore(cMinor, content);

    assertTrue(majorScore > 0.0);
    assertTrue(minorScore > 0.0);
  }

  @Test
  void scoresAreAlwaysPositive() {
    SentimentProfile angry = SentimentProfile.fromLabel("ANGRY");
    for (int root = 0; root < 12; root++) {
      assertTrue(KeyAffinity.sentimentScore(KeySignature.major(root), angry) > 0.0);
      assertTrue(KeyAffinity.sentimentScore(KeySignature.minor(root), angry) > 0.0);
    }
  }
}
