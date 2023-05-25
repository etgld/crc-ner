package org.apache.ctakes.examples.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

@PipeBitInfo(
        name = "DCTAnnotator ( TxTimelines )",
        description = " Gets the DCT either from the note filename ( if the filename conforms to spec ) or from the header if it's a UPMC note" //,
        //dependencies = { PipeBitInfo.TypeProduct.EVENT },
        //products = { PipeBitInfo.TypeProduct.DOC }
)

public class DCTAnnotator extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    final static private Logger LOGGER = Logger.getLogger("DCTAnnotator");

    @Override
    public void initialize( UimaContext context ) throws ResourceInitializationException {
        super.initialize( context );
    }


    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        //new NoteSpecs( jCas ).getNoteDate();
    }
}