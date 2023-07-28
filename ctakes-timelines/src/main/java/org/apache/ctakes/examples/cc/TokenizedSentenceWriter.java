package org.apache.ctakes.examples.cc;

import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class TokenizedSentenceWriter extends AbstractJCasFileWriter {


    static private final Logger LOGGER = Logger.getLogger( "EventTextWriter" );
    static private final String FILE_EXTENSION = ".txt";


    @Override
    public void writeFile( final JCas jCas,
                           final String outputDir,
                           final String documentId,
                           final String fileName ) throws IOException {
        final File outputFilePath = new File( outputDir , fileName + "_tokenized_sents" + FILE_EXTENSION );
        LOGGER.info("Writing " + fileName + FILE_EXTENSION + " to " + outputFilePath.getPath()  +" ...") ;
        try ( Writer writer = new BufferedWriter( new FileWriter( outputFilePath ) ) ) {
            JCasUtil.select( jCas, Sentence.class ).forEach(
                    p -> {
                        String tokenized = JCasUtil.selectCovered(BaseToken.class, p)
                                .stream()
                                .sorted(Comparator.comparingInt(BaseToken::getBegin))
                                .map(t -> t instanceof NewlineToken ? "<cr>" : t.getCoveredText())
                                .collect(Collectors.joining(" "));
                        try {
                            writer.write( tokenized + "\n");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    );
        }
        LOGGER.info( "Finished Writing" );
    }
}
