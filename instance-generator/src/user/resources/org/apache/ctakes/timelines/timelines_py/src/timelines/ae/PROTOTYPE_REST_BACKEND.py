# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import logging
import os
from time import time
from typing import Dict, List, Tuple, Union

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel
from seqeval.metrics.sequence_labeling import get_entities

from cnlpt.CnlpModelForClassification import CnlpConfig, CnlpModelForClassification
from .cnlp_rest import create_instance_string, get_dataset, initialize_cnlpt_model

app = FastAPI()
logger = logging.getLogger("CNLPT_LEVEL_TIMELINE_REST_PROTOTYPE")
logger.setLevel(logging.INFO)



class SentenceDocument(BaseModel):
    sentence: str


class TokenizedSentenceDocument(BaseModel):
    """sent_tokens: a list of sentences, where each sentence is a list of tokens"""

    sent_tokens: List[List[str]]
    metadata: str


class Timex(BaseModel):
    begin: int
    end: int
    timeClass: str


class Event(BaseModel):
    begin: int
    end: int
    dtr: str


class Relation(BaseModel):
    # Allow args to be none, so that we can potentially link them to times or events in the client, or if they don't
    # care about that. pass back the token indices of the args in addition.
    arg1: Union[str, None]
    arg2: Union[str, None]
    category: str
    arg1_start: int
    arg2_start: int


class TemporalResults(BaseModel):
    """lists of timexes, events and relations for list of sentences"""

    timexes: List[List[Timex]]
    events: List[List[Event]]
    relations: List[List[Relation]]


def create_instance_string(tokens: List[str]):
    return " ".join(tokens)


@app.on_event("startup")
async def startup_event(
        dtr_path: str,
        tlink_path: str,
        mod_path: str,
):
    pass


def rest():
    import argparse

    parser = argparse.ArgumentParser(description="Run the http server for temporal")
    parser.add_argument(
        "-p",
        "--port",
        type=int,
        help="The port number to run the server on",
        default=8000,
    )

    args = parser.parse_args()

    import uvicorn

    uvicorn.run(
        "cnlpt.api.temporal_rest:app", host="0.0.0.0", port=args.port, reload=True
    )


if __name__ == "__main__":
    rest()
