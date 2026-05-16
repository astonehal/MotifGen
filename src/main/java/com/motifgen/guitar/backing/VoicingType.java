package com.motifgen.guitar.backing;

/**
 * Chord voicing styles available to the {@link GuitarChordVoicer}.
 *
 * <ul>
 *   <li>POWER   — root + fifth only (power chord)</li>
 *   <li>OPEN    — open-position triad with open strings where possible</li>
 *   <li>BARRE   — full barre-chord voicing</li>
 *   <li>JAZZ    — extended/altered voicings; shell-voicing filter applied by StrumPattern</li>
 *   <li>TRIAD   — three-note close-position triad</li>
 *   <li>SHELL   — root + third + seventh only</li>
 * </ul>
 */
public enum VoicingType {
  POWER,
  OPEN,
  BARRE,
  JAZZ,
  TRIAD,
  SHELL
}
