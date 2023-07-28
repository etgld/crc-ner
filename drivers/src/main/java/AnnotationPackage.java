import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.jcas.tcas.Annotation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public class AnnotationPackage {
    public String sentence;
    public String annotationList;
    public boolean entity;
    public String label;
    public String filename;
    public String message;

    public void initialize(
            @Nullable Sentence sentence,
            List<Annotation> annotations,
            boolean entity,
            String label,
            String filename
    ){
        this.sentence = (sentence == null) ? "EMPTY/NULL SENT" : GenericUtils.annotationInfo(sentence);
        this.annotationList = annotations
                .stream()
                .map(
                        GenericUtils::annotationInfo
                ).collect(Collectors.joining(" , "));
        this.entity = entity;
        this.label = label;
        this.filename = filename;
    }

    public String niceString(){
        String type = this.entity ? "Entity" : "Relation";
        String message = !(this.message == null || this.message.isEmpty()) ? String.format("problems: %s , ", this.message) : "";
        return String.format(
                    "filename: %s , annotation type: %s , %slabel: %s \n\n  %s \n\n",
                    this.filename,
                    type,
                    message,
                    this.label,
                    this.sentence + " : " + this.annotationList
            );
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null){
            return false;
        }

        if (this == obj){
            return true;
        }

        if (!(obj instanceof AnnotationPackage)){
            return false;
        }

        AnnotationPackage annObj = (AnnotationPackage) obj;

        boolean sentEq = this.sentence.equals(annObj.sentence);
        boolean annListEq = this.annotationList.equals(annObj.annotationList);
        boolean entEq = this.entity == annObj.entity;
        boolean labelEq = this.label.equals(annObj.label);
        boolean filenameEq = this.filename.equals(annObj.filename);
        return sentEq && annListEq && entEq && labelEq && filenameEq;
    }

    @Override
    public int hashCode() {
        return this.sentence.hashCode() ^ this.annotationList.hashCode() ^ this.label.hashCode() ^ this.filename.hashCode();
    }
}
