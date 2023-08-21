package org.apache.ctakes.examples.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.refsem.EventProperties;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@PipeBitInfo(
        name = "EventFilter ( TxTimelines )",
        description = " Filters event mentions which contain any of the provided set of terms ( to be called after EventAnnotator ) ",
        dependencies = { PipeBitInfo.TypeProduct.EVENT },
        products = { PipeBitInfo.TypeProduct.EVENT }
)

public class EventFilter extends org.apache.uima.fit.component.JCasAnnotator_ImplBase {
    final static private Logger LOGGER = Logger.getLogger( "EventFilter" );

    public static final String PARAM_FILTER_LIST = "filterList";

    @ConfigurationParameter(
            name = PARAM_FILTER_LIST,
            description = "The way we store files for processing.  Aligned pair of directories ",
            mandatory = false
    )
    private String filterList;

    private Set<String> terms;

    @Override
    public void initialize( UimaContext context ) throws ResourceInitializationException {
        super.initialize( context );
        this.terms = getTerms();
    }

    public void process( JCas jCas ) throws AnalysisEngineProcessException {
        JCasUtil.select( jCas, EventMention.class )
                .stream()
                .filter( this::toRemove )
                .forEach( EventMention::removeFromIndexes );
    }

    private boolean toRemove( EventMention eventMention ){

        // for preserving my own sanity
        // https://winterbe.com/posts/2015/03/15/avoid-null-checks-in-java/

        String contextualModality = Optional.of( eventMention )
                .map( EventMention::getEvent )
                .map( Event::getProperties )
                .map( EventProperties::getContextualModality )
                .orElse( "" )
                .trim()
                .toLowerCase();

        boolean isHypothetical = contextualModality.equals( "hypothetical" );

        // this is very very brute force, it behooves us to check
        // how the dictionary lookup works since there are ways for even
        // fuzzy matching to be more efficient than this but for
        // low demand and small exclusion lists it's not _too_ much of a trade off
        boolean isFilterMatch = false;
        if ( !this.terms.isEmpty() ) {
            isFilterMatch = this.terms.stream()
                    .anyMatch(
                            term -> eventMention
                                    .getCoveredText()
                                    .toLowerCase()
                                    .contains(term)
                    );
        }
        return isFilterMatch || isHypothetical;
    }

    private Set<String> getTerms() {
        if ( filterList != null && !filterList.isEmpty() ) {
            try ( InputStream descriptorStream = FileLocator.getAsStream( filterList ) ) {
                return new BufferedReader(
                        new InputStreamReader(
                                descriptorStream,
                                StandardCharsets.UTF_8
                        )
                ).lines()
                        .map( String::toLowerCase )
                        .collect( Collectors.toSet() );
            } catch ( IOException e ) {
                throw new RuntimeException( e );
            }
        } else {
            //throw new RuntimeException( "Missing Filter List" );
            LOGGER.info( "Missing Filter List, Using Empty List" );
            return new HashSet<>();
        }
    }
}