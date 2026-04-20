package com.motifgen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A 16-bar musical sentence composed of motifs arranged in a structural pattern.
 * Typical sentence structure: a a' b a'' (each 4 bars) = 16 bars.
 */
public class Sentence {
    private final List<Motif> phrases;
    private final String structure;
    private final String keyName;
    private final double score;

    public Sentence(List<Motif> phrases, String structure, String keyName, double score) {
        this.phrases = Collections.unmodifiableList(new ArrayList<>(phrases));
        this.structure = structure;
        this.keyName = keyName;
        this.score = score;
    }

    public List<Motif> getPhrases() { return phrases; }
    public String getStructure() { return structure; }
    public String getKeyName() { return keyName; }
    public double getScore() { return score; }

    /** Get all notes across all phrases, properly offset in time. */
    public List<Note> getAllNotes() {
        List<Note> all = new ArrayList<>();
        long offset = 0;
        for (Motif phrase : phrases) {
            for (Note n : phrase.getNotes()) {
                all.add(n.withStartTick(n.startTick() + offset));
            }
            offset += phrase.totalTicks();
        }
        return Collections.unmodifiableList(all);
    }

    public int totalBars() {
        return phrases.stream().mapToInt(Motif::getBars).sum();
    }

    public Sentence withScore(double newScore) {
        return new Sentence(phrases, structure, keyName, newScore);
    }

    @Override
    public String toString() {
        return "Sentence[key=%s, structure=%s, bars=%d, score=%.1f]"
                .formatted(keyName, structure, totalBars(), score);
    }
}
