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

There are two main separate software packages that this code uses:
- [Apache cTAKES](https://github.com/apache/ctakes)
- [CLU Lab Timenorm](https://github.com/clulab/timenorm)

cTAKES contains several tools for text engineering and information extraction with a focus on clinical text, it is based on [Apache UIMA](https://uima.apache.org).
Timenorm provides methods for identifying normalizing date and time expressions.
Within cTAKES the main module which drives this code is the cTAKES [Python Bridge to Java](https://github.com/apache/ctakes/tree/main/ctakes-pbj).
While cTAKES is written in Java, the Python Bridge to Java (`ctakes-pbj`) allows for use of python code to process text artifacts the same way one can do 
with Java within cTAKES.  `ctakes-pbj` accomplishes this by passing text artifacts and their annotated information between the relevant Java and Python processes 
using [DKPro cassis]( https://github.com/dkpro/dkpro-cassis) for serialization, [Apache ActiveMQ]( https://activemq.apache.org) for message brokering, and [stomp.py](https://github.com/jasonrbriggs/stomp.py) for Python-side receipt from and transmission to ActiveMQ.  


## Questions and Technical Issues

Please contact [Eli Goldner](mailto:eli.goldner@childrens.harvard.edu?subject=Timelines%20Docker%20Issue/Question) for non code-level issues or questions.  For issues in the code please open an issue through the repository page on GitHub.
