import gov.nih.nlm.nls.lvg.Util.In;
import org.apache.commons.collections4.MapUtils;
import org.apache.ctakes.chunker.ae.Chunker;
import org.apache.ctakes.chunker.ae.adjuster.ChunkAdjuster;
import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.*;
import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.dictionary.lookup2.ae.DefaultJCasTermAnnotator;
import org.apache.ctakes.postagger.POSTagger;
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


public class InferencePipeline {
    private static final Logger LOGGER = Logger.getLogger(InferencePipeline.class);


    public static void main(String[] args) throws Exception {
        if(args.length < 2){
            System.err.println("Required arguments: <THYME xml dir> <output dir>");
            System.exit(-1);
        }

        File cnlptCorpusDir = makeAndReturnTaskDir(args[1], "");

        CollectionReader reader = getCollectionReader(new File(args[0]));

        PipelineBuilder builder = new PipelineBuilder();
        // These first three replicate the DefaultTokenizerPipeline
        // in DefaultFastPipeline

        builder.add( SimpleSegmentAnnotator.class );
        builder.add( SentenceDetector.class );
        builder.add( TokenizerAnnotatorPTB.class );

        // adding everything for now since idk what the
        // timex annotators make use of

        // non-core annotators in DefaultFastPipeline
        builder.add( ContextDependentTokenizerAnnotator.class );
        AnalysisEngineDescription posTagger = POSTagger.createAnnotatorDescription();
        builder.addDescription( posTagger );

        // from ChunkerSubPipe
        builder.add( Chunker.class );
        AnalysisEngineDescription firstAdjuster = ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "NP"}, 1);
        AnalysisEngineDescription secondAdjuster = ChunkAdjuster.createAnnotatorDescription(new String[]{"NP", "PP", "NP"}, 2);
        builder.addDescription( firstAdjuster );
        builder.addDescription( secondAdjuster );

        // from DictionarySubPipe
        // TODO get actual lookup path and figure out how to set the UMLS key
        AnalysisEngineDescription termAnnotator = DefaultJCasTermAnnotator.createAnnotatorDescription("CHANGE_ME_TO_WHEREVER_CHEMO_ONTOLOGY_IS");
        builder.addDescription( termAnnotator );

        // from AttributeCleartkSubPipe



        PrintWriter cnlptOut = new PrintWriter( cnlptCorpusDir );

            AnalysisEngineDescription builderDesc = builder.getAnalysisEngineDesc();

            JCasIterator casIter = new JCasIterator( reader, AnalysisEngineFactory.createEngine( builderDesc ) );
            // int counter = 1;
            // columnNameToType.keySet()
            //         .stream()
            //         .sorted()
            //         .forEach( t -> cnlptOut.print( t + "\t" ) );
            // cnlptOut.print( "text" );

            cnlptOut.println();
            while (casIter.hasNext()) {
                int i = 0;
            }
        }

    static File makeAndReturnTaskDir(String baseDir, String taskString){
        File taskDir = new File(baseDir, taskString);
        taskDir.mkdirs();
        return taskDir;
    }



    static CollectionReader getCollectionReader( File xmlDirectory ) throws Exception {
        List<File> collectedFiles = getFilesFor(xmlDirectory);
        return UriCollectionReader.getCollectionReaderFromFiles(collectedFiles);
    }


    static List<File> getFilesFor(File xmlDirectory) {
        List<File> files = new ArrayList<>();
        for ( File dir : Objects.requireNonNull(xmlDirectory.listFiles())) {
            if (dir.isDirectory()) {
                File file = new File(dir, dir.getName());
                if (file.exists()) {
                    files.add(file);
                } else {
                    System.err.println("Missing note: " + file);
                }
            }
        }
        return files;
    }
}
