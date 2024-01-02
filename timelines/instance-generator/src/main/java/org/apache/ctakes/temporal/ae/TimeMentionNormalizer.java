package org.apache.ctakes.temporal.ae;

import org.apache.commons.io.FilenameUtils;
import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.util.annotation.IdentifiedAnnotationUtil;
import org.apache.ctakes.core.util.annotation.OntologyConceptUtil;
import org.apache.ctakes.core.util.doc.SourceMetadataUtil;
import org.apache.ctakes.typesystem.type.constants.CONST;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.refsem.EventProperties;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.ctakes.typesystem.type.structured.DocumentPath;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
// import org.apache.ctakes.typesystem.type.refsem.Date;
import org.apache.ctakes.typesystem.type.refsem.Time;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.log4j.Logger;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.clulab.timenorm.scfg.Temporal;
import org.clulab.timenorm.scfg.TemporalExpressionParser;
import org.clulab.timenorm.scfg.TimeSpan;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import java.util.*;


// @PipeBitInfo(
//         name = "Event Time Anafora Writer",
//         description = "Writes Temporal Events and Times in Anafora format."
// )

public class TimeMentionNormalizer extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    final static private Logger LOGGER = Logger.getLogger( "TimeMentionNormalizer" );
    static private final TemporalExpressionParser normalizer = TemporalExpressionParser.en();
    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        final SourceData sourceData = SourceMetadataUtil.getOrCreateSourceData( jCas );
        final String docTime = sourceData.getSourceOriginalDate();
        
        TimeSpan _DCT = null;
        
        
        if ( docTime == null || docTime.isEmpty() ){
            LOGGER.warn( "Empty DCT, not creating the node" );
        } else {
            String[] docTimeComponents = docTime.split("-");
            
            
            // properly generated
            if (docTimeComponents.length == 3) {
                _DCT = TimeSpan.of(
                                  Integer.parseInt(docTimeComponents[0]),
                                  Integer.parseInt(docTimeComponents[1]),
                                  Integer.parseInt(docTimeComponents[2]));
            } else {
                // DocTimeApproximator generated
                _DCT = TimeSpan.of(
                                  Integer.parseInt(docTime.substring(0, 4)),
                                  Integer.parseInt(docTime.substring(4, 6)),
                                  Integer.parseInt(docTime.substring(6, 8)));
            }
        }
        final TimeSpan DCT = _DCT;
        DocumentPath documentPath = JCasUtil.select( jCas, DocumentPath.class ).iterator().next();
        final String fileName = FilenameUtils.getBaseName( documentPath.getDocumentPath() );
        // JCasUtil.select( jCas, TimeMention.class ).forEach(
        //     t -> normalize( DCT, fileName, t )
        // );
        Collection<TimeMention> timeMentions = JCasUtil.select( jCas, TimeMention.class );

        for ( TimeMention timeMention : timeMentions){
            normalize( jCas, DCT, fileName, timeMention );
        }
    }

    private void normalize( JCas jCas, TimeSpan DCT, String fileName, TimeMention timeMention ){
        String typeName = "";
        String unnormalizedTimex = timeMention.getCoveredText();
        Temporal normalizedTimex = null;
        try{
            normalizedTimex = normalizer.parse( unnormalizedTimex, DCT ).get();
        } catch (Exception ignored){}
        if ( normalizedTimex != null ){
            // setting this in date due since breaking the
            // parts up of the temporal mention is too weird at the moment
            // and generally we're just using the date anyway
            // timeMention.setDate( normalizedTimex.timeMLValue() );
            // Time _placeHolder = new Time();
            // _placeHolder.setNormalizedForm( normalizedTimex );
            // _placeHolder.addToIndexes();
            // timeMention.setTime( _placeHolder );
            Time time = timeMention.getTime();
            if (time == null){
                time = new Time( jCas );
                time.addToIndexes();
                timeMention.setTime( time );
            }
            time.setNormalizedForm( normalizedTimex.timeMLValue() );
        } else {
            LOGGER.warn( fileName + "resorting to unnormalized timex: " + unnormalizedTimex );
        }
    }
}
