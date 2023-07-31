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


public class InferencePipeline {
    private static final Logger LOGGER = Logger.getLogger(InferencePipeline.class);


    public static void main(String[] args) throws Exception {
        if(args.length < 2){
            System.err.println("Required arguments: <THYME xml dir> <output dir>");
            System.exit(-1);
        }


        List<String> splits = Arrays.asList("train", "dev", "test");

        File cnlptCorpusDir = makeAndReturnTaskDir(args[1], "");

        for(String partition : splits) {
            CollectionReader reader = getCollectionReader(new File(args[0], partition.toLowerCase()));

            PipelineBuilder builder = new PipelineBuilder();
            // TODO - get list annotator etc working, NB for meds paragraph annotator disappeared some annotations
            builder.add(UriToDocumentTextAnnotator.class);
            builder.add(SimpleSegmentAnnotator.class);
            builder.add(ParagraphAnnotator.class);
            builder.add(ListAnnotator.class);

            AnalysisEngineDescription sentAnnDesc = SentenceDetectorAnnotatorBIO.getDescription();
            builder.addDescription( sentAnnDesc );
            builder.add( MrsDrSentenceJoiner.class );
            builder.add( ParagraphSentenceFixer.class );
            builder.add( ListParagraphFixer.class );
            builder.add( ListSentenceFixer.class );
            builder.add( EolSentenceFixer.class );
            builder.add( TokenizerAnnotatorPTB.class );

            // String[] suffixes = new String[] {".seighe.dave.inprogress.xml", "seighe.seighe.completed.xml"};

            // AnalysisEngineDescription readerDesc = AnaforaXMLReader.getDescription(
            //         "fw",
            //         "cg",
            //         suffixes
            // );
            // builder.addDescription(readerDesc);

            String partitionDir = String.format("%s.tsv", partition.toLowerCase());

            PrintWriter cnlptOut = new PrintWriter(
                    new File(
                            cnlptCorpusDir,
                            partitionDir
                    )
            );
            PrintWriter metricsOut = new PrintWriter(
                    new File(
                            cnlptCorpusDir,
                            String.format(
                                    "%s_metrics.txt",
                                    partition.toLowerCase()
                            )
                    )
            );
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
