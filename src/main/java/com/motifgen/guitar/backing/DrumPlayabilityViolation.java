package com.motifgen.guitar.backing;

import java.util.List;

/**
 * Immutable record describing a single drum playability violation.
 *
 * @param type      the category of violation
 * @param limb      the physical limb involved
 * @param beatTick  absolute tick position where the violation occurs
 * @param drumNotes the drum events involved in the violation
 */
public record DrumPlayabilityViolation(
    Type type,
    Limb limb,
    long beatTick,
    List<DrumEvent> drumNotes) {

  /** Categories of playability violation. */
  public enum Type {
    /** Same limb required to strike twice within its minimum gap. */
    LIMB_COLLISION,
    /** Hihat (closed/open) and ride cymbal required simultaneously on right hand. */
    RIGHT_HAND_CONFLICT,
    /** Two physically impossible simultaneous notes. */
    IMPOSSIBLE_SIMULTANEOUS,
    /** Kick pattern requires foot speed beyond human ability. */
    KICK_TOO_FAST
  }

  /** Physical limbs mapped to GM drum voices. */
  public enum Limb {
    RIGHT_FOOT,
    LEFT_FOOT,
    RIGHT_HAND,
    LEFT_HAND
  }
}
