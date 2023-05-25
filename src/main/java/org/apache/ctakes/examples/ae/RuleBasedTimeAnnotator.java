package org.apache.ctakes.examples.ae;

import org.apache.ctakes.contexttokenizer.ae.ContextDependentTokenizerAnnotator;
import org.apache.ctakes.core.ae.TokenizerAnnotator;
import org.apache.ctakes.core.fsm.adapters.*;
import org.apache.ctakes.core.fsm.machine.*;
import org.apache.ctakes.core.fsm.output.*;
import org.apache.ctakes.core.fsm.token.BaseToken;
import org.apache.ctakes.core.fsm.token.EolToken;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.typesystem.type.syntax.*;
import org.apache.ctakes.typesystem.type.textsem.*;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.*;

import org.apache.ctakes.core.pipeline.PipeBitInfo;

@PipeBitInfo(
        name = "Context Dependent Annotator",
        description = "Finds tokens based upon context.  Time, Date, Roman numeral, Fraction, Range, Measurement, Person title.",
        dependencies = { PipeBitInfo.TypeProduct.SENTENCE, PipeBitInfo.TypeProduct.BASE_TOKEN }
)

public class RuleBasedTimeAnnotator extends JCasAnnotator_ImplBase {
    // LOG4J logger based on class name
    private final Logger iv_logger = Logger.getLogger(getClass().getName());

    private DateFSM iv_dateFSM;
    private TimeFSM iv_timeFSM;

    @Override
    public void initialize(UimaContext annotCtx) throws ResourceInitializationException {
        super.initialize(annotCtx);

        iv_dateFSM = new DateFSM();
        iv_timeFSM = new TimeFSM();

        iv_logger.info("Finite state machines loaded.");
    }

    @Override
    public void process(JCas jcas) throws AnalysisEngineProcessException {

        try {

            iv_logger.info("process(JCas)");

            Collection<Sentence> sents = JCasUtil.select(jcas, Sentence.class);

            for(Sentence sentAnnot : sents){
                List<org.apache.ctakes.typesystem.type.syntax.BaseToken> tokens =
                        JCasUtil.selectCovered(org.apache.ctakes.typesystem.type.syntax.BaseToken.class, sentAnnot);
                // adapt JCas objects into objects expected by the Finite state
                // machines
                List<BaseToken> baseTokenList = new ArrayList<>();
                for(org.apache.ctakes.typesystem.type.syntax.BaseToken bta : tokens){
                    // ignore newlines, avoid null tokens
                    BaseToken bt = adaptToBaseToken(bta);
                    if(!(bt instanceof EolToken))
                        baseTokenList.add(bt);
                }

                // execute FSM logic
                executeFSMs(jcas, baseTokenList);
            }
        } catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    private void executeFSMs(JCas jcas, List<? extends BaseToken> baseTokenList) throws AnalysisEngineProcessException {
        try {
            Set<DateToken> dateTokenSet = iv_dateFSM.execute(baseTokenList);
            for (DateToken dt : dateTokenSet) {
                TimeAnnotation dta = new TimeAnnotation(jcas, dt.getStartOffset(), dt.getEndOffset());
                dta.addToIndexes();
            }

            Set<TimeToken> timeTokenSet = iv_timeFSM.execute(baseTokenList);
            for (TimeToken tt : timeTokenSet) {
                TimeAnnotation ta = new TimeAnnotation(jcas, tt.getStartOffset(), tt.getEndOffset());
                ta.addToIndexes();
            }

        } catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    /**
     * Adapts JCas objects to BaseToken interfaces expected by the Finite State
     * Machines.
     *
     */
    private static BaseToken adaptToBaseToken(org.apache.ctakes.typesystem.type.syntax.BaseToken obj) throws Exception {
        if (obj instanceof WordToken) {
            WordToken wta = (WordToken) obj;
            return new WordTokenAdapter(wta);
        } else if (obj instanceof NumToken) {
            NumToken nta = (NumToken) obj;
            if (nta.getNumType() == TokenizerAnnotator.TOKEN_NUM_TYPE_INTEGER) {
                return new IntegerTokenAdapter(nta);
            }
            return new DecimalTokenAdapter(nta);
        } else if (obj instanceof PunctuationToken) {
            PunctuationToken pta = (PunctuationToken) obj;
            return new PunctuationTokenAdapter(pta);
        } else if (obj instanceof NewlineToken) {
            NewlineToken nta = (NewlineToken) obj;
            return new NewlineTokenAdapter(nta);
        } else if (obj instanceof ContractionToken) {
            ContractionToken cta = (ContractionToken) obj;
            return new ContractionTokenAdapter(cta);
        } else if (obj instanceof SymbolToken) {
            SymbolToken sta = (SymbolToken) obj;
            return new SymbolTokenAdapter(sta);
        }

        throw new Exception("No Context Dependent Tokenizer adapter for class: " + obj.getClass());
    }

    public static AnalysisEngineDescription createAnnotatorDescription() throws ResourceInitializationException{
        return AnalysisEngineFactory.createEngineDescription(ContextDependentTokenizerAnnotator.class);
    }
}
