package org.apache.ctakes.examples.cc;

import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.typesystem.type.textsem.TimeAnnotation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.clulab.timenorm.scfg.Temporal;
import org.clulab.timenorm.scfg.TemporalExpressionParser;
import org.clulab.timenorm.scfg.TimeSpan;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ComparisonWriter  extends AbstractJCasFileWriter {


    static private final Logger LOGGER = Logger.getLogger( "ComparisonWriter" );
    static private final String FILE_EXTENSION = ".txt";
    private static final TemporalExpressionParser normalizer = TemporalExpressionParser.en();
    // for now give a 'non time'
    static private final TimeSpan dummyDCT = TimeSpan.of(2017, 4, 1);

    @Override
    public void writeFile( final JCas jCas,
                           final String outputDir,
                           final String documentId,
                           final String fileName ) throws IOException {
        final File outputFilePath = new File( outputDir , fileName + "_compared_mentions" + FILE_EXTENSION );
        LOGGER.info("Writing " + fileName + FILE_EXTENSION + " to " + outputFilePath.getPath()  +" ...") ;
        try ( Writer writer = new BufferedWriter( new FileWriter( outputFilePath ) ) ) {
            Map<Sentence, Collection<TimeMention>> _sent2SVMTimes = JCasUtil.indexCovered(
                    jCas,
                    Sentence.class,
                    TimeMention.class
            );

            Map<Sentence, Collection<TimeAnnotation>> _sent2RuleTimes = JCasUtil.indexCovered(
                    jCas,
                    Sentence.class,
                    TimeAnnotation.class
            );

            Map<Sentence, List<TimeMention>> sent2SVMTimes = new HashMap<>();
            _sent2SVMTimes.forEach(
                    (sentence, timexes) -> sent2SVMTimes.put(
                            sentence,
                            timexes.stream()
                                    .sorted(
                                            Comparator.comparingInt(
                                                    TimeMention::getBegin
                                            )
                                    ).collect(Collectors.toList())
                    )
            );

            Map<Sentence, List<TimeAnnotation>> sent2RuleTimes = new HashMap<>();
            _sent2RuleTimes.forEach(
                    (sentence, timexes) -> sent2RuleTimes.put(
                            sentence,
                            timexes.stream()
                                    .sorted(
                                            Comparator.comparingInt(
                                                    TimeAnnotation::getBegin
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
                            sentence -> (sent2RuleTimes.containsKey(sentence) || sent2SVMTimes.containsKey(sentence))
                    )
                    .forEach(
                            sentence -> {
                                try {
                                    writeMention(
                                            casSentences.indexOf(sentence) + 1,
                                            sentence,
                                            sent2SVMTimes.getOrDefault(sentence, new ArrayList<>()),
                                            sent2RuleTimes.getOrDefault(sentence, new ArrayList<>()),
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
     * @param TimeMentions annotations
     * @param writer   writer to which pretty text for the sentence should be written
     * @throws IOException if the writer has issues
     */
    static public void writeMention(
            final int sentIndex,
            final Sentence container,
            final List<TimeMention> TimeMentions,
            final List<TimeAnnotation> TimeAnnotations,
            final Writer writer
    ) throws IOException {

        writer.write(
                String.format(
                        "\nSentence: %d\n\n",
                        sentIndex
                )
        );

        writer.write(
                taggedSentence(
                        container,
                        TimeMentions,
                        TimeAnnotations
                )
        );
        writer.write("\n");
    }

    static private String taggedSentence( Sentence sentence,
                                          List<TimeMention> timeMentions,
                                          List<TimeAnnotation> timeAnnotations ){
        StringBuilder out = new StringBuilder();
        String tag = "timex";


        String sentenceText = sentence.getCoveredText().replace("\n", " ");
        int sentenceBegin = sentence.getBegin();
        int previous = 0;

        List<String> svmMentionOuts = new ArrayList<>();

        List<String> ruleMentionOuts = new ArrayList<>();


        for (TimeMention timeMention : timeMentions){
            int localBegin = timeMention.getBegin() - sentenceBegin;
            int localEnd = timeMention.getEnd() - sentenceBegin;
            if (previous < localBegin) {
                out.append(sentenceText, previous, localBegin);
            }

            String unnormalized = timeMention.getCoveredText();

            Temporal normalized = null;
            try {
                normalized = normalizer.parse(unnormalized, dummyDCT).get();
            } catch (Exception ignored){}
            out.append("< BackwardsTimeAnnotator > ");
            // out.append(normalized);
            out.append(sentenceText, localBegin, localEnd);
            out.append(" < / BackwardsTimeAnnotator >");
            if (normalized != null) {
                svmMentionOuts.add(
                        String.format(
                                "__HANDLED__ literal : %s\ttimeML : %s\tfull : %s\n\n",
                                sentenceText.substring(localBegin, localEnd),
                                normalized.timeMLValue(),
                                normalized
                        )
                );
            } else {
                svmMentionOuts.add(
                        String.format(
                                "__UNHANDLED__ literal : %s\n\n",
                                sentenceText.substring(localBegin, localEnd)
                        )
                );
            }
            previous = localEnd;
        }

        for (TimeAnnotation timeAnnotation : timeAnnotations){
            int localBegin = timeAnnotation.getBegin() - sentenceBegin;
            int localEnd = timeAnnotation.getEnd() - sentenceBegin;
            if (previous < localBegin) {
                out.append(sentenceText, previous, localBegin);
            }

            String unnormalized = timeAnnotation.getCoveredText();

            Temporal normalized = null;
            try {
                normalized = normalizer.parse(unnormalized, dummyDCT).get();
            } catch (Exception ignored){}
            out.append("< RuleBasedTimeAnnotator > ");
            // out.append(normalized);
            out.append(sentenceText, localBegin, localEnd);
            out.append(" < / RuleBasedTimeAnnotator >");
            if (normalized != null) {
                svmMentionOuts.add(
                        String.format(
                                "__HANDLED__ literal : %s\ttimeML : %s\tfull : %s\n\n",
                                sentenceText.substring(localBegin, localEnd),
                                normalized.timeMLValue(),
                                normalized
                        )
                );
            } else {
                svmMentionOuts.add(
                        String.format(
                                "__UNHANDLED__ literal : %s\n\n",
                                sentenceText.substring(localBegin, localEnd)
                        )
                );
            }
            previous = localEnd;
        }



        out.append(sentenceText, previous, sentenceText.length());
        out.append("\n\n").append(String.join("", svmMentionOuts)).append("\n\n");
        return out.toString();
    }
}
