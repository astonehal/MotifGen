package com.motifgen.scoring;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.List;

/**
 * Exports a Sentence to MusicXML format.
 */
public class MusicXMLExporter {

    private static final int DEFAULT_TEMPO_BPM = 120;

    // Maps pitch class (0-11) to MusicXML step and alter
    private static final String[] STEPS =  {"C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B"};
    private static final int[]    ALTERS = { 0,   1,   0,   1,   0,   0,   1,   0,   1,   0,   1,   0};

    public static void export(Sentence sentence, File outputFile, int tempoBpm) throws Exception {
        List<Note> notes = sentence.getAllNotes();
        Motif firstPhrase = sentence.getPhrases().getFirst();
        int ticksPerBeat = firstPhrase.getTicksPerBeat();
        int beatsPerBar = firstPhrase.getBeatsPerBar();
        int divisions = ticksPerBeat; // MusicXML divisions per quarter note

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element scorePartwise = doc.createElement("score-partwise");
        scorePartwise.setAttribute("version", "4.0");
        doc.appendChild(scorePartwise);

        // Work title
        Element work = appendElement(doc, scorePartwise, "work");
        appendTextElement(doc, work, "work-title",
                "MotifGen: " + sentence.getKeyName() + " (" + sentence.getStructure() + ")");

        // Identification
        Element identification = appendElement(doc, scorePartwise, "identification");
        Element creator = appendTextElement(doc, identification, "creator", "MotifGen");
        creator.setAttribute("type", "software");

        // Part list
        Element partList = appendElement(doc, scorePartwise, "part-list");
        Element scorePart = appendElement(doc, partList, "score-part");
        scorePart.setAttribute("id", "P1");
        appendTextElement(doc, scorePart, "part-name", "Melody");

        // Part with measures
        Element part = appendElement(doc, scorePartwise, "part");
        part.setAttribute("id", "P1");

        int totalBars = sentence.totalBars();
        long ticksPerBar = (long) beatsPerBar * ticksPerBeat;

        for (int bar = 0; bar < totalBars; bar++) {
            Element measure = appendElement(doc, part, "measure");
            measure.setAttribute("number", String.valueOf(bar + 1));

            // Attributes on first measure
            if (bar == 0) {
                Element attributes = appendElement(doc, measure, "attributes");
                appendTextElement(doc, attributes, "divisions", String.valueOf(divisions));

                // Key signature
                Element key = appendElement(doc, attributes, "key");
                KeyInfo keyInfo = parseKeyName(sentence.getKeyName());
                appendTextElement(doc, key, "fifths", String.valueOf(keyInfo.fifths));
                appendTextElement(doc, key, "mode", keyInfo.mode);

                // Time signature
                Element time = appendElement(doc, attributes, "time");
                appendTextElement(doc, time, "beats", String.valueOf(beatsPerBar));
                appendTextElement(doc, time, "beat-type", "4");

                // Clef (treble)
                Element clef = appendElement(doc, attributes, "clef");
                appendTextElement(doc, clef, "sign", "G");
                appendTextElement(doc, clef, "line", "2");

                // Tempo direction
                Element direction = appendElement(doc, measure, "direction");
                direction.setAttribute("placement", "above");
                Element dirType = appendElement(doc, direction, "direction-type");
                Element metronome = appendElement(doc, dirType, "metronome");
                appendTextElement(doc, metronome, "beat-unit", "quarter");
                appendTextElement(doc, metronome, "per-minute", String.valueOf(tempoBpm));
                Element sound = appendElement(doc, direction, "sound");
                sound.setAttribute("tempo", String.valueOf(tempoBpm));
            }

            // Collect notes in this bar
            long barStart = bar * ticksPerBar;
            long barEnd = barStart + ticksPerBar;

            long cursor = barStart;
            for (Note note : notes) {
                if (note.endTick() <= barStart || note.startTick() >= barEnd) continue;

                // Add forward rest if there's a gap before this note
                long noteStart = Math.max(note.startTick(), barStart);
                if (noteStart > cursor) {
                    addRest(doc, measure, noteStart - cursor, divisions);
                }

                if (note.isRest()) {
                    long dur = Math.min(note.endTick(), barEnd) - noteStart;
                    addRest(doc, measure, dur, divisions);
                    cursor = noteStart + dur;
                    continue;
                }

                long effectiveEnd = Math.min(note.endTick(), barEnd);
                long duration = effectiveEnd - noteStart;
                if (duration <= 0) continue;

                Element noteElem = appendElement(doc, measure, "note");

                // Pitch
                Element pitch = appendElement(doc, noteElem, "pitch");
                int pc = note.pitch() % 12;
                appendTextElement(doc, pitch, "step", STEPS[pc]);
                if (ALTERS[pc] != 0) {
                    appendTextElement(doc, pitch, "alter", String.valueOf(ALTERS[pc]));
                }
                int octave = note.pitch() / 12 - 1;
                appendTextElement(doc, pitch, "octave", String.valueOf(octave));

                // Duration (in divisions)
                appendTextElement(doc, noteElem, "duration", String.valueOf(duration));

                // Note type
                String type = ticksToType(duration, divisions);
                if (type != null) {
                    appendTextElement(doc, noteElem, "type", type);
                }

                // Dynamics
                Element dynamics = appendElement(doc, noteElem, "dynamics");
                appendTextElement(doc, dynamics, "other-dynamics",
                        String.valueOf(Math.round(note.velocity() / 127.0 * 100)));

                cursor = effectiveEnd;
            }

            // Fill remaining bar with rest
            if (cursor < barEnd) {
                addRest(doc, measure, barEnd - cursor, divisions);
            }
        }

        // Write
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                "-//Recordare//DTD MusicXML 4.0 Partwise//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                "http://www.musicxml.org/dtds/partwise.dtd");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(outputFile));
    }

    public static void export(Sentence sentence, File outputFile) throws Exception {
        export(sentence, outputFile, DEFAULT_TEMPO_BPM);
    }

    private static void addRest(Document doc, Element measure, long durationTicks, int divisions) {
        Element note = appendElement(doc, measure, "note");
        appendElement(doc, note, "rest");
        appendTextElement(doc, note, "duration", String.valueOf(durationTicks));
        String type = ticksToType(durationTicks, divisions);
        if (type != null) {
            appendTextElement(doc, note, "type", type);
        }
    }

    private static String ticksToType(long ticks, int divisions) {
        // divisions = ticks per quarter note
        double quarters = (double) ticks / divisions;
        if (quarters >= 3.5) return "whole";
        if (quarters >= 1.5) return "half";
        if (quarters >= 0.75) return "quarter";
        if (quarters >= 0.375) return "eighth";
        if (quarters >= 0.1875) return "16th";
        if (quarters >= 0.09) return "32nd";
        return "64th";
    }

    private record KeyInfo(int fifths, String mode) {}

    private static KeyInfo parseKeyName(String keyName) {
        // Key name format: "C major", "A minor", "F# major", etc.
        String mode = keyName.contains("minor") ? "minor" : "major";
        String rootStr = keyName.replace(" major", "").replace(" minor", "").trim();

        // Map root note to fifths on the circle of fifths
        int fifths = switch (rootStr) {
            case "C"  -> 0;
            case "G"  -> 1;
            case "D"  -> 2;
            case "A"  -> 3;
            case "E"  -> 4;
            case "B"  -> 5;
            case "F#" -> 6;
            case "C#" -> 7;
            case "F"  -> -1;
            case "Bb" -> -2;
            case "Eb" -> -3;
            case "Ab" -> -4;
            case "Db" -> -5;
            case "Gb" -> -6;
            case "Cb" -> -7;
            // enharmonic sharps
            case "G#" -> 8; // not standard but handle gracefully
            case "D#" -> 9;
            case "A#" -> 10;
            default   -> 0;
        };

        // For minor keys, the fifths value is relative to the minor key
        // (e.g., A minor = 0 fifths, E minor = 1 fifth, D minor = -1)
        if (mode.equals("minor")) {
            fifths = fifths - 3; // A minor (root A, fifths=3) → 0
        }

        return new KeyInfo(fifths, mode);
    }

    private static Element appendElement(Document doc, Element parent, String name) {
        Element el = doc.createElement(name);
        parent.appendChild(el);
        return el;
    }

    private static Element appendTextElement(Document doc, Element parent, String name, String text) {
        Element el = doc.createElement(name);
        el.setTextContent(text);
        parent.appendChild(el);
        return el;
    }
}
