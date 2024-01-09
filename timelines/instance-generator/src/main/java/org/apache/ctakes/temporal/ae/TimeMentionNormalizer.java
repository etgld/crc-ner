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

import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.tongfei.progressbar.*;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
// @PipeBitInfo(
//         name = "Event Time Anafora Writer",
//         description = "Writes Temporal Events and Times in Anafora format."
// )

public class TimeMentionNormalizer extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    static private final Logger LOGGER = Logger.getLogger( "TimeMentionNormalizer" );
    static private final TemporalExpressionParser normalizer = TemporalExpressionParser.en();
    static private final TimeLimiter timeLimiter = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor());
    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        final SourceData sourceData = SourceMetadataUtil.getOrCreateSourceData( jCas );
        final String docTime = sourceData.getSourceOriginalDate();
        DocumentPath documentPath = JCasUtil.select( jCas, DocumentPath.class ).iterator().next();
        final String fileName = FilenameUtils.getBaseName( documentPath.getDocumentPath() );
        TimeSpan _DCT = null;
        if ( docTime == null || docTime.isEmpty() ){
            LOGGER.warn( "Empty DCT for file " + fileName );
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
        // JCasUtil.select( jCas, TimeMention.class ).forEach(
        //     t -> normalize( DCT, fileName, t )
        // );
        List<TimeMention> timeMentions = JCasUtil
            .select( jCas, TimeMention.class )
            .stream()
            .collect( Collectors.toList() );

        LOGGER.info( "normalizing " + timeMentions.size() + " time expressions for " + fileName );
        for ( TimeMention timeMention : ProgressBar.wrap( timeMentions, "Normalizing" ) ){
            try{
                int success = timeLimiter.callUninterruptiblyWithTimeout(
                                                        () -> normalize( jCas, DCT, fileName, timeMention ),
                                                        1,
                                                        TimeUnit.SECONDS
                                                        );
            } catch (Exception e){
                LOGGER.error( " could not parse timex with covered text " + timeMention.getCoveredText() + " in 1 seconds or less ");
            }
        }
        LOGGER.info("finished normalizing " + timeMentions.size() + " time expressions for " + fileName);
    }

    private int normalize( JCas jCas, TimeSpan DCT, String fileName, TimeMention timeMention ){
        String typeName = "";
        String unnormalizedTimex = String.join(" ", timeMention.getCoveredText().split("\\s"));
        Temporal normalizedTimex = null;
        int begin = timeMention.getBegin();
        int end = timeMention.getEnd();
        try{
            normalizedTimex = normalizer.parse( unnormalizedTimex, DCT ).get();
        } catch (Exception ignored){}
        if ( normalizedTimex != null ){
            Time time = timeMention.getTime();
            if (time == null){
                time = new Time( jCas );
                time.addToIndexes();
            }
            time.setNormalizedForm( normalizedTimex.timeMLValue() );
            timeMention.setTime( time );
        }
        return 0;
    }
}
