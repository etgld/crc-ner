# java -cp target/txtimelines-lookup-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
     org.apache.ctakes.core.pipeline.PiperFileRunner \
     -p org/apache/ctakes/timelines/pipeline/Timelines \
     -a mybroker \
     -v /usr/local/miniconda3/envs/timelines-docker \
     -i ../input/ \
     -o ../output \
     -l org/apache/ctakes/dictionary/lookup/fast/bsv/Chemotherapy.xml \
     -s 81616 \
     -r 81616 \
     --key 9302930b-26f3-497d-8b32-3277c257293c
