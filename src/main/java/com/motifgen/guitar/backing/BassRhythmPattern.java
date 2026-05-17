package com.motifgen.guitar.backing;

import java.util.Arrays;

/**
 * Static factory returning the 8-slot boolean strum/rest pattern for each
 * {@link BassGrooveArchetype}.
 *
 * <p>Slot indices correspond to eighth-note positions within one 4/4 bar:
 * slot 0 = beat 1, slot 2 = beat 2, slot 4 = beat 3, slot 6 = beat 4.
 *
 * <ul>
 *   <li>DRIVING  = [1,1,1,1,1,1,1,1]</li>
 *   <li>BALLAD   = [1,0,0,0,1,0,0,0]</li>
 *   <li>FOLK     = [1,0,1,0,1,0,0,1]</li>
 *   <li>FUNK     = [1,1,0,1,0,0,1,0]</li>
 *   <li>REGGAE   = [0,0,1,0,0,0,1,0]</li>
 * </ul>
 */
public final class BassRhythmPattern {

  private static final boolean T = true;
  private static final boolean F = false;

  private static final boolean[] DRIVING = {T, T, T, T, T, T, T, T};
  private static final boolean[] BALLAD  = {T, F, F, F, T, F, F, F};
  private static final boolean[] FOLK    = {T, F, T, F, T, F, F, T};
  private static final boolean[] FUNK    = {T, T, F, T, F, F, T, F};
  private static final boolean[] REGGAE  = {F, F, T, F, F, F, T, F};
  private static final boolean[] POWER   = {T, F, F, F, T, F, F, F};

  private BassRhythmPattern() {}

  /**
   * Returns a defensive copy of the 8-slot pattern for the given archetype.
   *
   * @param archetype the bass groove archetype
   * @return copy of the 8-element boolean array
   */
  public static boolean[] forArchetype(BassGrooveArchetype archetype) {
    boolean[] base = switch (archetype) {
      case DRIVING -> DRIVING;
      case BALLAD  -> BALLAD;
      case FOLK    -> FOLK;
      case FUNK    -> FUNK;
      case REGGAE  -> REGGAE;
      case POWER   -> POWER;
    };
    return Arrays.copyOf(base, base.length);
  }
}
