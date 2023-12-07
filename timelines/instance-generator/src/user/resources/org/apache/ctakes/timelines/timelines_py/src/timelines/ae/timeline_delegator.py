import time
import logging

from transformers import pipeline
from ctakes_pbj.component import cas_annotator
from ctakes_pbj.pbj_tools import create_type
from ctakes_pbj.pbj_tools.create_relation import create_relation
from ctakes_pbj.pbj_tools.event_creator import EventCreator
from ctakes_pbj.pbj_tools.helper_functions import get_covered_list
from ctakes_pbj.type_system import ctakes_types
from typing import List, Tuple, Dict
from cassis.typesystem import (
    # FEATURE_BASE_NAME_HEAD,
    # TYPE_NAME_FS_ARRAY,
    # TYPE_NAME_FS_LIST,
    # TYPE_NAME_SOFA,
    FeatureStructure,
    # Type,
    # TypeCheckError,
    # TypeSystem,
    # TypeSystemMode,
)

from cassis.cas import Cas

logger = logging.getLogger(__name__)


def tokens_and_map(cas: Cas) -> Tuple[List[str], List[Tuple[int, int]]]:
    base_tokens = []
    token_map = []
    newline_tokens = cas.select(ctakes_types.NewlineToken)
    newline_token_indices = {(item.begin, item.end) for item in newline_tokens}

    for base_token in sorted(cas.select(ctakes_types.BaseToken), key=lambda t: t.begin):
        if (
            (base_token.begin, base_token.end)
            not in newline_token_indices
            # and base_token.get_covered_text()
            # and not base_token.get_covered_text().isspace()
        ):
            base_tokens.append(base_token.get_covered_text())
            token_map.append((base_token.begin, base_token.end))
        else:
            # since these indices are tracked as well in the timelines code presumably
            base_tokens.append("<cr>")
            token_map.append((base_token.begin, base_token.end))
    return base_tokens, token_map


def invert_map(token_map: List[Tuple[int, int]]) -> Dict[int, int]:
    inverse_map = {}
    for token_index, token_boundaries in enumerate(token_map):
        for boundary in token_boundaries:
            if boundary in inverse_map.keys():
                logger.warn(
                    f"pre-existing entry {inverse_map[boundary]} in reverse token map"
                )
            inverse_map[boundary] = token_index
    return inverse_map


def get_conmod_instance(event, cas) -> str:
    return ""


def get_tlink_instance(event, timex, cas, tokens, token_map) -> str:
    return ""


def get_dtr_instance(event, cas, tokens, token_map) -> str:
    return ""


class TimelineDelegator(cas_annotator.CasAnnotator):
    def __init__(self, cas):
        self.event_mention_type = cas.typesystem.get_type(ctakes_types.EventMention)
        self.timex_type = cas.typesystem.get_type(ctakes_types.TimeMention)
        self.tlink_type = cas.typesystem.get_type(ctakes_types.TemporalTextRelation)
        self.argument_type = cas.typesystem.get_type(ctakes_types.RelationArgument)
        self._dtr_path = None
        self._tlink_path = None
        self._conmod_path = None
        self.dtr_classifier = None
        self.tlink_classifier = None
        self.conmod_classifier = None
        # TODO - we need to figure out if
        # how to represent patients, cancer types, etc
        # by which organize the result, for now just assume
        # everything in the input folder is the same patient
        self.raw_events = None

    def init_params(self, args):
        self._dtr_path = args.dtr_path
        self._tlink_path = args.tlink_path
        self._conmod_path = args.conmod_path

    def initialize(self):
        self.dtr_classifier = pipeline(
            "text-classification", model=self._dtr_path, tokenizer=self._dtr_path
        )

        self.tlink_classifier = pipeline(
            "text-classification", model=self._tlink_path, tokenizer=self._tlink_path
        )

        self.conmod_classifier = pipeline(
            "text-classification", model=self._conmod_path, tokenizer=self._conmod_path
        )

        self.raw_events = []

    def declare_params(self, arg_parser):
        arg_parser.add_arg("dtr_path")
        arg_parser.add_arg("tlink_path")
        arg_parser.add_arg("conmod_path")

    # Process Sentences, adding Times, Events and TLinks found by cNLPT.
    def process(self, cas: Cas):
        # TODO - will need CUI-based filtering later but for now assume everything is a chemo mention
        self.write_chemo_mentions(cas, cas.select(self.event_mention_type))

    def write_chemo_mentions(self, cas: Cas, chemo_mentions):
        # base_tokens, token_map = tokens_and_map(cas)

        # dtr_instances = (get_dtr_instance(chemo, cas, base_tokens, token_map) for chemo in positive_chemo_mentions)
        # tlink_instances = (get_tlink_instance(chemo, cas, base_tokens, token_map)
        # for chemo in positive_chemo_mentions)
        conmod_instances = (get_conmod_instance(chemo, cas) for chemo in chemo_mentions)
        conmod_classifications = (
            result["label"] for result in self.conmod_classifier(conmod_instances)
        )

        positive_chemo_mentions = (
            chemo
            for chemo, modality in zip(chemo_mentions, conmod_classifications)
            if modality == "ACTUAL"
        )

        self._write_positive_chemo_mentions(cas, positive_chemo_mentions)

    def _write_positive_chemo_mentions(self, cas, positive_chemo_mentions):
        # TODO - figure out how to get DCT from within PBJ using SourceData
        cas_metadata_collection = cas.select( ctakes_types.Metadata )


        base_tokens, token_map = tokens_and_map(cas)

        dtr_instances = (
            get_dtr_instance(chemo, cas, base_tokens, token_map)
            for chemo in positive_chemo_mentions
        )
        # tlink_instances = (get_tlink_instance(chemo, cas, base_tokens, token_map) for chemo in positive_chemo_mentions)

        dtr_classifications = (
            result["label"] for result in self.dtr_classifier(dtr_instances)
        )
        # tlink_classifications = (result["label"] for result in self.tlink_classifier(tlink_instances))

    # Called once at the end of the pipeline.
    def collection_process_complete(self):
        # TODO - summarization code here
        pass
