rm -rf resources;
rm -rf target;
rm -rf instance-generator/resources;
rm -rf instance-generator/target;
rm -rf tweaked-timenorm/resources;
rm -rf tweaked-timenorm/target;
mvn clean package;
# java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
    #      org.apache.ctakes.core.pipeline.PiperFileRunner \
    #      -p org/apache/ctakes/timelines/pipeline/Timelines \
    #      -a  mybroker \
    #      -v /usr/local/miniconda3/envs/timelines-docker \
    #      -i ../input/ \
    #      -o ../output \
    #      -d ../dtr/ \
    #      -m ../conmod/ \
    #      -t ../tlink/ \
    #      -l org/apache/ctakes/dictionary/lookup/fast/bsv/Chemotherapy.xml \
    #      -s 8161 \
    #      -r 8161 \
    #      --pipPbj yes \
    #      --key 9302930b-26f3-497d-8b32-3277c257293c
# java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
#      org.apache.ctakes.core.pipeline.PiperFileRunner \
#      -p org/apache/ctakes/timelines/pipeline/Timelines \
#      -a  mybroker \
#      -v /usr/local/miniconda3/envs/timelines-docker \
#      -i ../input/ \
#      -o ../output \
#      -d ../dtr/ \
#      -m ../conmod/ \
#      -t ../tlink/ \
#      -l org/apache/ctakes/dictionary/lookup/fast/bsv/Chemotherapy.xml \
#      --pipPbj yes \
#      --key 9302930b-26f3-497d-8b32-3277c257293c

java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
     org.apache.ctakes.core.pipeline.PiperFileRunner \
     -p org/apache/ctakes/timelines/pipeline/Timelines \
     -a  mybroker \
     -v /usr/local/miniconda3/envs/timelines-docker \
     -i ../input/ \
     -o ../output \
     -d ../dtr/ \
     -m ../conmod/ \
     -t ../tlink/ \
     -l org/apache/ctakes/dictionary/lookup/fast/bsv/Chemotherapy.xml \
     -u deepphe \
     -w deepphe \
     --pipPbj yes \
     --key 9302930b-26f3-497d-8b32-3277c257293c
