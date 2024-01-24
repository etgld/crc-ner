java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
     org.apache.ctakes.core.pipeline.PiperFileRunner \
     -p org/apache/ctakes/timelines/pipeline/Timelines \
     -a  mybroker \
     -v /usr/local/miniconda3/envs/timelines-docker \
     -i ../input/ \
     -o ../output \
     -d ~/pubmedbert_models/dtr/ \
     -m ../conmod/ \
     -t  ~/pubmedbert_models/tlink/\
     -l org/apache/ctakes/dictionary/lookup/fast/bsv/Unified_Gold_Dev.xml \
     -u deepphe \
     -w deepphe \
     --pipPbj yes \
