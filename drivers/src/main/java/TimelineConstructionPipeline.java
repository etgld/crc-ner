import gov.nih.nlm.nls.lvg.Util.In;
import org.apache.commons.collections4.MapUtils;
import org.apache.ctakes.core.ae.*;
import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.cleartk.util.ae.UriToDocumentTextAnnotator;
import org.cleartk.util.cr.UriCollectionReader;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;


public class TimelineConstructionPipeline {
    private static final Logger LOGGER = Logger.getLogger(TimelineConstructionPipeline.class);


    public static void main(String[] args) throws Exception {
    }
}
