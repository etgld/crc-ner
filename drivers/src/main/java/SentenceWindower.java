import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author etg , chip-nlp
 * @since {6/6/2022}
 */
@PipeBitInfo(
        name = "SentenceWindower",
        description = "Creates a sentence covering each paragraph.",
        role = PipeBitInfo.Role.ANNOTATOR,
        dependencies = PipeBitInfo.TypeProduct.SENTENCE,
        products = PipeBitInfo.TypeProduct.SENTENCE
)
public class SentenceWindower extends JCasAnnotator_ImplBase {

    static private final Logger LOGGER = Logger.getLogger( "SentenceWindower" );

    public static final String PARAM_MAX_SEQUENCE_LENGTH = "MaxSequenceLength";

    // Lesson learned, no defaults! Ever!
    @ConfigurationParameter(
            name = PARAM_MAX_SEQUENCE_LENGTH,
            description = "Maximum length for sentences"
    )
    private static int maxSequenceLength;

    public static final String PARAM_OVERLAP_SIZE = "OverlapSize";
    @ConfigurationParameter(
            name = PARAM_OVERLAP_SIZE,
            description = "Number of characters for which overlap is allowed"
    )
    private static int overlapSize;

    public static final String PARAM_TOKEN_SENSITIVE = "TokenSensitive";
    @ConfigurationParameter(
            name = PARAM_TOKEN_SENSITIVE,
            description = "Whether to _not_ split in the middle of a ( whitespace delimited ) token"
    )
    private static boolean tokenSensitive;

    static private String addSentence( final JCas jcas, final int begin, final int end ) {
        final Sentence sentence = new Sentence( jcas, begin, end );
        sentence.addToIndexes();
        return sentence.getCoveredText();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    // TODO - maybe should allow for windowing around a mention of a given type a la radiotherapy with the doses
    public void process( final JCas jCas ) throws AnalysisEngineProcessException {
        if (!(overlapSize >= 0 && maxSequenceLength > 0 && overlapSize <= maxSequenceLength)){
            throw new AnalysisEngineProcessException();
        }
        LOGGER.info( "Token sensitive " + tokenSensitive + " with max length " + maxSequenceLength + " and overlap " + overlapSize );
        JCasUtil.select( jCas, Sentence.class ).forEach( s -> windowSentence( jCas, s ) );
    }

    // slow but cleaner
    static void windowSentence( JCas jCas, Sentence sentence ){
        int sentenceBegin = sentence.getBegin();
        int sentenceEnd = sentence.getEnd();
        String sentenceText = sentence.getCoveredText();
        sentence.removeFromIndexes();

        if (tokenSensitive) {
            insertTokenAwareWindows( jCas, sentenceBegin, sentenceEnd, sentenceText );
        } else {
            insertNaiveWindows( jCas, sentenceBegin, sentenceEnd );
        }
    }

    static void insertNaiveWindows( JCas jCas, int sentenceBegin, int sentenceEnd ){

        int current = sentenceBegin;

        if ( sentenceEnd - sentenceBegin <= maxSequenceLength ) return;

        while ( current < sentenceEnd ){
            int gap = current > sentenceBegin ? overlapSize : 0;
            current -= gap;
            int fullWindow = current + maxSequenceLength;
            int next = Math.min( fullWindow, sentenceEnd );
            addSentence( jCas, current, next );
            current = next;
        }
    }

    // painfully stateful but streams / FP can't fix everything
    static void insertTokenAwareWindows( JCas jCas, int sentenceBegin, int sentenceEnd, String sentenceText ) {
        //int sentenceBegin = sentence.getBegin();
        //int sentenceEnd = sentence.getEnd();

        if ( sentenceEnd - sentenceBegin <= maxSequenceLength ) return;

        // List<String> sentences = new ArrayList<>();

        List<Character> remainingCharacters = sentenceText
                .chars()
                .mapToObj( e -> ( char ) e )
                .collect( Collectors.toList() );

        int cas_window_start = sentenceBegin;
        int local_window_start = 0;

        boolean in_token = false;

        int in_token_begin = -1; // since might start in whitespace
        // having a real value is contingent on in_token being true

        int in_token_end;

        int look_behind_in_token_begin = -1;
        int look_behind_in_token_end = -1;

        int total_characters = remainingCharacters.size();

        for ( int i = 0; i < total_characters; i++ ){

            int forward_boundary = in_token ? in_token_begin - 1 : i;

            if ( !in_token && !Character.isWhitespace( remainingCharacters.get( i ) ) ){
                in_token = true;
                in_token_begin = i;
                if ( i >= ( local_window_start + maxSequenceLength ) - overlapSize  && look_behind_in_token_begin == -1 ) {
                    look_behind_in_token_begin = in_token_begin;
                }
            }
            if ( in_token && Character.isWhitespace( remainingCharacters.get( i ) ) ){
                in_token = false;
                in_token_end = i;
                if ( i >= ( local_window_start + maxSequenceLength ) - overlapSize  && look_behind_in_token_end == -1 ) {
                    look_behind_in_token_end = in_token_end;
                }
            }


            if ( i == total_characters - 1 ){
                //sentences.add( addSentence( jCas, cas_window_start, sentenceEnd ) );
                addSentence( jCas, cas_window_start, sentenceEnd );
            } else if ( i == local_window_start + maxSequenceLength ) {

                //sentences.add( addSentence( jCas, cas_window_start, forward_boundary + cas_window_start ) );
                addSentence( jCas, cas_window_start, forward_boundary + cas_window_start );

                int final_delta = overlapSize == 0 ? forward_boundary : Math.min( look_behind_in_token_begin, look_behind_in_token_end );

                cas_window_start += final_delta;
                local_window_start = final_delta;

                if ( cas_window_start >= sentenceEnd ) break;
            }
        }
    }

    public static AnalysisEngineDescription createAnnotatorDescription( int maxSequenceLength, int overlapSize, boolean tokenSensitive )
            throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(
                SentenceWindower.class,
                SentenceWindower.PARAM_MAX_SEQUENCE_LENGTH,
                maxSequenceLength,
                SentenceWindower.PARAM_OVERLAP_SIZE,
                overlapSize,
                SentenceWindower.PARAM_TOKEN_SENSITIVE,
                tokenSensitive
        );
    }
}
