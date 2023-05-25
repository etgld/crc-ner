package org.apache.ctakes.examples.cc;

import org.apache.commons.io.FilenameUtils;
import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.clulab.timenorm.scfg.TemporalExpressionParser;
import org.clulab.timenorm.scfg.TimeSpan;

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
        final File outputFilePath = new File( outputDir , "ID107_" + FilenameUtils.getBaseName(fileName) + FILE_EXTENSION );
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
                                          Pair<Annotation> timeEventPair ){
        StringBuilder out = new StringBuilder();

        String tag;

        String sentenceText = sentence.getCoveredText().replace("\n", " ");
        int sentenceBegin = sentence.getBegin();
        int previous = 0;

        TimeMention timeMention = (TimeMention) timeEventPair.getValue1();
        EventMention eventMention = (EventMention) timeEventPair.getValue2();

        int firstBegin, firstEnd, secondBegin, secondEnd;
        String firstText, secondText, firstTag, secondTag;

        if ( timeMention.getBegin() < eventMention.getBegin() ) {
            firstBegin = timeMention.getBegin() - sentenceBegin;
            firstEnd = timeMention.getEnd() - sentenceBegin;

            secondBegin = eventMention.getBegin() - sentenceBegin;
            secondEnd = eventMention.getEnd() - sentenceBegin;

            try {
                firstText = normalizer.parse(timeMention.getCoveredText(), dummyDCT).get().timeMLValue();
            } catch (Exception ignored) {
                firstText = timeMention.getCoveredText();
            }

            secondText = eventMention.getCoveredText();

            firstTag = "t";
            secondTag = "e";
        } else {
            secondBegin = timeMention.getBegin() - sentenceBegin;
            secondEnd = timeMention.getEnd() - sentenceBegin;

            firstBegin = eventMention.getBegin() - sentenceBegin;
            firstEnd = eventMention.getEnd() - sentenceBegin;

            try {
                secondText = normalizer.parse(timeMention.getCoveredText(), dummyDCT).get().timeMLValue();
            } catch (Exception ignored) {
                secondText = timeMention.getCoveredText();
            }

            firstText = eventMention.getCoveredText();
            firstTag = "e";
            secondTag = "t";
        }

        if ( secondBegin <= firstEnd ){
            // I don't even care anymore
            secondBegin = firstEnd + 1;
        }

        /*
        System.err.println(sentenceText);
        System.err.println(timeMention.getCoveredText());
        System.err.println(eventMention.getCoveredText());
        System.err.println(firstText);
        System.err.printf("%d %d\n", firstBegin, firstEnd);
        System.err.println(secondText);
        System.err.printf("%d %d\n", secondBegin, secondEnd);

         */


        out.append(sentenceText, previous, firstBegin);
        out.append(String.format("<%s>%s</%s>", firstTag, firstText, firstTag));
        out.append(sentenceText, firstEnd, secondBegin);
        out.append(String.format("<%s>%s</%s>", secondTag, secondText, secondTag));
        out.append(sentenceText, secondEnd, sentenceText.length());

        return out.toString();
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

        String unnormalized = annotation.getCoveredText();

        String normalized;
        if ( annotation instanceof TimeMention ) {
            tag = "t";
            try {
                normalized = normalizer.parse(unnormalized, dummyDCT).get().timeMLValue();
            } catch (Exception ignored) {
                normalized = unnormalized;
            }
        } else {
            tag = "e";
            normalized = unnormalized;
        }

        out.append( sentenceText, previous, localBegin) ;
        out.append( String.format( "<%s>%s</%s>", tag, normalized, tag ) );
        out.append( sentenceText, localEnd, sentenceText.length() );

        return out.toString();
    }
}
