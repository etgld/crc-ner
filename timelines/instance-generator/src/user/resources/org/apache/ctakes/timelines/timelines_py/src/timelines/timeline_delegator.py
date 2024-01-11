import os
import logging
import torch
import pandas as pd
from pprint import pprint

from itertools import chain
from transformers import pipeline
from ctakes_pbj.component import cas_annotator
from ctakes_pbj.type_system import ctakes_types
from typing import List, Tuple, Dict, Optional, Generator, Iterator, Union, Set
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

DTR_WINDOW_RADIUS = 10
MAX_TLINK_DISTANCE = 60
TLINK_PAD_LENGTH = 2
MODEL_MAX_LEN = 512
SPECIAL_TOKENS = [
    "<e>",
    "</e>",
    "<a1>",
    "</a1>",
    "<a2>",
    "</a2>",
    "<cr>",
    "<neg>",
    "<newline>",
]
CHEMO_TUI = "T061"


def normlize_mention(mention: Union[FeatureStructure, None]) -> str:
    if mention is not None:
        (raw_mention_text,) = mention.get_covered_text()
        return raw_mention_text.replace("\n", "")
    return "ERROR"


def tokens_and_map(
    cas: Cas, context: Optional[FeatureStructure] = None, mode="conmod"
) -> Tuple[List[str], List[Tuple[int, int]]]:
    base_tokens = []
    token_map = []
    newline_tag = "<cr>" if mode == "conmod" else "<newline>"
    newline_tokens = cas.select(ctakes_types.NewlineToken)
    newline_token_indices = {(item.begin, item.end) for item in newline_tokens}
    # duplicates = defaultdict(list)

    raw_token_collection = (
        cas.select(ctakes_types.BaseToken)
        if context is None
        else cas.select_covered(ctakes_types.BaseToken, context)
    )

    token_collection: Dict[int, Tuple[int, str]] = {}
    for base_token in raw_token_collection:
        begin = base_token.begin
        end = base_token.end
        token_text = (
            base_token.get_covered_text()
            if (begin, end) not in newline_token_indices
            else newline_tag
        )
        # TODO - ask Sean and Guergana why there might be duplicate Newline tokens
        # if begin in token_collection:
        #     prior_end, prior_text = token_collection[begin]
        #     print(
        #         f"WARNING: two tokens {(token_text, begin, end)} and {(prior_text, begin, prior_end)} share the same begin index, overwriting with latest"
        #     )
        token_collection[begin] = (end, token_text)
    for begin in sorted(token_collection):
        end, token_text = token_collection[begin]
        base_tokens.append(token_text)
        token_map.append((begin, end))

    return base_tokens, token_map


def invert_map(
    token_map: List[Tuple[int, int]]
) -> Tuple[Dict[int, int], Dict[int, int]]:
    begin_map: Dict[int, int] = {}
    end_map: Dict[int, int] = {}
    for token_index, token_boundaries in enumerate(token_map):
        begin, end = token_boundaries
        # these warnings are kind of by-passed by previous logic
        # since any time two tokens shared a begin and or an end
        # it was always a newline token and its exact duplicate
        if begin in begin_map:
            print(
                f"pre-existing token begin entry {begin} -> {begin_map[begin]} against {token_index} in reverse token map"
            )
            print(
                f"full currently stored token info {begin_map[begin]} -> {token_map[begin_map[begin]]} against current candidate {token_index} -> {(begin, end)}"
            )

        if end in end_map:
            print(
                f"pre-existing token end entry {end} -> {end_map[end]} against {token_index} in reverse token map"
            )
            print(
                f"full currently stored token info {end_map[end]} -> {token_map[end_map[end]]} against current candidate {token_index} -> {(begin, end)}"
            )
        begin_map[begin] = token_index
        end_map[end] = token_index
    return begin_map, end_map


def get_conmod_instance(event: FeatureStructure, cas: Cas) -> str:
    raw_sentence = list(cas.select_covering(ctakes_types.Sentence, event))[0]
    tokens, token_map = tokens_and_map(cas, raw_sentence, mode="conmod")
    begin2token, end2token = invert_map(token_map)
    event_begin = begin2token[event.begin]
    event_end = end2token[event.end] + 1
    str_builder = (
        tokens[:event_begin]
        + ["<e>"]
        + tokens[event_begin:event_end]
        + ["</e>"]
        + tokens[event_end:]
    )
    result = " ".join(str_builder)
    return result


def timexes_with_normalization(
    timexes: List[FeatureStructure],
) -> List[FeatureStructure]:
    def relevant(timex):
        return hasattr(timex, "time") and hasattr(timex.time, "normalizedForm")

    return [timex for timex in timexes if relevant(timex)]


def get_tlink_instance(
    event: FeatureStructure,
    timex: FeatureStructure,
    tokens: List[str],
    begin2token: Dict[int, int],
    end2token: Dict[int, int],
) -> str:
    # Have an event and a timex which are up to 60 tokens apart from each other
    # have two tokens before first annotation, first annotation plus tags
    # then all the text between the two annotations
    # second annotation plus tags, the last two tokens after the second annotation
    event_begin = begin2token[event.begin]
    event_end = end2token[event.end] + 1
    event_tags = ("<e>", "</e>")
    event_packet = (event_begin, event_end, event_tags)
    timex_begin = begin2token[timex.begin]
    timex_end = end2token[timex.end] + 1
    timex_tags = ("<t>", "</t>")
    timex_packet = (timex_begin, timex_end, timex_tags)

    first_packet, second_packet = sorted(
        (event_packet, timex_packet), key=lambda s: s[0]
    )
    (first_begin, first_end, first_tags) = first_packet
    (first_open_tag, first_close_tag) = first_tags

    (second_begin, second_end, second_tags) = second_packet
    (second_open_tag, second_close_tag) = second_tags

    # to avoid wrap arounds
    start_token_idx = max(0, first_begin - TLINK_PAD_LENGTH)
    end_token_idx = min(len(tokens) - 1, second_end + TLINK_PAD_LENGTH)

    str_builder = (
        # first two tokens
        tokens[start_token_idx:first_begin]
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
        + tokens[second_end:end_token_idx]
    )
    result = " ".join(str_builder)
    # print(f"tlink result: {result}")
    return result


def get_dtr_instance(
    event: FeatureStructure,
    tokens: List[str],
    begin2token: Dict[int, int],
    end2token: Dict[int, int],
) -> str:
    event_begin = begin2token[event.begin]
    event_end = end2token[event.end] + 1
    # window_tokens = tokens[event_begin - window_radius:event_end + window_radius - 1]
    str_builder = (
        tokens[event_begin - DTR_WINDOW_RADIUS : event_begin]
        + ["<e>"]
        + tokens[event_begin:event_end]
        + ["</e>"]
        + tokens[event_end : event_end + DTR_WINDOW_RADIUS]
    )
    result = " ".join(str_builder)
    return result


def get_tlink_window_mentions(
    event: FeatureStructure,
    relevant_mentions: Iterator[FeatureStructure],
    begin2token: Dict[int, int],
    end2token: Dict[int, int],
    token2char: List[Tuple[int, int]],
) -> Generator[FeatureStructure, None, None]:
    event_begin_token_index = begin2token[event.begin]
    event_end_token_index = end2token[event.end]

    token_window_begin = max(0, event_begin_token_index - MAX_TLINK_DISTANCE)
    token_window_end = min(
        len(token2char) - 1, event_end_token_index + MAX_TLINK_DISTANCE
    )

    char_window_begin = token2char[token_window_begin][0]
    char_window_end = token2char[token_window_end][1]

    def in_window(mention):
        begin_inside = char_window_begin <= mention.begin <= char_window_end
        end_inside = char_window_begin <= mention.end <= char_window_end
        return begin_inside and end_inside

    # return [mention for mention in cas.select(mention_type) if in_window(mention)]

    for mention in relevant_mentions:
        if in_window(mention):
            yield mention


def deleted_neighborhood(
    central_mention: FeatureStructure, mentions: List[FeatureStructure]
) -> Generator[FeatureStructure, None, None]:
    for mention in mentions:
        if central_mention != mention:
            yield mention


def pt_and_note(cas: Cas):
    document_path_collection = cas.select(ctakes_types.DocumentPath)
    document_path = list(document_path_collection)[0].documentPath
    patient_id = os.path.basename(os.path.dirname(document_path))
    note_name = os.path.basename(document_path).split(".")[0]
    return patient_id, note_name


def get_tuis(event: FeatureStructure) -> Set[str]:
    def get_tui(event):
        return getattr(event, "tui", None)

    ont_concept_arr = getattr(event, "ontologyConceptArr", None)
    elements = getattr(ont_concept_arr, "elements", [])
    if len(elements) > 0:
        return set([*filter(None, map(get_tui, elements))])
    return set()


class TimelineDelegator(cas_annotator.CasAnnotator):
    def __init__(self):
        self._dtr_path = None
        self._tlink_path = None
        self._conmod_path = None
        self.dtr_classifier = lambda _: []
        self.tlink_classifier = lambda _: []
        self.conmod_classifier = lambda _: []
        self.raw_events = defaultdict(list)

    def init_params(self, arg_parser):
        self._dtr_path = arg_parser.dtr_path
        self._tlink_path = arg_parser.tlink_path
        self._conmod_path = arg_parser.conmod_path

    def initialize(self):
        if torch.cuda.is_available():
            main_device = 0
            print("GPU with CUDA is available, using GPU")
        else:
            main_device = -1
            print("GPU with CUDA is not available, defaulting to CPU")
        self.dtr_classifier = pipeline(
            "text-classification",
            model=self._dtr_path,
            tokenizer=self._dtr_path,
            device=main_device,  # here and for the rest we specify the device since pipelines default to CPU
            padding=True,
            truncation=True,
            max_length=MODEL_MAX_LEN,
        )

        print("DTR classifier loaded")

        self.tlink_classifier = pipeline(
            "text-classification",
            model=self._tlink_path,
            tokenizer=self._tlink_path,
            device=main_device,
            padding=True,
            truncation=True,
            max_length=MODEL_MAX_LEN,
        )

        print("TLINK classifier loaded")

        self.conmod_classifier = pipeline(
            "text-classification",
            model=self._conmod_path,
            tokenizer=self._conmod_path,
            device=main_device,
            padding=True,
            truncation=True,
            max_length=MODEL_MAX_LEN,
        )

        print("Conmod classifier loaded")

    def declare_params(self, arg_parser):
        arg_parser.add_arg("--dtr_path")
        arg_parser.add_arg("--tlink_path")
        arg_parser.add_arg("--conmod_path")

    # Process Sentences, adding Times, Events and TLinks found by cNLPT.
    def process(self, cas: Cas):
        proc_mentions = [
            event
            for event in cas.select(cas.typesystem.get_type(ctakes_types.EventMention))
            if CHEMO_TUI
            in get_tuis(event)  # as of 1/10/24, using T061 which is ProcedureMention
        ]

        if len(proc_mentions) > 0:
            self._write_raw_timelines(cas, proc_mentions)
        else:
            patient_id, note_name = pt_and_note(cas)
            print(
                f"No chemotherapy mentions ( using TUI: {CHEMO_TUI} ) found in patient {patient_id} note {note_name} skipping"
            )

    def collection_process_complete(self):
        print("Finished processing notes")
        for pt_id, records in self.raw_events.items():
            print(f"Writing results for {pt_id}")
            pt_df = pd.DataFrame.from_records(
                records,
                columns=[
                    "DCT",
                    "chemo",
                    "dtr",
                    "normed_timex",
                    "other_chemo",
                    "tlink",
                    "note_name",
                    "dtr_inst",
                    "tlink_inst",
                ],
            )
            pt_df.to_csv(f"{pt_id}_raw.tsv", index=False, sep="\t")

    def _write_raw_timelines(self, cas: Cas, proc_mentions: List[FeatureStructure]):
        conmod_instances = (get_conmod_instance(chemo, cas) for chemo in proc_mentions)

        conmod_classifications = (
            result["label"]
            for result in filter(None, self.conmod_classifier(conmod_instances))
        )
        actual_proc_mentions = [
            chemo
            for chemo, modality in zip(proc_mentions, conmod_classifications)
            if modality == "ACTUAL"
        ]
        if len(actual_proc_mentions) > 0:
            self._write_actual_proc_mentions(cas, actual_proc_mentions)
        else:
            patient_id, note_name = pt_and_note(cas)
            print(
                f"No concrete chemotherapy mentions found in patient {patient_id} note {note_name} skipping"
            )

    def _write_actual_proc_mentions(
        self, cas: Cas, positive_chemo_mentions: List[FeatureStructure]
    ):
        timex_type = cas.typesystem.get_type(ctakes_types.TimeMention)
        event_type = cas.typesystem.get_type(ctakes_types.EventMention)
        cas_source_data = cas.select(ctakes_types.Metadata)[0].sourceData
        # in its normalized string form, maybe need some exceptions
        # for if it's missing describing the file spec
        document_creation_time = cas_source_data.sourceOriginalDate
        relevant_timexes = timexes_with_normalization(cas.select(timex_type))

        base_tokens, token_map = tokens_and_map(cas, mode="dtr")
        begin2token, end2token = invert_map(token_map)

        def dtr_result(chemo):
            inst = get_dtr_instance(chemo, base_tokens, begin2token, end2token)
            result = list(self.dtr_classifier(inst))[0]
            label = result["label"]
            return label, inst

        def tlink_result_dict(chemo):
            relevant_mentions = chain.from_iterable(
                deleted_neighborhood(chemo, positive_chemo_mentions), relevant_timexes
            )
            window_mentions = get_tlink_window_mentions(
                chemo, relevant_mentions, begin2token, end2token, token_map
            )

            begin_end_maps = begin2token, end2token
            return self.tlink_result_dict(
                event=chemo,
                window_mentions=window_mentions,
                begin_end_maps=begin_end_maps,
                base_tokens=base_tokens,
            )

        patient_id, note_name = pt_and_note(cas)
        if len(list(relevant_timexes)) == 0:
            print(
                f"WARNING: No normalized timexes discovered in {patient_id} file {note_name}"
            )
        for chemo in positive_chemo_mentions:
            chemo_dtr, dtr_inst = dtr_result(chemo)
            tlink_dict = tlink_result_dict(chemo)
            for other_mention, tlink_inst_pair in tlink_dict.items():
                tlink, tlink_inst = tlink_inst_pair
                chemo_text = normalize_mention(chemo)
                if other_mention.type == timex_type:
                    timex_text = other_mention.time.normalizedForm
                    other_chemo_text = "none"
                elif other_mention.type == event_type:
                    timex_text = "none"
                    other_chemo_text = normalize_mention(other_mention)
                else:
                    print(other_mention)
                    print(other_mention.type)
                    raw_text = (
                        other_mention.get_covered_text().replace("\n", "")
                        if other_mention is not None
                        else "ERROR"
                    )
                    timex_text = f"TYPE ERROR {raw_text}"
                    other_chemo_text = "TYPE ERROR"
                instance = [
                    document_creation_time,
                    chemo_text,
                    chemo_dtr,
                    timex_text,
                    other_chemo_text,
                    tlink,
                    note_name,
                    dtr_inst,
                    tlink_inst,
                ]
                self.raw_events[patient_id].append(instance)

    def tlink_result_dict(
        self,
        event: FeatureStructure,
        window_mentions: Generator[FeatureStructure, None, None],
        begin_end_maps: Tuple[Dict[int, int], Dict[int, int]],
        base_tokens: List[str],
    ) -> Dict[FeatureStructure, Tuple[str, str]]:
        begin2token, end2token = begin_end_maps
        tlink_instances = (
            get_tlink_instance(event, w_timex, base_tokens, begin2token, end2token)
            for w_timex in window_mentions
        )
        return {
            window_mention: (result["label"], inst)
            for window_mention, result, inst in zip(
                window_mentions, self.tlink_classifier(tlink_instances), tlink_instances
            )
        }
