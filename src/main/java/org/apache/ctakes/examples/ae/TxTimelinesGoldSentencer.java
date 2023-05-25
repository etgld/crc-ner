package org.apache.ctakes.examples.ae;

import org.apache.commons.io.FilenameUtils;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.typesystem.type.textspan.Paragraph;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.cleartk.util.ViewUriUtil;

import java.util.Arrays;

/**
 * @author SPF , chip-nlp
 * @since {8/29/2022}
 */
@PipeBitInfo(
        name = "TxTimelinesGoldSentencer",
        description = ".",
        role = PipeBitInfo.Role.ANNOTATOR,
        products = PipeBitInfo.TypeProduct.SENTENCE
)
public class TxTimelinesGoldSentencer extends JCasAnnotator_ImplBase {

    /**
     * {@inheritDoc}
     */
    @Override
    public void process( final JCas jcas ) throws AnalysisEngineProcessException {
        String documentText = jcas.getDocumentText();

        Arrays.asList(documentText.split("[.;]")).forEach(
                protoSent -> {
                    String _sentence = protoSent.trim();
                    int sentBegin = documentText.indexOf( _sentence );
                    final Sentence sentence = new Sentence( jcas, sentBegin, sentBegin + _sentence.length() );
                    sentence.addToIndexes();
                }
        );
    }

}
