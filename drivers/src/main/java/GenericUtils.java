import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GenericUtils {

    static String getTokenStringForSentence(Sentence sent){
        return JCasUtil.selectCovered(
                        BaseToken.class,
                        sent)
                .stream()
                .sorted(
                        // another just in case sorting
                        Comparator.comparingInt(Annotation::getBegin)
                )
                .map(
                        p -> p instanceof NewlineToken ? "<cr>" : p.getCoveredText()
                ).collect(
                        Collectors.joining(" ")
                );
    }

    static File makeAndReturnTaskDir(String baseDir, String taskString){
        File taskDir = new File(baseDir, taskString);
        taskDir.mkdirs();
        return taskDir;
    }

    public static String getRelationAnnotationString(
            JCas jcas,
            Sentence sent,
            Annotation annot1,
            Annotation annot2)
    {
        boolean inArg1 = false;
        boolean inArg2 = false;
        StringBuilder sbuff = new StringBuilder();
        for(BaseToken token : JCasUtil.selectCovered(jcas, BaseToken.class, sent)){
            if(token instanceof NewlineToken) sbuff.append("<cr> "); //continue;
            // for multi-word arguments, annot1 and annot2 will not be event mentions so they will get the default
            // values of the current token.
            String annot1TokenString = token.getCoveredText();
            String annot2TokenString = token.getCoveredText();

            if(token.getBegin() == annot1.getBegin()) {
                sbuff.append(String.format("<a1> %s ", annot1TokenString));
                inArg1 = true;
            }else if(token.getBegin() == annot2.getBegin()) {
                sbuff.append(String.format("<a2> %s ", annot2TokenString));
                inArg2 = true;
            }else if(inArg1 && token.getEnd() == annot1.getEnd()) {
                sbuff.append(String.format("%s </a1> ", annot1TokenString));
                inArg1 = false;
            }else if(inArg2 && token.getEnd() == annot2.getEnd()) {
                sbuff.append(String.format("%s </a2> ", annot2TokenString));
                inArg2 = false;
            }else if(inArg1 && token.getBegin() >= annot1.getEnd()) {
                sbuff.append(String.format("</a1> %s ", token.getCoveredText()));
                inArg1 = false;
            }else if(inArg2 && token.getBegin() >= annot2.getEnd()) {
                sbuff.append(String.format("</a2> %s ", token.getCoveredText()));
                inArg2 = false;
            }else{
                sbuff.append(token.getCoveredText());
                sbuff.append(" ");
            }
        }
        // it's possible for both to be open and that would be a bug, but we're not writing xml, this is just
        // input for BERT which won't complain too much about ill-formed xml.
        if(inArg1){
            sbuff.append("</a1> ");
        }
        if(inArg2){
            sbuff.append("</a2> ");
        }
        return sbuff.substring(0, sbuff.length()-1).replaceAll("\\s", " ");
    }

    static boolean beginInside(Annotation source, Annotation target){
        // turning off for old KCR
        return (source.getBegin() >= target.getBegin()) && (source.getBegin() < target.getEnd());
        // return (source.getBegin() == target.getBegin());
    }

    static Integer nearestBegin( Annotation annotation, List<BaseToken> sentTokens ){
       return sentTokens.stream()
               .mapToInt
                       (sentToken -> Math.abs(
                                       annotation.getBegin() - sentToken.getBegin()
                               )
                       )
               .min()
               .orElse(-1);
    }

    static Integer nearestEnd( Annotation annotation, List<BaseToken> sentTokens ){
        return sentTokens.stream()
                .mapToInt
                        (sentToken -> Math.abs(
                                        annotation.getEnd() - sentToken.getEnd()
                                )
                        )
                .min()
                .orElse(-1);
    }

    static boolean endInside(Annotation source, Annotation target){
        return (source.getEnd() >= target.getBegin()) && (source.getEnd() <= target.getEnd());
        // return (source.getEnd() <= target.getEnd());
    }

    static Integer inexactStart(Annotation annotation, List<BaseToken> sentTokens){
        return IntStream.range(0, sentTokens.size())
                .filter(
                        i -> beginInside(annotation, sentTokens.get(i))
                )
                .findFirst()
                .orElse(
                        nearestBegin(
                                annotation,
                                sentTokens
                        )
                );
    }

    static Integer inexactEnd(Annotation annotation, List<BaseToken> sentTokens){
        return IntStream.range(0, sentTokens.size())
                .filter(
                        i -> endInside(annotation, sentTokens.get(i))
                )
                .findFirst()
                .orElse(
                        nearestEnd(
                                annotation,
                                sentTokens
                        )
                );
    }

    static void insertTags(
            Annotation annotation,
            List<BaseToken> sentTokens,
            String[] tags,
            String annTag
    ) {
        int localStart = inexactStart(annotation, sentTokens);

        int localEnd = inexactEnd(annotation, sentTokens);

        if ((localStart == -1) || (localEnd == -1)){
            System.err.printf("IT HAPPNENED %s", GenericUtils.annotationInfo(annotation));
            System.err.printf(
                    sentTokens
                            .stream()
                            .map(
                                    p ->
                                            p instanceof NewlineToken ? "<cr>" : p.getCoveredText())
                            .collect(
                                    Collectors.joining(" ")
                            )
            );
            return;
        }

        for (int i = localStart; i <= localEnd; i++){
            if (i == localStart){
                tags[i] = !annTag.isEmpty() ? "B-" + annTag : "B";
            }
            else {
                tags[i] = !annTag.isEmpty() ? "I-" + annTag : "I";
            }
        }
    }

    public static List<? extends Annotation> getPrimeAnnotations( List<? extends Annotation> annotations ){
        List<Annotation> primes = new ArrayList<>();

        int prevBegin, prevEnd;
        int currBegin, currEnd;
        
        for (int i = 0; i < annotations.size(); i++){
            if (i == 0){
                prevBegin = -1;
                prevEnd = -1;
            } else {
                prevBegin = primes.get(primes.size() - 1).getBegin();
                prevEnd = primes.get(primes.size() - 1).getEnd();
            }

            currBegin = annotations.get(i).getBegin();
            currEnd = annotations.get(i).getEnd();
            
            if ((prevBegin >= currBegin) && (currEnd >= prevEnd)){
                primes.remove(primes.size() - 1);
                primes.add(annotations.get(i));

            } else if (prevEnd <= currBegin) {
                primes.add(annotations.get(i));
            }
        }

        return primes;
    }

    public static String getSequenceInstance( List<BaseToken> sentTokens,
                                              List<Annotation> annotations,
                                              String annTag ){
        int sentence_length = sentTokens.size();
        String[] tags = new String[sentence_length];
        Arrays.fill(tags, "O");

        annotations.forEach(
                annotation -> insertTags(
                        annotation,
                        sentTokens,
                        tags,
                        annTag
                )
        );

        return String.join(" ", tags);
    }

    public static String getSequenceInstance( Sentence sentence,
                                              List<Annotation> annotations,
                                              String annTag ){
        return GenericUtils.getSequenceInstance(
                JCasUtil.selectCovered(BaseToken.class, sentence)
                        .stream()
                        .sorted(Comparator.comparingInt(BaseToken::getBegin))
                        .collect(Collectors.toList()),
                annotations,
                annTag
        );
    }

    public static String getSequenceInstance( Sentence sentence,
                                              List<Annotation> annotations ){
        return GenericUtils.getSequenceInstance(sentence, annotations, "");
    }

    public static String getSequenceInstance( Sentence sentence,
                                              Class<? extends Annotation> type,
                                              String task ){
        return GenericUtils.getSequenceInstance(
                // sorting is probably unnecessary but just in case
                JCasUtil.selectCovered(BaseToken.class, sentence)
                        .stream()
                        .sorted(Comparator.comparingInt(BaseToken::getBegin))
                        .collect(Collectors.toList()),
                GenericUtils.getPrimeAnnotations(JCasUtil.selectCovered(type, sentence))
                        .stream()
                        .sorted(Comparator.comparingInt(Annotation::getBegin))
                        .collect(Collectors.toList()),
                task
        );
    }

    static String relToStr( BinaryTextRelation rel , Sentence sentence ){
        String label = rel.getCategory();
        Annotation arg1 = rel.getArg1().getArgument();
        Annotation arg2 = rel.getArg2().getArgument();

        List<BaseToken> sentTokens = JCasUtil.selectCovered(
                        org.apache.ctakes.typesystem.type.syntax.BaseToken.class,
                        sentence
                )
                .stream()
                .sorted(Comparator.comparingInt(Annotation::getBegin))
                .collect(Collectors.toList());

        String inds = String.format(
                "%d-%d",
                GenericUtils.inexactStart( arg1, sentTokens ),
                GenericUtils.inexactStart( arg2, sentTokens )
        );

        return label + "_" + inds;
    }

    static String annotationInfo( Annotation annotation ){
        return String.format(
                "%s of type %s at ( %d , %d )",
                (annotation instanceof Sentence) ? getTokenStringForSentence( (Sentence) annotation) : annotation.getCoveredText(),
                annotation.getClass().getSimpleName(),
                annotation.getBegin(),
                annotation.getEnd()
        );
    }

    static String buildMetrics ( Map<String, Integer> task2Count ){
        StringBuilder nerStr = new StringBuilder();
        StringBuilder reStr = new StringBuilder();


        List<String> nerTasks = Arrays.asList(
                "medication",
                "dosage",
                "duration",
                "form",
                "frequency",
                "route",
                "strength"
        );

        List<String> reTasks = Arrays.asList(
                "med-duration",
                "med-dosage",
                "med-form",
                "med-frequency",
                "med-route",
                "med-strength"
        );

        nerTasks.forEach(
                key -> {
                    if (task2Count.containsKey(key)) {
                        nerStr.append(
                                String.format(
                                        "%s:\t%d\n",
                                        key,
                                        task2Count.get(key)
                                )
                        );
                    }
                }
        );

        reTasks.forEach(
                key -> {
                    if (task2Count.containsKey(key)) {
                        reStr.append(
                                String.format(
                                        "%s:\t%d\n",
                                        key,
                                        task2Count.get(key)
                                )
                        );
                    }
                }
        );

        return "NER:\n\n" + nerStr + "\n\nRE:\n\n"  + reStr;
    }
}
