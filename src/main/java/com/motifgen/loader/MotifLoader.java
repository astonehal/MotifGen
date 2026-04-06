package com.motifgen.loader;

import com.motifgen.model.Motif;
import java.io.File;

/**
 * Unified loader that delegates to MIDI or MusicXML loader based on file extension.
 */
public class MotifLoader {

    public static Motif load(String filePath, int bars) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".mid") || name.endsWith(".midi")) {
            return MidiLoader.load(file, bars);
        } else if (name.endsWith(".xml") || name.endsWith(".musicxml") || name.endsWith(".mxl")) {
            return MusicXMLLoader.load(file, bars);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file format. Use .mid, .midi, .xml, or .musicxml: " + name);
        }
    }
}
