# ChemoTimelines Docker

Dockerizable source code for the author system for the [Chemotherapy Treatment Timelines Extraction from the Clinical Narrative](https://sites.google.com/view/chemotimelines2024/task-descriptions) shared task.

## Overview of tools

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

Under the project root directory:

```
docker compose build --no-cache
```

## Start a container

```
docker compose up
```
## Questions and Technical Issues

Please contact [Eli Goldner](mailto:eli.goldner@childrens.harvard.edu?subject=Timelines%20Docker%20Issue/Question)
