package org.apache.ctakes.examples.cc;

import org.apache.ctakes.core.fsm.machine.DateFSM;
import org.apache.ctakes.core.fsm.machine.TimeFSM;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.clulab.timenorm.scate.WordsToNumber;
import org.clulab.timenorm.scfg.*;
import org.clulab.timenorm.scate.*;
import scala.Option;
import scala.Tuple2;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.*;
import java.util.Arrays;
import java.io.FileInputStream;



public class NormalizedTimeAnnotator extends JCasAnnotator_ImplBase {

    static private final Logger LOGGER = Logger.getLogger( "NormalizedTimeAnnotator" );



    String[] weekdays = {
            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",
    };

    String[] months = {
            "january",
            "february",
            "march",
            "april",
            "may",
            "june",
            "july",
            "august",
            "september",
            "october",
            "november",
            "december",
    };

    SimpleInterval dummyDCT = SimpleInterval.of(2017, 4, 1);

    // requires a model stream even though it's optional in Scala, this should just load the default


    @Override
    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        Instant start = Instant.now();
        JCasUtil.select( jCas, Sentence.class ).forEach(
                sentence -> insertNormalized( jCas, sentence )
        );
        Instant end = Instant.now();
        LOGGER.info( Duration.between( start, end ).getSeconds() );
    }

    public void insertNormalized( JCas jCas, Sentence sentence ){

    }
}
