import os
import logging
import pandas as pd

from transformers import pipeline
from ctakes_pbj.component import cas_annotator
from ctakes_pbj.pbj_tools import create_type
from ctakes_pbj.type_system import ctakes_types
from typing import List, Tuple, Dict, Optional, Generator, Union
from cassis.typesystem import (
    # FEATURE_BASE_NAME_HEAD,
    # TYPE_NAME_FS_ARRAY,
    # TYPE_NAME_FS_LIST,
    # TYPE_NAME_SOFA,
    FeatureStructure,
    Type,
    # TypeCheckError,
    # TypeSystem,
    # TypeSystemMode,
)

from cassis.cas import Cas
from collections import defaultdict

logger = logging.getLogger(__name__)

_window_radius = 10


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


def get_conmod_instance(event: FeatureStructure, cas: Cas) -> str:
    raw_sentence = cas.select_covering(ctakes_types.Sentence, event)[0]
    tokens, token_map = tokens_and_map(cas, raw_sentence)
    char2token = invert_map(token_map)
    event_begin = char2token[event.begin]
    event_end = char2token[event.end] + 1
    str_builder = (
        tokens[:event_begin]
        + ["<e>"]
        + tokens[event_begin:event_end]
        + ["</e>"]
        + tokens[event_end:]
    )
    return " ".join(str_builder)


def get_tlink_instance(
    event: FeatureStructure,
    timex: FeatureStructure,
    tokens: List[str],
    char2token: Dict[int, int],
) -> str:
    event_begin = char2token[event.begin]
    event_end = char2token[event.end] + 1
    event_tags = ("<e>", "</e>")
    event_packet = (event_begin, event_end, event_tags)
    timex_begin = char2token[timex.begin]
    timex_end = char2token[timex.end] + 1
    timex_tags = ("<t>", "</t>")
    timex_packet = (timex_begin, timex_end, timex_tags)

    first_packet, second_packet = sorted(
        (event_packet, timex_packet), key=lambda s: s[0]
    )
    (first_begin, first_end, first_tags) = first_packet
    (first_open_tag, first_close_tag) = first_tags

    (second_begin, second_end, second_tags) = second_packet
    (second_open_tag, second_close_tag) = second_tags
    str_builder = (
        # since the window is around the event,
        # from the beginning to the first mention
        tokens[event_begin - _window_radius : first_begin]
        # tag body of the first mention
        + [first_open_tag]
        + tokens[first_begin:first_end]
        + [first_close_tag]
        # intermediate part of the window
        + tokens[first_end:second_begin]
        # tag body of the second mention
        + [second_open_tag]
        + tokens[second_begin:second_end]
        + [second_close_tag]
        # ending part of the window
        + tokens[second_end : event_end + _window_radius]
    )
    return " ".join(str_builder)


def get_dtr_instance(
    event: FeatureStructure, tokens: List[str], char2token: Dict[int, int]
) -> str:
    # raw_sentence = cas.select_covering(ctakes_types.Sentence, event)[0]
    # tokens, token_map = tokens_and_map(cas, raw_sentence)
    # inverse_map = invert_map(token_map)
    event_begin = char2token[event.begin]
    event_end = char2token[event.end] + 1
    # window_tokens = tokens[event_begin - window_radius:event_end + window_radius - 1]
    str_builder = (
        tokens[event_begin - _window_radius : event_begin]
        + ["<e>"]
        + tokens[event_begin:event_end]
        + ["</e>"]
        + tokens[event_end : event_end + _window_radius]
    )
    return " ".join(str_builder)


def get_window_mentions(
    event: FeatureStructure,
    cas: Cas,
    mention_type: Union[Type, str],
    char2token: Dict[int, int],
    token2char: List[Tuple[int, int]],
) -> Generator[FeatureStructure, None, None]:
    event_begin_token_index = char2token[event.begin]
    event_end_token_index = char2token[event.end]
    char_window_begin = token2char[event_begin_token_index][0]
    char_window_end = token2char[event_end_token_index][1]

    def in_window(index):
        return index >= char_window_begin and index <= char_window_end

    for mention in cas.select(mention_type):
        if in_window(mention.begin) and in_window(mention.end):
            yield mention


class TimelineDelegator(cas_annotator.CasAnnotator):
    def __init__(self):
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
        arg_parser.add_arg("--dtr_path")
        arg_parser.add_arg("--tlink_path")
        arg_parser.add_arg("--conmod_path")

    # Process Sentences, adding Times, Events and TLinks found by cNLPT.
    def process(self, cas: Cas):
        # TODO - will need CUI-based filtering later but for now assume everything is a chemo mention
        self.write_raw_timelines(
            cas, cas.select(cas.typesystem.get_type(ctakes_types.EventMention))
        )

    def write_raw_timelines(self, cas: Cas, chemo_mentions):
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
        timex_type = cas.typesystem.get_type(ctakes_types.TimeMention)
        cas_metadata_collection = cas.select(ctakes_types.Metadata)
        cas_metadata = list(cas_metadata_collection)[0]
        cas_source_data = cas_metadata.getSourceData()
        # in its normalized string form, maybe need some exceptions
        # for if it's missing describing the file spec
        document_creation_time = cas_source_data.getSourceOriginalDate()

        base_tokens, token_map = tokens_and_map(cas)
        char2token = invert_map(token_map)

        dtr_instances = (
            get_dtr_instance(chemo, base_tokens, char2token)
            for chemo in positive_chemo_mentions
        )

        dtr_classifications = {
            chemo: result["label"]
            for chemo, result in zip(
                positive_chemo_mentions, self.dtr_classifier(dtr_instances)
            )
        }

        def tlink_result_dict(event):
            window_timexes = get_window_mentions(
                event, cas, timex_type, char2token, token_map
            )
            tlink_instances = (
                get_tlink_instance(event, timex, base_tokens, char2token)
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
                        timex.get_covered_text() if timex is not None else "ERROR",
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
