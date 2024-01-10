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

    // static String SKIP_NO_TUI = "noTuiSkip";

    // static String TIMEOUT = "timeout";

    // @ConfigurationParameter( name = TimeMentionNormalizer.SKIP_NO_TUI, mandatory = false,
    //                          description = "Skip processing notes that have no relevant event mentions" )
    private String _tui = "T061";


    // @ConfigurationParameter( name = TimeMentionNormalizer.TIMEOUT, mandatory = false,
    //                          description = "Stop trying to normalize a TimeMention after this many seconds" )
    private int _timeout = 5;
    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        final SourceData sourceData = SourceMetadataUtil.getOrCreateSourceData( jCas );
        final String docTime = sourceData.getSourceOriginalDate();
        DocumentPath documentPath = JCasUtil.select( jCas, DocumentPath.class ).iterator().next();
        final String fileName = FilenameUtils.getBaseName( documentPath.getDocumentPath() );
        if (_tui != null && !_tui.trim().isEmpty()){
            long relevantTUICount = JCasUtil
                .select( jCas, EventMention.class )
                .stream()
                .map( OntologyConceptUtil::getUmlsConcepts )
                .flatMap( Collection::stream )
                .map( UmlsConcept::getTui )
                .filter( tui -> tui.equals( _tui ) )
                .collect( Collectors.counting() );

            if ( relevantTUICount == 0 ){
                LOGGER.info(fileName + " : no events with TUI " + _tui + ", skipping to save time");
                return;
            }
        }

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
                                                        _timeout,
                                                        TimeUnit.SECONDS
                                                        );
            } catch (Exception e){
                String unnormalizedTimex = String.join(" ", timeMention.getCoveredText().split("\\s"));
                LOGGER.error( " could not parse timex with covered text " + timeMention.getCoveredText() + " in " + _timeout + " seconds or less ");
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
        } catch (Exception ignored){
            LOGGER.error( "failed to normalize timex " + unnormalizedTimex );
            return 1;
        }
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
