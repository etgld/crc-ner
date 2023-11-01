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
from time import time
from typing import List

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel

from .cnlp_rest import (
    EntityDocument,
    create_instance_string,
    get_dataset,
    initialize_model,
)

app = FastAPI()
logger = logging.getLogger("Conmod Processor")
logger.setLevel(logging.DEBUG)

task = "Conmod"
labels = [-1, 1]

max_length = 128


class ConmodResults(BaseModel):
    # TODO - VERIFY!!!
    """modalities: dictionary from entity id to classification decision about modality; true -> actual,
    false -> hypothetical"""

    modalities: List[int]


@app.on_event("startup")
async def startup_event(conmod_path):
    initialize_model(app, conmod_path)


@app.post("/conmod/process")
async def process(doc: EntityDocument):
    doc_text = doc.doc_text
    logger.info(
        f"Received document of len {len(doc_text)} to process with {len(doc.entities)} entities"
    )
    instances = []
    start_time = time()

    if len(doc.entities) == 0:
        return ConmodResults(statuses=[])

    for ent_ind, offsets in enumerate(doc.entities):
        logger.info(f"Entity ind: {ent_ind} has offsets ({offsets[0]}, {offsets[1]})")
        inst_str = create_instance_string(doc_text, offsets)
        logger.info(f"Instance string is {inst_str}")
        instances.append(inst_str)

    # TODO - inherit all the relevant dataset arguments from the
    # overall configuration from the model and tokenizer ( notionally stored in app.state )
    dataset = get_dataset(instances, app.state.tokenizer, max_length)
    preproc_end = time()

    output = app.state.trainer.predict(test_dataset=dataset)
    predictions = output.predictions[0]
    predictions = np.argmax(predictions, axis=1)

    pred_end = time()

    results = []
    for ent_ind in range(len(dataset)):
        results.append(labels[predictions[ent_ind]])

    output = ConmodResults(statuses=results)

    postproc_end = time()

    preproc_time = preproc_end - start_time
    pred_time = pred_end - preproc_end
    postproc_time = postproc_end - pred_end

    logging.warning(
        f"Pre-processing time: {preproc_time}, processing time: {pred_time}, post-processing time {postproc_time}"
    )

    return output


@app.get("/conmod/{test_str}")
async def test(test_str: str):
    return {"argument": test_str}


def rest():
    import argparse

    parser = argparse.ArgumentParser(description="Run the http server for conmod")
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
        "timelines.ae.conmod_rest:app", host="0.0.0.0", port=args.port, reload=True
    )


if __name__ == "__main__":
    rest()
