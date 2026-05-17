package com.motifgen.guitar.backing;

import java.util.Arrays;

/**
 * Static factory returning 16-slot sixteenth-note boolean grids for the
 * kick, snare, and cymbal voices of each {@link DrumGrooveArchetype}, plus
 * General-MIDI note-number constants for drum kit pieces (channel 10).
 *
 * <p>Slot indices map to sixteenth-note positions within one 4/4 bar:
 * 0 = beat 1.00, 4 = beat 2.00, 8 = beat 3.00, 12 = beat 4.00.
 */
public final class DrumPattern {

  /** Number of sixteenth-note slots per 4/4 bar. */
  public static final int SLOTS_PER_BAR = 16;

  // ----- General-MIDI drum-kit note numbers (channel 10) -----
  /** Acoustic bass drum (kick). */
  public static final int KICK         = 36;
  /** Acoustic snare. */
  public static final int SNARE        = 38;
  /** Closed hi-hat. */
  public static final int CLOSED_HIHAT = 42;
  /** Open hi-hat. */
  public static final int OPEN_HIHAT   = 46;
  /** Ride cymbal 1. */
  public static final int RIDE         = 51;
  /** Ride bell. */
  public static final int RIDE_BELL    = 53;
  /** Crash cymbal 1. */
  public static final int CRASH        = 49;
  /** High tom. */
  public static final int HIGH_TOM     = 50;
  /** Mid tom. */
  public static final int MID_TOM      = 47;
  /** Low tom. */
  public static final int LOW_TOM      = 45;

  private static final boolean T = true;
  private static final boolean F = false;

  // Kick on 1 and 3 with archetype-specific extras.
  private static final boolean[] KICK_DRIVING = {
      T,F,F,F,  T,F,F,F,  T,F,F,F,  T,F,F,F};
  private static final boolean[] KICK_BALLAD = {
      T,F,F,F,  F,F,F,F,  T,F,F,F,  F,F,F,F};
  private static final boolean[] KICK_FOLK = {
      T,F,F,F,  F,F,F,F,  T,F,F,F,  F,F,T,F};
  private static final boolean[] KICK_FUNK = {
      T,F,F,T,  F,F,T,F,  T,F,F,F,  F,T,F,F};
  // Reggae one-drop with a soft pickup on beat 1 so half-bar fills still
  // leave a kick on the first half of the bar.
  private static final boolean[] KICK_REGGAE = {
      T,F,F,F,  F,F,F,F,  T,F,F,F,  F,F,F,F};
  private static final boolean[] KICK_POWER = {
      T,F,F,F,  T,F,F,F,  T,F,F,F,  T,F,F,F};

  // Snare on 2 and 4 (back-beat).
  private static final boolean[] SNARE_STANDARD = {
      F,F,F,F,  T,F,F,F,  F,F,F,F,  T,F,F,F};
  private static final boolean[] SNARE_FOLK = {
      F,F,F,F,  T,F,F,F,  F,F,F,F,  T,F,F,F};
  private static final boolean[] SNARE_FUNK = {
      F,F,F,F,  T,F,F,T,  F,F,F,F,  T,F,T,F};
  // Reggae one-drop snare on beat 3 + accent on beat 2 so half-bar fills
  // still leave a snare hit in the first half.
  private static final boolean[] SNARE_REGGAE = {
      F,F,F,F,  T,F,F,F,  T,F,F,F,  F,F,F,F};

  // Cymbal on every eighth note for most archetypes.
  private static final boolean[] CYMBAL_EIGHTHS = {
      T,F,T,F,  T,F,T,F,  T,F,T,F,  T,F,T,F};
  private static final boolean[] CYMBAL_SIXTEENTHS = {
      T,T,T,T,  T,T,T,T,  T,T,T,T,  T,T,T,T};
  private static final boolean[] CYMBAL_BALLAD = {
      T,F,F,F,  T,F,F,F,  T,F,F,F,  T,F,F,F};

  private DrumPattern() {}

  /**
   * Returns a defensive copy of the 16-slot kick grid for {@code archetype}.
   *
   * @param archetype drum groove archetype
   * @return copy of the 16-slot boolean array
   */
  public static boolean[] kickGrid(DrumGrooveArchetype archetype) {
    boolean[] base = switch (archetype) {
      case DRIVING -> KICK_DRIVING;
      case BALLAD  -> KICK_BALLAD;
      case FOLK    -> KICK_FOLK;
      case FUNK    -> KICK_FUNK;
      case REGGAE  -> KICK_REGGAE;
      case POWER   -> KICK_POWER;
    };
    return Arrays.copyOf(base, base.length);
  }

  /**
   * Returns a defensive copy of the 16-slot snare grid for {@code archetype}.
   *
   * @param archetype drum groove archetype
   * @return copy of the 16-slot boolean array
   */
  public static boolean[] snareGrid(DrumGrooveArchetype archetype) {
    boolean[] base = switch (archetype) {
      case DRIVING, BALLAD, POWER -> SNARE_STANDARD;
      case FOLK   -> SNARE_FOLK;
      case FUNK   -> SNARE_FUNK;
      case REGGAE -> SNARE_REGGAE;
    };
    return Arrays.copyOf(base, base.length);
  }

  /**
   * Returns a defensive copy of the 16-slot cymbal grid for {@code archetype}.
   *
   * @param archetype drum groove archetype
   * @return copy of the 16-slot boolean array
   */
  public static boolean[] cymbalGrid(DrumGrooveArchetype archetype) {
    boolean[] base = switch (archetype) {
      case DRIVING, POWER -> CYMBAL_EIGHTHS;
      case FOLK           -> CYMBAL_EIGHTHS;
      case BALLAD         -> CYMBAL_BALLAD;
      case FUNK           -> CYMBAL_SIXTEENTHS;
      case REGGAE         -> CYMBAL_EIGHTHS;
    };
    return Arrays.copyOf(base, base.length);
  }
}
