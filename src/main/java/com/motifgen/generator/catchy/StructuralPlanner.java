package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.Set;

/**
 * Builds a {@link StructuralPlan} for a 16-bar sentence from a seed motif.
 *
 * <p>Four templates are supported: {@code AABA}, {@code ABAB}, {@code ABAC},
 * {@code ABCA}. When a {@link SentimentProfile} is supplied, the climax
 * position and preferred template are influenced by arousal and valence
 * (Scenarios 7 and 8).
 */
public final class StructuralPlanner {

  private static final Set<String> SUPPORTED_TEMPLATES = Set.of("AABA", "ABAB", "ABAC", "ABCA");

  private static final int    DEFAULT_TOTAL_BARS      = 16;
  private static final double CLIMAX_BASE             = 0.45;
  private static final double CLIMAX_AROUSAL_FACTOR   = 0.25;
  private static final double HIGH_AROUSAL_THRESHOLD  = 0.70;
  private static final double LOW_VALENCE_THRESHOLD   = 0.40;
  private static final double HIGH_VALENCE_THRESHOLD  = 0.70;
  private static final double PLAYFUL_AROUSAL_LOW     = 0.40;
  private static final double PLAYFUL_AROUSAL_HIGH    = 0.60;

  // ── Public API ──────────────────────────────────────────────────────────

  /**
   * Builds a plan using the default climax position (0.6 relative), ignoring
   * sentiment. Kept for backward compatibility.
   */
  public StructuralPlan plan(Motif motif, String template, KeySignature key) {
    if (!SUPPORTED_TEMPLATES.contains(template)) {
      throw new IllegalArgumentException(
          "Unsupported template: " + template + " (expected one of " + SUPPORTED_TEMPLATES + ")");
    }
    int sectionCount   = template.length();
    int phraseBars     = DEFAULT_TOTAL_BARS / sectionCount;
    int notesPerPhrase = countSoundingNotes(motif);
    int totalNotes     = notesPerPhrase * sectionCount;
    int climaxPosition = Math.max(0,
        Math.min(totalNotes - 1, (int) Math.round(totalNotes * 0.6)));
    return new StructuralPlan(template, phraseBars, DEFAULT_TOTAL_BARS,
        notesPerPhrase, climaxPosition, key.root());
  }

  /**
   * Builds a sentiment-aware plan. The climax relative position is
   * {@code 0.45 + (arousal * 0.25)}, so high arousal (A=1.0) places the
   * climax at ~70% through the sentence (later half) and low arousal (A=0.0)
   * places it at ~45% (earlier/middle portion).
   */
  public StructuralPlan plan(Motif motif, String template, KeySignature key,
      SentimentProfile profile) {
    if (!SUPPORTED_TEMPLATES.contains(template)) {
      throw new IllegalArgumentException(
          "Unsupported template: " + template + " (expected one of " + SUPPORTED_TEMPLATES + ")");
    }
    int sectionCount    = template.length();
    int phraseBars      = DEFAULT_TOTAL_BARS / sectionCount;
    int notesPerPhrase  = countSoundingNotes(motif);
    int totalNotes      = notesPerPhrase * sectionCount;
    double climaxRelPos = CLIMAX_BASE + (profile.arousal() * CLIMAX_AROUSAL_FACTOR);
    int climaxPosition  = Math.max(0,
        Math.min(totalNotes - 1, (int) Math.round(totalNotes * climaxRelPos)));
    return new StructuralPlan(template, phraseBars, DEFAULT_TOTAL_BARS,
        notesPerPhrase, climaxPosition, key.root());
  }

  /**
   * Returns the template name most aligned with the sentiment (Scenario 7).
   *
   * <ul>
   *   <li>High arousal (&ge; 0.7) → {@code AABA} (building/hook form)</li>
   *   <li>Playful (V &ge; 0.7, A in 0.4–0.6) → {@code ABAB}</li>
   *   <li>Serious (V &le; 0.4) → {@code ABAC}</li>
   *   <li>Otherwise → {@code AABA}</li>
   * </ul>
   *
   * @param profile sentiment profile
   * @return preferred template string
   */
  public String preferredTemplate(SentimentProfile profile) {
    double v = profile.valence();
    double a = profile.arousal();
    if (a >= HIGH_AROUSAL_THRESHOLD) {
      return "AABA";
    }
    if (v >= HIGH_VALENCE_THRESHOLD && a >= PLAYFUL_AROUSAL_LOW && a <= PLAYFUL_AROUSAL_HIGH) {
      return "ABAB";
    }
    if (v <= LOW_VALENCE_THRESHOLD) {
      return "ABAC";
    }
    return "AABA";
  }

  // ── Private helpers ─────────────────────────────────────────────────────

  private static int countSoundingNotes(Motif motif) {
    int n = 0;
    for (Note note : motif.getNotes()) {
      if (!note.isRest()) {
        n++;
      }
    }
    return Math.max(n, 1);
  }
}
