package org.apache.ctakes.examples.cc;

import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.temporal.ae.TimeAnnotator;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.TimeAnnotation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.clulab.timenorm.scfg.*;
import scala.util.Success;

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
final public class PairwiseOut extends AbstractJCasFileWriter {


    static private final Logger LOGGER = Logger.getLogger( "EventTextWriter" );
    static private final String FILE_EXTENSION = ".txt";
    private static final TemporalExpressionParser normalizer = TemporalExpressionParser.en();
    // for now give a 'non time'
    static private final TimeSpan dummyDCT = TimeSpan.of(2017, 4, 1);

    @Override
    public void writeFile( final JCas jCas,
                           final String outputDir,
                           final String documentId,
                           final String fileName ) throws IOException {
        final File outputFilePath = new File( outputDir , fileName + "_pairwise" + FILE_EXTENSION );
        LOGGER.info("Writing " + fileName + FILE_EXTENSION + " to " + outputFilePath.getPath()  +" ...") ;


        Map<Sentence, Collection<TimeMention>> sent2ColTimes = JCasUtil.indexCovered(
                jCas,
                Sentence.class,
                TimeMention.class
        );

        Map<Sentence, Collection<EventMention>> sent2ColEvents = JCasUtil.indexCovered(
                jCas,
                Sentence.class,
                EventMention.class
        );

        Map<Sentence, List<TimeMention>> sent2Times = new HashMap<>();
        Map<Sentence, List<EventMention>> sent2Events = new HashMap<>();

        sent2ColTimes.forEach(
                ( sentence, timexes ) -> sent2Times.put(
                        sentence,
                        timexes.stream()
                                .sorted(
                                        Comparator.comparingInt(
                                                TimeMention::getBegin
                                        )
                                ).collect( Collectors.toList() )
                )
        );

        sent2ColEvents.forEach(
                ( sentence, events ) -> sent2Events.put(
                        sentence,
                        events.stream()
                                .sorted(
                                        Comparator.comparingInt(
                                                EventMention::getBegin
                                        )
                                ).collect( Collectors.toList() )
                )
        );


        List<Sentence> casSentences = JCasUtil.select(jCas, Sentence.class)
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                Sentence :: getBegin
                        )
                ).collect(Collectors.toList());

        try ( Writer writer = new BufferedWriter( new FileWriter( outputFilePath ) ) ) {
            casSentences
                    .stream()
                    .filter(
                            s -> ( sent2Events.containsKey( s ) || sent2Times.containsKey( s ) )
                    )
                    .forEach(
                            sentence -> {
                                try {
                                    writeMention(
                                            sentence,
                                            sent2Times.getOrDefault( sentence, new ArrayList<>() ),
                                            sent2Events.getOrDefault( sentence, new ArrayList<>() ),
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


    /**
     * Write a sentence from the document text
     *
     * @param container sentence containing the annotation
     * @param timeMentions annotations
     * @param eventMentions annotations
     * @param writer   writer to which pretty text for the sentence should be written
     * @throws IOException if the writer has issues
     */
    static public void writeMention(
            final Sentence container,
            final List<TimeMention> timeMentions,
            final List<EventMention> eventMentions,
            final Writer writer
    ) throws IOException {

        if ( timeMentions.size() > 0 && eventMentions.size() == 0 ){
            timeMentions.forEach(
                    timeMention -> {
                        try {
                            writer.write(
                                    taggedSentence(
                                            container,
                                            timeMention
                                    ) + "\n"
                            );
                        } catch( IOException e ) {
                            throw new RuntimeException( e );
                        }
                    }
            );

            return;
        }

        if ( eventMentions.size() > 0 && timeMentions.size() == 0 ){
            eventMentions.forEach(
                    timeMention -> {
                        try {
                            writer.write(
                                    taggedSentence(
                                            container,
                                            timeMention
                                    ) + "\n"
                            );
                        } catch ( IOException e ) {
                            throw new RuntimeException( e );
                        }
                    }
            );

            return;
        }


        timeMentions
                .stream()
                .flatMap(
                        timeMention -> eventMentions
                                .stream()
                                .map(
                                        eventMention -> new Pair<Annotation>
                                                ( timeMention, eventMention )
                                )
                )
                .forEach(
                        pair -> {
                            try {
                                writer.write(
                                        taggedSentence(
                                                container,
                                                pair
                                        ) + "\n"
                                );
                            } catch ( IOException e ) {
                                throw new RuntimeException( e );
                            }
                        }
                );
    }

    static private String taggedSentence( Sentence sentence,
                                          Pair<Annotation> timeEventPairs ){
        return "";
    }

    static private String taggedSentence( Sentence sentence,
                                          Annotation annotation ){

        StringBuilder out = new StringBuilder();

        String tag;

        String sentenceText = sentence.getCoveredText().replace("\n", " ");
        int sentenceBegin = sentence.getBegin();
        int previous = 0;

        int localBegin = annotation.getBegin() - sentenceBegin;
        int localEnd = annotation.getEnd() - sentenceBegin;
        if (previous < localBegin) {
            out.append(sentenceText, previous, localBegin);
        }

        String unnormalized = annotation.getCoveredText();

        String normalized;
        if ( annotation instanceof TimeMention ) {
            tag = "t";
            try {
                normalized = normalizer.parse(unnormalized, dummyDCT).get().toString();
            } catch (Exception ignored) {
                normalized = unnormalized;
            }
        } else {
            tag = "e";
            normalized = unnormalized;
        }
        out.append(String.format("<%s> ", tag));
        out.append(normalized);
        out.append(sentenceText, localBegin, localEnd);
        out.append(String.format(" </%s>", tag));

        return out.toString();
    }
}
