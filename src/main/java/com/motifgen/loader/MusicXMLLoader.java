package com.motifgen.loader;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a motif from a MusicXML file.
 * Parses note elements with pitch, duration, and rest indicators.
 */
public class MusicXMLLoader {

    private static final int DEFAULT_TICKS_PER_BEAT = 480;

    public static Motif load(File file, int bars) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        doc.getDocumentElement().normalize();

        int divisions = parseDivisions(doc);
        int ticksPerBeat = DEFAULT_TICKS_PER_BEAT;
        double tickScale = (double) ticksPerBeat / divisions;

        int beatsPerBar = parseBeatsPerBar(doc);

        List<Note> notes = new ArrayList<>();
        long currentTick = 0;

        NodeList measureNodes = doc.getElementsByTagName("measure");
        int measureCount = Math.min(measureNodes.getLength(), bars);

        for (int m = 0; m < measureCount; m++) {
            Element measure = (Element) measureNodes.item(m);
            NodeList noteNodes = measure.getElementsByTagName("note");

            for (int n = 0; n < noteNodes.getLength(); n++) {
                Element noteElem = (Element) noteNodes.item(n);

                // Check for chord (simultaneous note)
                boolean isChord = noteElem.getElementsByTagName("chord").getLength() > 0;

                int duration = parseIntElement(noteElem, "duration");
                long durationTicks = Math.round(duration * tickScale);

                boolean isRest = noteElem.getElementsByTagName("rest").getLength() > 0;

                if (!isChord && !isRest) {
                    // Advance tick for non-chord, non-rest previous note handled below
                }

                if (isRest) {
                    notes.add(new Note(Note.REST, currentTick, durationTicks, 0));
                    currentTick += durationTicks;
                } else {
                    int pitch = parsePitch(noteElem);
                    if (pitch >= 0) {
                        int velocity = parseDynamics(noteElem);
                        long startTick = isChord ? (currentTick - durationTicks) : currentTick;
                        if (startTick < 0) startTick = currentTick;
                        notes.add(new Note(pitch, startTick, durationTicks, velocity));
                        if (!isChord) {
                            currentTick += durationTicks;
                        }
                    }
                }
            }
        }

        if (notes.isEmpty()) {
            throw new IllegalArgumentException("No notes found in MusicXML file: " + file.getName());
        }

        return new Motif(notes.stream().filter(n -> !n.isRest()).toList(),
                bars, beatsPerBar, ticksPerBeat);
    }

    private static int parsePitch(Element noteElem) {
        NodeList pitchNodes = noteElem.getElementsByTagName("pitch");
        if (pitchNodes.getLength() == 0) return -1;

        Element pitch = (Element) pitchNodes.item(0);
        String step = getTextContent(pitch, "step");
        int octave = parseIntElement(pitch, "octave");
        int alter = 0;
        NodeList alterNodes = pitch.getElementsByTagName("alter");
        if (alterNodes.getLength() > 0) {
            alter = (int) Double.parseDouble(alterNodes.item(0).getTextContent().trim());
        }

        int basePitch = switch (step) {
            case "C" -> 0; case "D" -> 2; case "E" -> 4; case "F" -> 5;
            case "G" -> 7; case "A" -> 9; case "B" -> 11;
            default -> 0;
        };

        return (octave + 1) * 12 + basePitch + alter;
    }

    private static int parseDivisions(Document doc) {
        NodeList divNodes = doc.getElementsByTagName("divisions");
        if (divNodes.getLength() > 0) {
            return Integer.parseInt(divNodes.item(0).getTextContent().trim());
        }
        return 1;
    }

    private static int parseBeatsPerBar(Document doc) {
        NodeList beatNodes = doc.getElementsByTagName("beats");
        if (beatNodes.getLength() > 0) {
            try {
                return Integer.parseInt(beatNodes.item(0).getTextContent().trim());
            } catch (NumberFormatException e) {
                return 4;
            }
        }
        return 4;
    }

    private static int parseDynamics(Element noteElem) {
        NodeList dynNodes = noteElem.getElementsByTagName("dynamics");
        if (dynNodes.getLength() > 0) {
            try {
                return Integer.parseInt(dynNodes.item(0).getTextContent().trim());
            } catch (NumberFormatException e) {
                return 80;
            }
        }
        return 80;
    }

    private static int parseIntElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            try {
                return Integer.parseInt(nodes.item(0).getTextContent().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }
}
