rm -rf resources;
rm -rf instance-generator/resources;
rm -rf tweaked-timenorm/resources;
mvn clean package;
java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
     org.apache.ctakes.core.pipeline.PiperFileRunner \
     -p org/apache/ctakes/timelines/pipeline/Timelines \
     -a  ~/apache-artemis-2.19.1/bin/rt_broker/ \
     -v /usr/local/miniconda3/envs/timelines-docker \
     -i ../input/ \
     -o ../output \
     -d ../dtr/ \
     -m ../conmod/ \
     -t ../tlink/ \
     -l org/apache/ctakes/dictionary/lookup/fast/bsv/Chemotherapy.xml \
     --pipPbj yes \
     --key 9302930b-26f3-497d-8b32-3277c257293c
