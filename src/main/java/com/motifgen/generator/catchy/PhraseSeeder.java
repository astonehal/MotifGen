package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
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
 * contrast set (retrograde, 3rd up, 3rd down). The choice within each set is
 * deterministic per seed so a given run produces a stable fleet.
 *
 * <p>If the phrase being seeded is the final phrase of the sentence and is a
 * {@code 'B'} or {@code 'C'} role, the last sounding note is forced onto the
 * key's tonic pitch class to preserve the existing tonic-resolution guarantee.
 */
public final class PhraseSeeder {

  /** A seeded phrase plus a flag the orchestrator uses to skip refinement. */
  public record SeededPhrase(Motif phrase, boolean immutable) {}

  private enum BTransform { TRANSPOSE_FIFTH_UP, TRANSPOSE_FOURTH_DOWN, INVERT }
  private enum CTransform { RETROGRADE, TRANSPOSE_THIRD_UP, TRANSPOSE_THIRD_DOWN }

  private final Random rng;
  private final MotifTransformer transformer = new MotifTransformer();

  public PhraseSeeder(long seed) {
    this.rng = new Random(seed);
  }

  public SeededPhrase seed(char role, boolean isFinalPhrase, Motif baseMotif,
      KeySignature key) {
    Motif phrase = switch (role) {
      case 'A' -> transformer.identity(baseMotif);
      case 'B' -> applyB(baseMotif, key);
      case 'C' -> applyC(baseMotif, key);
      default -> transformer.identity(baseMotif);
    };

    boolean immutable = role == 'A';

    if (isFinalPhrase && !immutable) {
      phrase = forceLastNoteToTonic(phrase, key);
    }

    return new SeededPhrase(phrase, immutable);
  }

  private Motif applyB(Motif motif, KeySignature key) {
    BTransform choice = BTransform.values()[rng.nextInt(BTransform.values().length)];
    return switch (choice) {
      case TRANSPOSE_FIFTH_UP -> transformer.diatonicTranspose(motif, 4, key);
      case TRANSPOSE_FOURTH_DOWN -> transformer.diatonicTranspose(motif, -3, key);
      case INVERT -> transformer.invert(motif, firstSoundingPitch(motif));
    };
  }

  private Motif applyC(Motif motif, KeySignature key) {
    CTransform choice = CTransform.values()[rng.nextInt(CTransform.values().length)];
    return switch (choice) {
      case RETROGRADE -> transformer.retrograde(motif);
      case TRANSPOSE_THIRD_UP -> transformer.diatonicTranspose(motif, 2, key);
      case TRANSPOSE_THIRD_DOWN -> transformer.diatonicTranspose(motif, -2, key);
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
