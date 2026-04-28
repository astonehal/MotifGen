package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Maps each character of a structural template to a {@link MotifTransformer}
 * operation, producing a {@link SeededPhrase} per phrase position.
 *
 * <p>{@code 'A'} sections are always identity copies of the seed motif and are
 * marked immutable. {@code 'B'} sections draw from a contrast set
 * (5th up, 4th down, inversion). {@code 'C'} sections draw from a different
 * contrast set (retrograde, 3rd up, 3rd down).
 *
 * <p>When a {@link SentimentProfile} is provided, the diatonic step size used
 * for B/C transforms is biased by arousal (Scenario 5), and
 * {@link SyncopationApplier} is invoked on every non-A phrase (Scenario 6).
 */
public final class PhraseSeeder {

  /** A seeded phrase plus a flag the orchestrator uses to skip refinement. */
  public record SeededPhrase(Motif phrase, boolean immutable) {}

  private enum BTransform { TRANSPOSE_FIFTH_UP, TRANSPOSE_FOURTH_DOWN, INVERT }
  private enum CTransform { RETROGRADE, TRANSPOSE_THIRD_UP, TRANSPOSE_THIRD_DOWN }

  private static final int DEFAULT_STEP_SIZE = 1;

  private final Random rng;
  private final MotifTransformer transformer = new MotifTransformer();
  private final SentimentProfile profile;

  /** Backward-compatible constructor — no sentiment influence. */
  public PhraseSeeder(long seed) {
    this.rng     = new Random(seed);
    this.profile = null;
  }

  /**
   * Sentiment-aware constructor. Arousal scales the diatonic step size and
   * triggers {@link SyncopationApplier} on non-A phrases.
   */
  public PhraseSeeder(long seed, SentimentProfile profile) {
    this.rng     = new Random(seed);
    this.profile = profile;
  }

  public SeededPhrase seed(char role, boolean isFinalPhrase, Motif baseMotif,
      KeySignature key) {
    Motif phrase = switch (role) {
      case 'A' -> transformer.identity(baseMotif);
      case 'B' -> applyB(baseMotif, key);
      case 'C' -> applyC(baseMotif, key);
      default  -> transformer.identity(baseMotif);
    };

    boolean immutable = role == 'A';

    if (!immutable && profile != null) {
      long ticksPerBeat = baseMotif.getTicksPerBeat();
      phrase = SyncopationApplier.apply(phrase, profile, ticksPerBeat, rng);
    }

    if (isFinalPhrase && !immutable) {
      phrase = forceLastNoteToTonic(phrase, key);
    }

    return new SeededPhrase(phrase, immutable);
  }

  /** Arousal-biased step size: {@code 1 + (int)(arousal * 3)}. */
  private int stepSize() {
    if (profile == null) return DEFAULT_STEP_SIZE;
    return 1 + (int) (profile.arousal() * 3);
  }

  private Motif applyB(Motif motif, KeySignature key) {
    BTransform choice = BTransform.values()[rng.nextInt(BTransform.values().length)];
    int step = stepSize();
    return switch (choice) {
      case TRANSPOSE_FIFTH_UP    -> transformer.diatonicTranspose(motif,  step + 3, key);
      case TRANSPOSE_FOURTH_DOWN -> transformer.diatonicTranspose(motif, -(step + 2), key);
      case INVERT                -> transformer.invert(motif, firstSoundingPitch(motif));
    };
  }

  private Motif applyC(Motif motif, KeySignature key) {
    CTransform choice = CTransform.values()[rng.nextInt(CTransform.values().length)];
    int step = stepSize();
    return switch (choice) {
      case RETROGRADE            -> transformer.retrograde(motif);
      case TRANSPOSE_THIRD_UP    -> transformer.diatonicTranspose(motif,  step + 1, key);
      case TRANSPOSE_THIRD_DOWN  -> transformer.diatonicTranspose(motif, -(step + 1), key);
    };
  }

  private static Motif forceLastNoteToTonic(Motif phrase, KeySignature key) {
    List<Note> notes = new ArrayList<>(phrase.getNotes());
    int lastIdx = -1;
    for (int i = notes.size() - 1; i >= 0; i--) {
      if (!notes.get(i).isRest()) {
        lastIdx = i;
        break;
      }
    }
    if (lastIdx < 0) return phrase;

    Note last = notes.get(lastIdx);
    int newPitch = nearestPitchOfClass(last.pitch(), key.root());
    notes.set(lastIdx, new Note(newPitch, last.startTick(),
        last.durationTicks(), last.velocity()));
    return new Motif(notes, phrase.getBars(), phrase.getBeatsPerBar(),
        phrase.getTicksPerBeat());
  }

  private static int nearestPitchOfClass(int anchor, int targetPc) {
    int octaveStart = (anchor / 12) * 12;
    int candidate = octaveStart + ((targetPc % 12) + 12) % 12;
    int up = candidate >= anchor ? candidate : candidate + 12;
    int down = candidate <= anchor ? candidate : candidate - 12;
    int chosen = (Math.abs(up - anchor) <= Math.abs(anchor - down)) ? up : down;
    return Math.max(0, Math.min(127, chosen));
  }

  private static int firstSoundingPitch(Motif motif) {
    for (Note n : motif.getNotes()) {
      if (!n.isRest()) return n.pitch();
    }
    return 60;
  }
}
