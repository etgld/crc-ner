"""
isort:skip_file
"""
# Core python imports

import logging
from typing import List

import torch
# intra-library imports
from cnlpt.CnlpModelForClassification import CnlpModelForClassification, CnlpConfig
from cnlpt.cnlp_data import cnlp_preprocess_data
from datasets import Dataset
# FastAPI imports
from pydantic import BaseModel
# Modeling imports
from transformers import (
    AutoConfig,
    AutoModel,
    AutoTokenizer,
    HfArgumentParser,
    Trainer,
    TrainingArguments,
)


class UnannotatedDocument(BaseModel):
    doc_text: str


class EntityDocument(BaseModel):
    """doc_text: The raw text of the document
    offset:  A list of entities, where each is a tuple of character offsets into doc_text for that entity
    """

    doc_text: str
    entities: List[List[int]]


def get_dataset(
        inst_list,
        tokenizer,
        max_length: int = 128,
        hier: bool = False,
        chunk_len: int = 200,
        num_chunks: int = 40,
        insert_empty_chunk_at_beginning: bool = False,
):
    dataset = Dataset.from_dict({"text": inst_list})
    task_dataset = dataset.map(
        cnlp_preprocess_data,
        batched=True,
        load_from_cache_file=False,
        desc="Running tokenizer on dataset, organizing labels, creating hierarchical segments if necessary",
        batch_size=100,
        fn_kwargs={
            "tokenizer": tokenizer,
            "max_length": max_length,
            "inference": True,
            "hierarchical": hier,
            # TODO: need to get this from the model if necessary
            "chunk_len": chunk_len,
            "num_chunks": num_chunks,
            "insert_empty_chunk_at_beginning": insert_empty_chunk_at_beginning,
            "truncate_examples": True,
        },
    )
    return task_dataset


def create_entity_instance_string(doc_text: str, offsets: List[int]):
    start = max(0, offsets[0] - 100)
    end = min(len(doc_text), offsets[1] + 100)
    raw_str = (
            doc_text[start: offsets[0]]
            + " <e> "
            + doc_text[offsets[0]: offsets[1]]
            + " </e> "
            + doc_text[offsets[1]: end]
    )
    return raw_str.replace("\n", " ")


def initialize_model(app, model_name, cuda=True, batch_size=8, mode="cnlpt"):
    args = [
        "--output_dir",
        "save_run/",
        "--per_device_eval_batch_size",
        str(batch_size),
        "--do_predict",
        "--report_to",
        "none",
    ]
    parser = HfArgumentParser((TrainingArguments,))
    (training_args,) = parser.parse_args_into_dataclasses(args=args)

    app.state.training_args = training_args

    if mode == "cnlpt":
        AutoConfig.register("cnlpt", CnlpConfig)
        AutoModel.register(CnlpConfig, CnlpModelForClassification)

    config = AutoConfig.from_pretrained(model_name)
    app.state.config = config
    app.state.tokenizer = AutoTokenizer.from_pretrained(model_name, config=config)
    # can probably squash to one step but being safe
    model = CnlpModelForClassification.from_pretrained(
        model_name, config=config
    ) if mode == "cnlpt" else AutoModel.from_pretrained(model_name, config=config)
    if cuda and not torch.cuda.is_available():
        logging.warning(
            "CUDA is set to True (probably a default) but was not available; setting to False and proceeding. If you have a GPU you need to debug why pytorch cannot see it."
        )
        cuda = False

    if cuda:
        model = model.to("cuda")
    else:
        model = model.to("cpu")

    app.state.model = model
    app.state.trainer = Trainer(
        model=model,
        args=app.state.training_args,
        compute_metrics=None,
    )
