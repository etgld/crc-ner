package org.apache.ctakes.temporal.ae;

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
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.ctakes.core.pipeline.PipeBitInfo.TypeProduct.BASE_TOKEN;
import static org.apache.ctakes.core.pipeline.PipeBitInfo.TypeProduct.DOCUMENT_ID_PREFIX;

@PipeBitInfo(
        name = "Event Time Anafora Writer",
        description = "Writes Temporal Events and Times in Anafora format.",
)

public class TimeMentionNormalizer extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    final static private Logger LOGGER = Logger.getLogger( "TimeMentionNormalizer" );
    static private final TemporalExpressionParser normalizer = TemporalExpressionParser.en();
    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        final SourceData sourceData = SourceMetadataUtil.getOrCreateSourceData( jCas );
        final String DCT = sourceData.getSourceOriginalDate();
        DocumentPath documentPath = JCasUtil.select( jCas, DocumentPath.class ).iterator().next();
        String fileName = FilenameUtils.getBaseName( documentPath.getDocumentPath() );
        JCasUtil.select( jCas, TimeMention.class ).forEach(
            t -> normalize( DCT, fileName, t )
        )
    }

    private void normalize( String DCT, String fileName, TimeMention timeMention ){
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
            timeMention.setDate( normalizedTimex.timeMLValue() )
        } else {
            LOGGER.warn( fileName + "resorting to unnormalized timex: " + unnormalizedTimex );
        }
    }
}