# ChemoTimelines Docker

Dockerizable source code for the author system for the [Chemotherapy Treatment Timelines Extraction from the Clinical Narrative](https://sites.google.com/view/chemotimelines2024/task-descriptions) shared task.

## Overview of Docker dependencies

- [Docker Engine](https://docs.docker.com/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html)

The image (by default) requires three folders for the TLINK, DTR, and contexual modality Huggingface classifiers at the top of the repository with the respective names `tlink`, `dtr`, and `conmod`.

## Data directories

There are three mounted directories:

- *Input*: Each subfolder should be a patient identifier, the contents of the subfolder should be the patient's notes
- *Processing*: Timeline information extraction over each note within cTAKES, aggregation of results by patient identifier
- *Output*: Aggregated timelines information, one `tsv` file per patient identifier 

## Build docker image

Under the project root directory ( you may need to use `sudo` ):

```
docker compose build --no-cache
```


## Start a container

You may need `sudo` here as well:

```
docker compose up
```
## Core Dependencies

There are three main separate software packages that this code uses:
- [Apache cTAKES](https://github.com/apache/ctakes)
- [CLU Lab Timenorm](https://github.com/clulab/timenorm)
- [Huggingface Transformers](https://huggingface.co/docs/transformers/index)


cTAKES contains several tools for text engineering and information extraction with a focus on clinical text, it is based on [Apache UIMA](https://uima.apache.org).
Within cTAKES the main module which drives this code is the cTAKES [Python Bridge to Java](https://github.com/apache/ctakes/tree/main/ctakes-pbj).
While cTAKES is written in Java, the Python Bridge to Java (*ctakes-pbj*) allows for use of python code to process text artifacts the same way one can do 
with Java within cTAKES.  *ctakes-pbj* accomplishes this by passing text artifacts and their annotated information between the relevant Java and Python processes 
using [DKPro cassis]( https://github.com/dkpro/dkpro-cassis) for serialization, [Apache ActiveMQ]( https://activemq.apache.org) for message brokering, and [stomp.py](https://github.com/jasonrbriggs/stomp.py) for Python-side receipt from and transmission to ActiveMQ.  

Timenorm provides methods for identifying normalizing date and time expressions.  We use a customized version (included as a maven module) where we change a heuristic for approximate dates.

We used Huggingface Transformers for training the TLINK model, and use their [Pipelines interface](https://huggingface.co/docs/transformers/main_classes/pipelines) for loading the model for inference.


## Architecture

We use two maven modules, one for the Java and Python annotators relevant to processing the clinical notes, and the other which has the customized version of Timenorm.

### Core command

The central command in the Docker (you can also run it outside the Docker with the appropriate dependencies):
```
java -cp instance-generator/target/instance-generator-5.0.0-SNAPSHOT-jar-with-dependencies.jar \
     org.apache.ctakes.core.pipeline.PiperFileRunner \
     -p org/apache/ctakes/timelines/pipeline/Timelines \
     -a  mybroker \
     -i ../input/ \
     -o ../output \
     -l org/apache/ctakes/dictionary/lookup/fast/bsv/Unified_Gold_Dev.xml \
     --pipPbj yes \
```
The `org.apache.ctakes.core.pipeline.PiperFileRunner` class is the entry point. `-a mybroker` points to the ActiveMQ broker for the process (you can see how to set one up in the Dockerfile). 

### (Optional) running the core command outside of the Docker

Add to the core command `-v <path to conda environment>** to run with a specified conda environment.  Ideally, cTAKES should start the ActiveMQ broker by itself when you run the command.  However we have had to start the broker ourselves before running the command via 
```
mybroker/bin/artemis run &
```
And stopping it after the run with:
```
mybroker/bin/artemis stop
```
## The piper file

The piper file at `org/apache/ctakes/timelines/pipeline/Timelines` describes the flow logic of the information extraction, e.g. the annotators involved, the order in which they are run, as well as their configuration parameters.

The contents of the piper file by default are:
```
package org.apache.ctakes.timelines

set SetJavaHome=no
set ForceExit=no
cli DTRModelPath=d
cli ModalityModelPath=m
cli TlinkModelPath=t

load PbjStarter

add PythonRunner Command="-m pip install resources/org/apache/ctakes/timelines/timelines_py" Wait=yes

set TimelinesSecondStep=timelines.timelines_pipeline

add PythonRunner Command="-m $TimelinesSecondStep -rq JavaToPy -o $OutputDirectory"

set minimumSpan=2
set exclusionTags=“”

// Just the components we need from DefaultFastPipeline
set WriteBanner=yes

// Load a simple token processing pipeline from another pipeline file
load DefaultTokenizerPipeline

// Add non-core annotators
add ContextDependentTokenizerAnnotator
// Dictionary module requires tokens so needs to be loaded after the tokenization stack
load DictionarySubPipe

add BackwardsTimeAnnotator classifierJarPath=/org/apache/ctakes/temporal/models/timeannotator/model.jar
add DCTAnnotator
add TimeMentionNormalizer timeout=10

add PbjJmsSender SendQueue=JavaToPy SendStop=yes
```

## Questions and Technical Issues

Please contact [Eli Goldner](mailto:eli.goldner@childrens.harvard.edu?subject=Timelines%20Docker%20Issue/Question) for non code-level issues or questions.  For issues in the code please open an issue through the repository page on GitHub.
