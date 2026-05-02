package com.motifgen.generator.catchy;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.sentiment.SentimentProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Applies syncopation to a {@link Motif} proportional to the sentiment's
 * arousal and valence values (Scenario 6).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Compute {@code syncopationLevel = valence * 0.3 + arousal * 0.7}.</li>
 *   <li>Determine {@code shiftCount = floor(syncopationLevel * soundingNotes * 0.4)}.</li>
 *   <li>For each randomly chosen on-beat note, shift its start tick forward by
 *       {@code ticksPerBeat / 2} (an eighth note), shortening duration by the
 *       same amount so the note ends at the same point.</li>
 *   <li>Clamp start tick so it stays strictly inside the phrase, and clamp
 *       duration to a minimum of 1 tick.</li>
 * </ol>
 */
public final class SyncopationApplier {

  private static final double VALENCE_WEIGHT = 0.3;
  private static final double AROUSAL_WEIGHT = 0.7;
  private static final double SHIFT_FRACTION = 0.4;

  private SyncopationApplier() {}

  /**
   * Returns a new motif with syncopation applied according to the profile.
   *
   * @param motif         source motif
   * @param profile       sentiment profile
   * @param ticksPerBeat  ticks per beat (used to compute the eighth-note shift)
   * @param rng           random source for selecting which notes to shift
   * @return new motif with the same note count but shifted start ticks
   */
  public static Motif apply(Motif motif, SentimentProfile profile,
      long ticksPerBeat, Random rng) {
    List<Note> notes = new ArrayList<>(motif.getNotes());
    List<Integer> soundingIdx = new ArrayList<>();
    for (int i = 0; i < notes.size(); i++) {
      if (!notes.get(i).isRest()) soundingIdx.add(i);
    }
    if (soundingIdx.isEmpty()) return motif;

    double level = profile.valence() * VALENCE_WEIGHT + profile.arousal() * AROUSAL_WEIGHT;
    int shiftCount = (int) Math.floor(level * soundingIdx.size() * SHIFT_FRACTION);
    long phraseEnd = motif.totalTicks();
    long shift = Math.max(1L, ticksPerBeat / 2);

    // Shuffle a copy of the sounding indices so we pick random victims
    List<Integer> shuffled = new ArrayList<>(soundingIdx);
    for (int i = shuffled.size() - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      int tmp = shuffled.get(i);
      shuffled.set(i, shuffled.get(j));
      shuffled.set(j, tmp);
    }

    for (int k = 0; k < Math.min(shiftCount, shuffled.size()); k++) {
      int idx = shuffled.get(k);
      Note n = notes.get(idx);
      long newStart = n.startTick() + shift;
      long newDur   = n.durationTicks() - shift;

      // Clamp: newStart must be < phraseEnd; newDur >= 1
      newStart = Math.min(newStart, phraseEnd - 1);
      newDur   = Math.max(1L, newDur);

      notes.set(idx, new Note(n.pitch(), newStart, newDur, n.velocity()));
    }

    return new Motif(notes, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
  }
}
