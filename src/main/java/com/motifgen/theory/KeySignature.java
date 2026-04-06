package com.motifgen.theory;

import java.util.List;

/**
 * Represents a musical key with its scale degrees.
 */
public record KeySignature(String name, int root, boolean minor, int[] scaleDegrees) {

    // Major scale intervals: W W H W W W H
    private static final int[] MAJOR_INTERVALS = {0, 2, 4, 5, 7, 9, 11};
    // Natural minor scale intervals: W H W W H W W
    private static final int[] MINOR_INTERVALS = {0, 2, 3, 5, 7, 8, 10};

    private static final String[] NOTE_NAMES_SHARP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] NOTE_NAMES_FLAT = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    public static KeySignature major(int root) {
        int[] degrees = new int[7];
        for (int i = 0; i < 7; i++) {
            degrees[i] = (root + MAJOR_INTERVALS[i]) % 12;
        }
        String name = noteName(root) + " major";
        return new KeySignature(name, root, false, degrees);
    }

    public static KeySignature minor(int root) {
        int[] degrees = new int[7];
        for (int i = 0; i < 7; i++) {
            degrees[i] = (root + MINOR_INTERVALS[i]) % 12;
        }
        String name = noteName(root) + " minor";
        return new KeySignature(name, root, true, degrees);
    }

    /** Get the relative major/minor key. */
    public KeySignature relative() {
        if (minor) {
            return major((root + 3) % 12);
        } else {
            return minor((root + 9) % 12);
        }
    }

    /** Get the parallel major/minor key. */
    public KeySignature parallel() {
        if (minor) {
            return major(root);
        } else {
            return minor(root);
        }
    }

    /** Get the dominant key (V). */
    public KeySignature dominant() {
        int domRoot = (root + 7) % 12;
        return minor ? minor(domRoot) : major(domRoot);
    }

    /** Get the subdominant key (IV). */
    public KeySignature subdominant() {
        int subRoot = (root + 5) % 12;
        return minor ? minor(subRoot) : major(subRoot);
    }

    /** Check if a pitch class belongs to this key. */
    public boolean containsPitchClass(int pc) {
        pc = ((pc % 12) + 12) % 12;
        for (int degree : scaleDegrees) {
            if (degree == pc) return true;
        }
        return false;
    }

    /** Score how well a set of pitch classes fits this key (0.0 to 1.0). */
    public double fitScore(List<Integer> pitchClasses) {
        if (pitchClasses.isEmpty()) return 0.0;
        long inKey = pitchClasses.stream().filter(this::containsPitchClass).count();
        return (double) inKey / pitchClasses.size();
    }

    /** Interval in semitones from this key's root to another root. */
    public int intervalTo(KeySignature other) {
        return ((other.root - this.root) % 12 + 12) % 12;
    }

    /** Get all 24 major and minor keys. */
    public static List<KeySignature> allKeys() {
        List<KeySignature> keys = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            keys.add(major(i));
            keys.add(minor(i));
        }
        return keys;
    }

    /** Get keys closely related to this one (relative, parallel, dominant, subdominant). */
    public List<KeySignature> relatedKeys() {
        return List.of(this, relative(), parallel(), dominant(), subdominant());
    }

    public static String noteName(int pitchClass) {
        return NOTE_NAMES_SHARP[((pitchClass % 12) + 12) % 12];
    }

    public static String noteNameFlat(int pitchClass) {
        return NOTE_NAMES_FLAT[((pitchClass % 12) + 12) % 12];
    }
}
