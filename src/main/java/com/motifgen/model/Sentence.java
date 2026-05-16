package com.motifgen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A 16-bar musical sentence composed of motifs arranged in a structural pattern.
 * Typical sentence structure: a a' b a'' (each 4 bars) = 16 bars.
 *
 * <p>An optional {@code metadata} map carries arbitrary string key/value annotations
 * (e.g. playability labels) without coupling this model to downstream concerns.
 */
public class Sentence {
    private final List<Motif> phrases;
    private final String structure;
    private final String keyName;
    private final double score;
    private final Map<String, String> metadata;

    public Sentence(List<Motif> phrases, String structure, String keyName, double score) {
        this(phrases, structure, keyName, score, Collections.emptyMap());
    }

    private Sentence(
        List<Motif> phrases,
        String structure,
        String keyName,
        double score,
        Map<String, String> metadata) {
      this.phrases = Collections.unmodifiableList(new ArrayList<>(phrases));
      this.structure = structure;
      this.keyName = keyName;
      this.score = score;
      this.metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public List<Motif> getPhrases() { return phrases; }
    public String getStructure() { return structure; }
    public String getKeyName() { return keyName; }
    public double getScore() { return score; }

    /** Returns an unmodifiable view of this sentence's metadata annotations. */
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Returns the metadata value for {@code key}, or {@code null} if absent.
     *
     * @param key metadata key
     * @return value, or {@code null}
     */
    public String getMetadataValue(String key) {
        return metadata.get(key);
    }

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
        return new Sentence(phrases, structure, keyName, newScore, metadata);
    }

    /**
     * Returns a new {@code Sentence} with the given metadata entry added or replaced.
     * All other fields are preserved.
     *
     * @param key   metadata key (must not be null)
     * @param value metadata value (must not be null)
     * @return new sentence with updated metadata
     */
    public Sentence withMetadata(String key, String value) {
        if (key == null) throw new IllegalArgumentException("metadata key must not be null");
        if (value == null) throw new IllegalArgumentException("metadata value must not be null");
        Map<String, String> updated = new HashMap<>(metadata);
        updated.put(key, value);
        return new Sentence(phrases, structure, keyName, score, updated);
    }

    @Override
    public String toString() {
        return "Sentence[key=%s, structure=%s, bars=%d, score=%.1f]"
                .formatted(keyName, structure, totalBars(), score);
    }
}
