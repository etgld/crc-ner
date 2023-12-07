import os
import logging
import pandas as pd

from transformers import pipeline
from ctakes_pbj.component import cas_annotator
from ctakes_pbj.pbj_tools import create_type
from ctakes_pbj.type_system import ctakes_types
from typing import List, Tuple, Dict, Optional
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
from collections import defaultdict

logger = logging.getLogger(__name__)


def tokens_and_map(
    cas: Cas, context: Optional[FeatureStructure] = None
) -> Tuple[List[str], List[Tuple[int, int]]]:
    base_tokens = []
    token_map = []
    newline_tokens = cas.select(ctakes_types.NewlineToken)
    newline_token_indices = {(item.begin, item.end) for item in newline_tokens}

    token_collection = (
        cas.select(ctakes_types.BaseToken)
        if context is None
        else cas.select_covered(ctakes_types.BaseToken, context)
    )
    for base_token in sorted(token_collection, key=lambda t: t.begin):
        if (base_token.begin, base_token.end) not in newline_token_indices:
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
        begin, end = token_boundaries
        if begin in inverse_map.keys():
            logger.warn(
                f"pre-existing token begin entry {begin} -> {inverse_map[begin]} in reverse token map"
            )

        if end in inverse_map.keys():
            logger.warn(
                f"pre-existing token end entry {end} -> {inverse_map[end]} in reverse token map"
            )
        inverse_map[begin] = token_index
        inverse_map[end] = token_index
    return inverse_map


def get_conmod_instance(event, cas) -> str:
    raw_sentence = cas.select_covering(ctakes_types.Sentence, event)[0]
    tokens, token_map = tokens_and_map(cas, raw_sentence)
    inverse_map = invert_map(token_map)
    event_begin = inverse_map[event.begin] + 1
    event_end = inverse_map[event.end]
    str_builder = (
        tokens[:event_begin]
        + ["<e>"]
        + tokens[event_begin:event_end]
        + ["</e>"]
        + tokens[event_end:]
    )
    return " ".join(str_builder)


def get_tlink_instance(event, timex, cas, tokens, token_map) -> str:
    window_radius = 10
    return ""


def get_dtr_instance(event, cas, tokens, token_map) -> str:
    window_radius = 10
    return ""


def get_window_timexes(event):
    return []


class TimelineDelegator(cas_annotator.CasAnnotator):
    def __init__(self, cas):
        self.event_mention_type = cas.typesystem.get_type(ctakes_types.EventMention)
        self.timex_type = cas.typesystem.get_type(ctakes_types.TimeMention)
        self.tlink_type = cas.typesystem.get_type(ctakes_types.TemporalTextRelation)
        self.argument_type = cas.typesystem.get_type(ctakes_types.RelationArgument)
        self._dtr_path = None
        self._tlink_path = None
        self._conmod_path = None
        self.dtr_classifier = lambda _: []
        self.tlink_classifier = lambda _: []
        self.conmod_classifier = lambda _: []
        self.raw_events = defaultdict(list)

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
            result["label"]
            for result in filter(None, self.conmod_classifier(conmod_instances))
        )
        positive_chemo_mentions = (
            chemo
            for chemo, modality in zip(chemo_mentions, conmod_classifications)
            if modality == "ACTUAL"
        )

        self._write_positive_chemo_mentions(cas, positive_chemo_mentions)

    def _write_positive_chemo_mentions(self, cas, positive_chemo_mentions):
        cas_metadata_collection = cas.select(ctakes_types.Metadata)
        cas_metadata = list(cas_metadata_collection)[0]
        cas_source_data = cas_metadata.getSourceData()
        # in its normalized string form, maybe need some exceptions
        # for if it's missing describing the file spec
        document_creation_time = cas_source_data.getSourceOriginalDate()

        base_tokens, token_map = tokens_and_map(cas)

        dtr_instances = (
            get_dtr_instance(chemo, cas, base_tokens, token_map)
            for chemo in positive_chemo_mentions
        )
        # tlink_instances = (get_tlink_instance(chemo, cas, base_tokens, token_map) for chemo in positive_chemo_mentions)

        dtr_classifications = {
            chemo: result["label"]
            for chemo, result in zip(
                positive_chemo_mentions, self.dtr_classifier(dtr_instances)
            )
        }

        def tlink_result_dict(event):
            window_timexes = get_window_timexes(event)

            tlink_instances = (
                get_tlink_instance(event, timex, cas, base_tokens, token_map)
                for timex in window_timexes
            )

            return {
                timex: result["label"]
                for timex, result in zip(
                    window_timexes, self.tlink_classifier(tlink_instances)
                )
            }

        tlink_classifications = {
            chemo: tlink_result_dict(chemo) for chemo in positive_chemo_mentions
        }

        document_path_collection = cas.select(ctakes_types.DocumentPath)
        document_path = list(document_path_collection)[0]
        patient_id = os.path.basename(os.path.dirname(document_path))

        for chemo in positive_chemo_mentions:
            chemo_dtr = dtr_classifications[chemo]
            for timex, chemo_timex_rel in tlink_classifications[chemo]:
                self.raw_events[patient_id].append(
                    [
                        document_creation_time,
                        chemo.get_covered_text(),
                        chemo_dtr,
                        timex.get_covered_text(),
                        chemo_timex_rel,
                    ]
                )

    def collection_process_complete(self):
        # Per 12/6/23 meeting, summarization is done
        # outside the Docker to maximize the ability for the user
        # to customize everything.  So we just write the raw results
        # to tsv
        for pt_id, records in self.raw_events.items():
            pt_df = pd.DataFrame.from_records(
                records, columns=["DCT", "chemo_text", "dtr", "timex", "tlink"]
            )
            pt_df.to_csv(f"{pt_id}_raw.tsv", index=False, sep="\t")
