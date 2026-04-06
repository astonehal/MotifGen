package com.motifgen.scoring;

import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import com.motifgen.theory.KeySignature;

import java.util.*;

/**
 * Scores sentence candidates based on multiple musical quality factors:
 * - Consonance: how well notes fit the key
 * - Climax placement: whether there is a clear high point around 2/3 through
 * - Rhythm diversity: variety of note durations
 * - Melodic contour: smooth, singable motion (mostly stepwise)
 * - Range: appropriate melodic range (not too wide, not too narrow)
 * - Cadential strength: does the sentence end convincingly?
 */
public class SentenceScorer {

    private static final double WEIGHT_CONSONANCE = 0.25;
    private static final double WEIGHT_CLIMAX = 0.15;
    private static final double WEIGHT_RHYTHM = 0.15;
    private static final double WEIGHT_CONTOUR = 0.20;
    private static final double WEIGHT_RANGE = 0.10;
    private static final double WEIGHT_CADENCE = 0.15;

    public record ScoreBreakdown(
            double consonance,
            double climax,
            double rhythmDiversity,
            double contour,
            double range,
            double cadence,
            double total
    ) {}

    /**
     * Score a sentence and return it with the computed score.
     */
    public Sentence score(Sentence sentence) {
        ScoreBreakdown breakdown = breakdown(sentence);
        return sentence.withScore(breakdown.total());
    }

    /**
     * Score all candidates and return them sorted best-first.
     */
    public List<Sentence> scoreAndRank(List<Sentence> candidates) {
        return candidates.stream()
                .map(this::score)
                .sorted(Comparator.comparingDouble(Sentence::getScore).reversed())
                .toList();
    }

    /**
     * Get a detailed score breakdown.
     */
    public ScoreBreakdown breakdown(Sentence sentence) {
        List<Note> notes = sentence.getAllNotes();
        if (notes.isEmpty()) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0);
        }

        KeySignature key = parseKey(sentence.getKeyName());

        double consonance = scoreConsonance(notes, key);
        double climax = scoreClimax(notes);
        double rhythm = scoreRhythmDiversity(notes);
        double contour = scoreContour(notes);
        double range = scoreRange(notes);
        double cadence = scoreCadence(notes, key);

        double total = consonance * WEIGHT_CONSONANCE
                + climax * WEIGHT_CLIMAX
                + rhythm * WEIGHT_RHYTHM
                + contour * WEIGHT_CONTOUR
                + range * WEIGHT_RANGE
                + cadence * WEIGHT_CADENCE;

        return new ScoreBreakdown(consonance, climax, rhythm, contour, range, cadence, total);
    }

    /**
     * Consonance: proportion of notes that fit within the key.
     */
    private double scoreConsonance(List<Note> notes, KeySignature key) {
        if (key == null) return 0.5;
        long inKey = notes.stream()
                .filter(n -> !n.isRest() && key.containsPitchClass(n.pitchClass()))
                .count();
        long total = notes.stream().filter(n -> !n.isRest()).count();
        return total == 0 ? 0 : (double) inKey / total;
    }

    /**
     * Climax: reward sentences where the highest pitch occurs around 60-75% through.
     * This follows the classic "golden ratio" climax placement.
     */
    private double scoreClimax(List<Note> notes) {
        List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
        if (pitched.isEmpty()) return 0;

        int maxPitch = pitched.stream().mapToInt(Note::pitch).max().orElse(0);
        long totalDuration = pitched.getLast().endTick();
        if (totalDuration == 0) return 0;

        // Find where the highest pitch occurs
        Note climaxNote = pitched.stream()
                .filter(n -> n.pitch() == maxPitch)
                .findFirst()
                .orElse(pitched.getFirst());

        double position = (double) climaxNote.startTick() / totalDuration;

        // Ideal climax at 0.6-0.75 (golden ratio area)
        double idealCenter = 0.667;
        double distance = Math.abs(position - idealCenter);
        return Math.max(0, 1.0 - distance * 3.0);
    }

    /**
     * Rhythm diversity: variety of note durations (using normalized entropy).
     */
    private double scoreRhythmDiversity(List<Note> notes) {
        Map<Long, Integer> durationCounts = new HashMap<>();
        for (Note n : notes) {
            if (!n.isRest()) {
                // Quantize to nearest 16th note equivalent
                long quantized = (n.durationTicks() / 60) * 60;
                durationCounts.merge(quantized, 1, Integer::sum);
            }
        }

        if (durationCounts.size() <= 1) return 0.1;

        // Shannon entropy normalized to [0, 1]
        double total = durationCounts.values().stream().mapToInt(Integer::intValue).sum();
        double entropy = 0;
        for (int count : durationCounts.values()) {
            double p = count / total;
            if (p > 0) entropy -= p * Math.log(p);
        }

        double maxEntropy = Math.log(durationCounts.size());
        return maxEntropy > 0 ? entropy / maxEntropy : 0;
    }

    /**
     * Melodic contour: reward stepwise motion, penalize large leaps.
     * Ideal melody is ~70% stepwise, ~25% small leaps, ~5% large leaps.
     */
    private double scoreContour(List<Note> notes) {
        List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
        if (pitched.size() < 2) return 0.5;

        int steps = 0;     // intervals <= 2 semitones
        int smallLeaps = 0; // intervals 3-5 semitones
        int largeLeaps = 0; // intervals > 5 semitones

        for (int i = 1; i < pitched.size(); i++) {
            int interval = Math.abs(pitched.get(i).pitch() - pitched.get(i - 1).pitch());
            if (interval <= 2) steps++;
            else if (interval <= 5) smallLeaps++;
            else largeLeaps++;
        }

        int total = pitched.size() - 1;
        double stepRatio = (double) steps / total;
        double smallLeapRatio = (double) smallLeaps / total;
        double largeLeapRatio = (double) largeLeaps / total;

        // Score based on proximity to ideal ratios
        double score = 1.0;
        score -= Math.abs(stepRatio - 0.65) * 0.5;
        score -= Math.abs(smallLeapRatio - 0.25) * 0.3;
        score -= largeLeapRatio * 0.8; // penalize large leaps heavily

        return Math.max(0, Math.min(1.0, score));
    }

    /**
     * Range: reward an appropriate melodic range (roughly an octave to octave+fifth).
     */
    private double scoreRange(List<Note> notes) {
        List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
        if (pitched.isEmpty()) return 0;

        int min = pitched.stream().mapToInt(Note::pitch).min().orElse(60);
        int max = pitched.stream().mapToInt(Note::pitch).max().orElse(72);
        int range = max - min;

        // Ideal range: 10-19 semitones (roughly an octave to an octave+fifth)
        if (range >= 10 && range <= 19) return 1.0;
        if (range >= 7 && range < 10) return 0.7;
        if (range > 19 && range <= 24) return 0.7;
        if (range < 7) return 0.3;
        return 0.3; // very wide range
    }

    /**
     * Cadential strength: does the sentence end on the tonic or dominant?
     */
    private double scoreCadence(List<Note> notes, KeySignature key) {
        if (key == null || notes.isEmpty()) return 0.5;

        List<Note> pitched = notes.stream().filter(n -> !n.isRest()).toList();
        if (pitched.isEmpty()) return 0.5;

        Note lastNote = pitched.getLast();
        int lastPc = lastNote.pitchClass();

        // Perfect cadence: ends on tonic
        if (lastPc == key.root()) return 1.0;
        // Imperfect cadence: ends on dominant
        if (lastPc == (key.root() + 7) % 12) return 0.7;
        // Ends on mediant
        if (lastPc == (key.root() + (key.minor() ? 3 : 4)) % 12) return 0.5;
        // Anything else
        return 0.2;

    }

    private KeySignature parseKey(String keyName) {
        if (keyName == null || keyName.isEmpty()) return null;

        String[] parts = keyName.split(" ");
        if (parts.length < 2) return null;

        int root = noteNameToPC(parts[0]);
        if (root < 0) return null;

        boolean isMinor = parts[1].equalsIgnoreCase("minor");
        return isMinor ? KeySignature.minor(root) : KeySignature.major(root);
    }

    private int noteNameToPC(String name) {
        return switch (name.toUpperCase()) {
            case "C" -> 0; case "C#", "DB" -> 1; case "D" -> 2; case "D#", "EB" -> 3;
            case "E" -> 4; case "F" -> 5; case "F#", "GB" -> 6; case "G" -> 7;
            case "G#", "AB" -> 8; case "A" -> 9; case "A#", "BB" -> 10; case "B" -> 11;
            default -> -1;
        };
    }
}
