package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Scores a {@link BassLine} on six equally-weighted sub-dimensions, returning
 * a composite score in [0, 1].
 *
 * <h3>Sub-dimensions</h3>
 * <ol>
 *   <li><b>root_emphasis</b> — fraction of beat-1 notes that are chord roots</li>
 *   <li><b>rhythmic_lock</b> — fraction of notes whose slots match the archetype pattern</li>
 *   <li><b>voice_leading</b> — 1 − avg_semitone_jump / 12</li>
 *   <li><b>register_fit</b>  — fraction of notes in the primary register [28, 43]</li>
 *   <li><b>playability</b>   — 1 − avg_dp_cost / MAX_REASONABLE_COST</li>
 *   <li><b>tonal_consonance</b> — fraction of notes whose pitch class is in the current chord</li>
 * </ol>
 */
public final class BassLineScorer {

  private static final double MAX_REASONABLE_COST = 10.0;
  private static final int    DIMENSIONS          = 6;

  private BassLineScorer() {}

  /**
   * Computes the composite score for a bass line against the given chord slots.
   *
   * @param line  the bass line (notes + archetype)
   * @param slots chord-slot progression
   * @param ppq   ticks per quarter note
   * @return composite score in [0, 1]
   */
  public static double score(BassLine line, List<ChordSlot> slots, int ppq) {
    List<BassNote> notes = line.notes();
    if (notes.isEmpty()) return 0.0;

    double rootEmphasis   = rootEmphasis(notes, slots, ppq);
    double rhythmicLock   = rhythmicLock(notes, line.archetype(), ppq);
    double voiceLeading   = voiceLeading(notes);
    double registerFit    = registerFit(notes);
    double playability    = playability(notes);
    double tonalConsonance = tonalConsonance(notes, slots);

    return (rootEmphasis + rhythmicLock + voiceLeading + registerFit
        + playability + tonalConsonance) / DIMENSIONS;
  }

  // -------------------------------------------------------------------------
  // Sub-dimension implementations
  // -------------------------------------------------------------------------

  /** Fraction of beat-1 positions (slot 0 of each bar) containing a chord root. */
  private static double rootEmphasis(List<BassNote> notes, List<ChordSlot> slots, int ppq) {
    if (slots.isEmpty()) return 0.5;
    long ticksPerBeat = ppq;
    long ticksPerBar  = ppq * 4L;
    int beatOneCount = 0;
    int rootOnBeatOne = 0;

    for (BassNote note : notes) {
      long posInBar = note.startTick() % ticksPerBar;
      if (posInBar < ticksPerBeat) { // on beat 1
        beatOneCount++;
        ChordSlot slot = slotAt(note.startTick(), slots);
        if (slot != null && isRoot(note.midi(), slot)) {
          rootOnBeatOne++;
        }
      }
    }
    return beatOneCount == 0 ? 0.5 : (double) rootOnBeatOne / beatOneCount;
  }

  /**
   * Fraction of notes whose start tick falls on an active archetype pattern slot.
   */
  private static double rhythmicLock(List<BassNote> notes, BassGrooveArchetype archetype, int ppq) {
    boolean[] pattern = BassRhythmPattern.forArchetype(archetype);
    long slotTicks = ppq / 2L; // eighth-note grid
    long barTicks  = slotTicks * pattern.length;

    int matching = 0;
    for (BassNote note : notes) {
      long posInBar = note.startTick() % barTicks;
      int slot = (int) (posInBar / slotTicks);
      if (slot < pattern.length && pattern[slot]) {
        matching++;
      }
    }
    return (double) matching / notes.size();
  }

  /** 1 − average semitone jump / 12 (clamped to [0, 1]). */
  private static double voiceLeading(List<BassNote> notes) {
    if (notes.size() < 2) return 1.0;
    double totalJump = 0.0;
    for (int i = 1; i < notes.size(); i++) {
      totalJump += Math.abs(notes.get(i).midi() - notes.get(i - 1).midi());
    }
    double avgJump = totalJump / (notes.size() - 1);
    return Math.max(0.0, 1.0 - avgJump / 12.0);
  }

  /** Fraction of notes in the primary register [28, 43]. */
  private static double registerFit(List<BassNote> notes) {
    long inPrimary = notes.stream()
        .filter(n -> n.midi() >= BassNote.MIDI_MIN && n.midi() <= BassNote.PRIMARY_MAX)
        .count();
    return (double) inPrimary / notes.size();
  }

  /** 1 − avg_fret_cost / MAX_REASONABLE_COST. Uses fret number as a cost proxy. */
  private static double playability(List<BassNote> notes) {
    if (notes.size() < 2) return 1.0;
    double totalCost = 0.0;
    for (int i = 1; i < notes.size(); i++) {
      int fretShift   = Math.abs(notes.get(i).fret() - notes.get(i - 1).fret());
      int stringCross = Math.abs(notes.get(i).stringIdx() - notes.get(i - 1).stringIdx());
      totalCost += fretShift * 1.0 + stringCross * 0.5;
    }
    double avgCost = totalCost / (notes.size() - 1);
    return Math.max(0.0, 1.0 - avgCost / MAX_REASONABLE_COST);
  }

  /** Fraction of notes whose pitch class appears in the concurrent chord. */
  private static double tonalConsonance(List<BassNote> notes, List<ChordSlot> slots) {
    if (slots.isEmpty()) return 0.5;
    int matching = 0;
    for (BassNote note : notes) {
      ChordSlot slot = slotAt(note.startTick(), slots);
      if (slot == null) continue;
      int pc = note.midi() % 12;
      boolean inChord = slot.pitches().stream().anyMatch(p -> p % 12 == pc);
      if (inChord) matching++;
    }
    return (double) matching / notes.size();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static ChordSlot slotAt(long tick, List<ChordSlot> slots) {
    for (ChordSlot slot : slots) {
      if (tick >= slot.startTick() && tick < slot.startTick() + slot.durationTicks()) {
        return slot;
      }
    }
    return slots.isEmpty() ? null : slots.get(slots.size() - 1);
  }

  private static boolean isRoot(int midi, ChordSlot slot) {
    if (slot.pitches().isEmpty()) return false;
    int lowestPc = slot.pitches().stream().mapToInt(p -> p % 12).min().orElse(-1);
    return midi % 12 == lowestPc;
  }
}
