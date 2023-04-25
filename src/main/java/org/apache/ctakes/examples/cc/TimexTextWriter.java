package org.apache.ctakes.examples.cc;

import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.clulab.timenorm.scfg.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Note level event annotations for debugging chemo dictionary lookup
 *
 * @author Eli , chip-nlp
 * @version %I%
 * @since 4/6/2023
 */
final public class TimexTextWriter extends AbstractJCasFileWriter {


    static private final Logger LOGGER = Logger.getLogger( "EventTextWriter" );
    static private final String FILE_EXTENSION = ".txt";

    static private final TimeSpan dummyDCT = TimeSpan.of(-1000, -10, -10);

    @Override
    public void writeFile( final JCas jCas,
                           final String outputDir,
                           final String documentId,
                           final String fileName ) throws IOException {
        final File outputFilePath = new File( outputDir , fileName + "_time_mentions" + FILE_EXTENSION );
        LOGGER.info("Writing " + fileName + FILE_EXTENSION + " to " + outputFilePath.getPath()  +" ...") ;
        try ( Writer writer = new BufferedWriter( new FileWriter( outputFilePath ) ) ) {
            Map<Sentence, Collection<TimeMention>> sent2ColEvents = JCasUtil.indexCovered(
                    jCas,
                    Sentence.class,
                    TimeMention.class
            );

            Map<Sentence, List<TimeMention>> sent2Events = new HashMap<>();
            sent2ColEvents.forEach(
                    (sentence, events) -> sent2Events.put(
                            sentence,
                            events.stream()
                                    .sorted(
                                            Comparator.comparingInt(
                                                    TimeMention::getBegin
                                            )
                                    ).collect(Collectors.toList())
                    )
            );
            List<Sentence> casSentences = JCasUtil.select(jCas, Sentence.class)
                    .stream()
                    .sorted(
                            Comparator.comparingInt(
                                    Sentence :: getBegin
                            )
                    ).collect(Collectors.toList());

            casSentences
                    .stream()
                    .filter(
                            sent2Events::containsKey
                    )
                    .forEach(
                            sentence -> {
                                try {
                                    writeMention(
                                            casSentences.indexOf(sentence) + 1,
                                            sentence,
                                            sent2Events.get(sentence),
                                            writer
                                    );
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                ;
                            }
                    );
        }
        LOGGER.info( "Finished Writing" );
    }


    static public Pair<Integer> getSpan( final Annotation attribute ){
        return new Pair<>(attribute.getBegin(), attribute.getEnd());
    }


    /**
     * Write a sentence from the document text
     *
     * @param container sentence containing the annotation
     * @param TimeMentions annotations
     * @param writer   writer to which pretty text for the sentence should be written
     * @throws IOException if the writer has issues
     */
    static public void writeMention(
            final int sentIndex,
            final Sentence container,
            final List<TimeMention> TimeMentions,
            final Writer writer
    ) throws IOException {

        writer.write(
                String.format(
                        "\nSentence: %d\n\n",
                        sentIndex
                )
        );


        Set<Pair<Integer>> labelToInds = new HashSet<>();

        TimeMentions.forEach(
                TimeMention -> labelToInds.add(getSpan(TimeMention))
        );

        writer.write(
                taggedSentence(
                        container,
                        labelToInds
                )
        );
        writer.write("\n");
    }

    static private String taggedSentence(Sentence sentence, Set<Pair<Integer>> labelToInds){
        StringBuilder out = new StringBuilder();
        String tag = "timex";

        List<Pair<Integer>> orderedInds = labelToInds
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        Pair::getValue1
                                )
                ).collect(
                        Collectors.toList()
                );

        String sentenceText = sentence.getCoveredText().replace("\n", " ");
        int sentenceBegin = sentence.getBegin();
        int previous = 0;

        for (Pair<Integer> indices : orderedInds){
            int localBegin = indices.getValue1() - sentenceBegin;
            int localEnd = indices.getValue2() - sentenceBegin;
            if (previous < localBegin) {
                out.append(sentenceText, previous, localBegin);
            }
            out.append(String.format("<%s>", tag));
            out.append(sentenceText, localBegin, localEnd);
            out.append(String.format("</%s>", tag));
            previous = localEnd;
        }

        out.append(sentenceText, previous, sentenceText.length());

        return out.toString();
    }
}
