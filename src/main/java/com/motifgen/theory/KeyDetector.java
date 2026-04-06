package com.motifgen.theory;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;

import java.util.*;

/**
 * Detects the musical key of a motif using the Krumhansl-Schmuckler key-finding algorithm.
 * Uses pitch-class distribution profiles correlated against major and minor key profiles.
 */
public class KeyDetector {

    // Krumhansl-Kessler major key profile
    private static final double[] MAJOR_PROFILE = {
            6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    };

    // Krumhansl-Kessler minor key profile
    private static final double[] MINOR_PROFILE = {
            6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    };

    public record KeyResult(KeySignature key, double correlation) implements Comparable<KeyResult> {
        @Override
        public int compareTo(KeyResult other) {
            return Double.compare(other.correlation, this.correlation); // descending
        }
    }

    /**
     * Detect the key of a motif. Returns all 24 keys ranked by correlation.
     */
    public static List<KeyResult> detectKey(Motif motif) {
        double[] distribution = computePitchClassDistribution(motif);
        List<KeyResult> results = new ArrayList<>();

        for (int root = 0; root < 12; root++) {
            double majorCorr = correlate(distribution, rotateProfile(MAJOR_PROFILE, root));
            results.add(new KeyResult(KeySignature.major(root), majorCorr));

            double minorCorr = correlate(distribution, rotateProfile(MINOR_PROFILE, root));
            results.add(new KeyResult(KeySignature.minor(root), minorCorr));
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Get the best-matching key.
     */
    public static KeySignature bestKey(Motif motif) {
        return detectKey(motif).getFirst().key();
    }

    /**
     * Get top N key candidates.
     */
    public static List<KeyResult> topKeys(Motif motif, int n) {
        List<KeyResult> all = detectKey(motif);
        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Compute weighted pitch-class distribution.
     * Weights notes by duration (longer notes contribute more to perceived key).
     */
    private static double[] computePitchClassDistribution(Motif motif) {
        double[] dist = new double[12];
        double total = 0;

        for (Note note : motif.getNotes()) {
            if (!note.isRest()) {
                double weight = note.durationTicks();
                dist[note.pitchClass()] += weight;
                total += weight;
            }
        }

        // Normalize
        if (total > 0) {
            for (int i = 0; i < 12; i++) {
                dist[i] /= total;
            }
        }
        return dist;
    }

    /**
     * Rotate a key profile to start at a given root.
     */
    private static double[] rotateProfile(double[] profile, int root) {
        double[] rotated = new double[12];
        for (int i = 0; i < 12; i++) {
            rotated[(i + root) % 12] = profile[i];
        }
        return rotated;
    }

    /**
     * Pearson correlation coefficient between two arrays.
     */
    private static double correlate(double[] x, double[] y) {
        double meanX = 0, meanY = 0;
        for (int i = 0; i < 12; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= 12;
        meanY /= 12;

        double sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < 12; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }

        double denom = Math.sqrt(sumX2 * sumY2);
        return denom == 0 ? 0 : sumXY / denom;
    }
}
