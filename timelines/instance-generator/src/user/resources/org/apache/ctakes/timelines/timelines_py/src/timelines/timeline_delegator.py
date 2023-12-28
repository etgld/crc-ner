import os
import logging
import torch
import pandas as pd

# from transformers import pipeline
from transformers.pipelines import TextClassificationPipeline
from transformers import AutoConfig, AutoModelForSequenceClassification, AutoTokenizer
from ctakes_pbj.component import cas_annotator
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

WINDOW_RADIUS = 10
SPECIAL_TOKENS = ["<e>", "</e>", "<a1>", "</a1>", "<a2>", "</a2>", "<cr>", "<neg>"]


def tokens_and_map(
    cas: Cas, context: Optional[FeatureStructure] = None
) -> Tuple[List[str], List[Tuple[int, int]]]:
    base_tokens = []
    token_map = []
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
            else "<cr>"
        )
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

    # for base_token in sorted(token_collection, key=lambda t: t.begin):
    #     if (base_token.begin, base_token.end) not in newline_token_indices:
    #         base_tokens.append(base_token.get_covered_text())
    #         token_map.append((base_token.begin, base_token.end))
    #         duplicates[(base_token.begin, base_token.end)].append(
    #             base_token.get_covered_text()
    #         )
    #     else:
    #         # since these indices are tracked as well in the timelines code presumably
    #         base_tokens.append("<cr>")
    #         token_map.append((base_token.begin, base_token.end))
    #         duplicates[(base_token.begin, base_token.end)].append("<cr>")
    # for inds, tokens in duplicates.items():
    #     if len(tokens) > 1:
    #         print(f"{tokens} {inds}")
    return base_tokens, token_map


def invert_map(
    token_map: List[Tuple[int, int]]
) -> Tuple[Dict[int, int], Dict[int, int]]:
    begin_map: Dict[int, int] = {}
    end_map: Dict[int, int] = {}
    for token_index, token_boundaries in enumerate(token_map):
        begin, end = token_boundaries
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
    tokens, token_map = tokens_and_map(cas, raw_sentence)
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
    print(f"conmod result: {result}")
    return result


def get_tlink_instance(
    event: FeatureStructure,
    timex: FeatureStructure,
    tokens: List[str],
    begin2token: Dict[int, int],
    end2token: Dict[int, int],
) -> str:
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
    str_builder = (
        # since the window is around the event,
        # from the beginning to the first mention
        tokens[event_begin - WINDOW_RADIUS : first_begin]
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
        + tokens[second_end : event_end + WINDOW_RADIUS]
    )
    result = " ".join(str_builder)
    print(f"tlink result: {result}")
    return result


def get_dtr_instance(
    event: FeatureStructure,
    tokens: List[str],
    begin2token: Dict[int, int],
    end2token: Dict[int, int],
) -> str:
    # raw_sentence = cas.select_covering(ctakes_types.Sentence, event)[0]
    # tokens, token_map = tokens_and_map(cas, raw_sentence)
    # inverse_map = invert_map(token_map)
    event_begin = begin2token[event.begin]
    event_end = end2token[event.end] + 1
    # window_tokens = tokens[event_begin - window_radius:event_end + window_radius - 1]
    str_builder = (
        tokens[event_begin - WINDOW_RADIUS : event_begin]
        + ["<e>"]
        + tokens[event_begin:event_end]
        + ["</e>"]
        + tokens[event_end : event_end + WINDOW_RADIUS]
    )
    result = " ".join(str_builder)
    print(f"dtr result: {result}")
    return result


def get_window_mentions(
    event: FeatureStructure,
    cas: Cas,
    mention_type: Union[Type, str],
    begin2token: Dict[int, int],
    end2token: Dict[int, int],
    token2char: List[Tuple[int, int]],
) -> Generator[FeatureStructure, None, None]:
    event_begin_token_index = begin2token[event.begin]
    event_end_token_index = end2token[event.end]
    char_window_begin = token2char[event_begin_token_index][0]
    char_window_end = token2char[event_end_token_index][1]

    def in_window(index):
        return char_window_begin <= index <= char_window_end

    for mention in cas.select(mention_type):
        if in_window(mention.begin) and in_window(mention.end):
            yield mention


def get_classifier(model_dir, main_device):
    config = AutoConfig.from_pretrained(model_dir)

    model = AutoModelForSequenceClassification.from_pretrained(
        model_dir,
        config=config,
    )

    tokenizer = AutoTokenizer.from_pretrained(
        model_dir, add_prefix_space=True, additional_special_tokens=SPECIAL_TOKENS
    )

    classifier = TextClassificationPipeline(
        model=model, tokeizer=tokenizer, device=main_device
    )

    return classifier


class TimelineDelegator(cas_annotator.CasAnnotator):
    def __init__(self):
        print("In __init__")
        self._dtr_path = None
        self._tlink_path = None
        self._conmod_path = None
        self.dtr_classifier = lambda _: []
        self.tlink_classifier = lambda _: []
        self.conmod_classifier = lambda _: []
        self.raw_events = defaultdict(list)

    def init_params(self, arg_parser):
        print("In init_params")
        self._dtr_path = arg_parser.dtr_path
        self._tlink_path = arg_parser.tlink_path
        self._conmod_path = arg_parser.conmod_path
        """
        print("params populated")
        self.dtr_classifier = pipeline(
            "text-classification", model=self._dtr_path, tokenizer=self._dtr_path
        )

        print("DTR classifier loaded")
        self.tlink_classifier = pipeline(
            "text-classification", model=self._tlink_path, tokenizer=self._tlink_path
        )

        print("TLINK classifier loaded")
        self.conmod_classifier = pipeline(
            "text-classification", model=self._conmod_path, tokenizer=self._conmod_path
        )

        print("Conmod classifier loaded")
        """

    def initialize(self):
        print("In inititalize")

        if torch.cuda.is_available():
            main_device = 0
            print("GPU with CUDA is available, using GPU")
        else:
            main_device = -1
            print("GPU with CUDA is not available, defaulting to CPU")
        # self.dtr_classifier = pipeline(
        #     "text-classification", model=self._dtr_path, tokenizer=self._dtr_path
        # )
        self.dtr_classifier = get_classifier(self._dtr_path, main_device)

        print("DTR classifier loaded")
        # self.tlink_classifier = pipeline(
        #     "text-classification", model=self._tlink_path, tokenizer=self._tlink_path
        # )

        self.tlink_classifier = get_classifier(self._tlink_path, main_device)

        print("TLINK classifier loaded")
        # self.conmod_classifier = pipeline(
        #     "text-classification", model=self._conmod_path, tokenizer=self._conmod_path
        # )

        self.conmod_classifier = get_classifier(self._conmod_path, main_device)

        print("Conmod classifier loaded")

    def declare_params(self, arg_parser):
        print("In declare_params")
        arg_parser.add_arg("--dtr_path")
        arg_parser.add_arg("--tlink_path")
        arg_parser.add_arg("--conmod_path")

    # Process Sentences, adding Times, Events and TLinks found by cNLPT.
    def process(self, cas: Cas):
        print("Processing CAS")
        # TODO - will need CUI-based filtering later
        chemos = cas.select(cas.typesystem.get_type(ctakes_types.EventMention))
        if len(chemos) > 0:
            self.write_raw_timelines(cas, chemos)
        else:
            document_path_collection = cas.select(ctakes_types.DocumentPath)
            document_path = list(document_path_collection)[0].documentPath
            patient_id = os.path.basename(os.path.dirname(document_path))
            note_name = os.path.basename(document_path).split(".")[0]
            print(
                f"No chemotherapy mentions found in patient {patient_id} note {note_name} skipping"
            )

    def write_raw_timelines(self, cas: Cas, chemo_mentions):
        print("in write_raw_timelines")
        conmod_instances = (get_conmod_instance(chemo, cas) for chemo in chemo_mentions)
        conmod_classifications = (
            result["label"]
            for result in filter(None, self.conmod_classifier(conmod_instances))
        )
        positive_chemo_mentions = [
            chemo
            for chemo, modality in zip(chemo_mentions, conmod_classifications)
            if modality == "ACTUAL"
        ]
        if len(positive_chemo_mentions) > 0:
            self._write_positive_chemo_mentions(cas, positive_chemo_mentions)
        else:
            document_path_collection = cas.select(ctakes_types.DocumentPath)
            document_path = list(document_path_collection)[0].documentPath
            patient_id = os.path.basename(os.path.dirname(document_path))
            note_name = os.path.basename(document_path).split(".")[0]
            print(
                f"No concrete chemotherapy mentions found in patient {patient_id} note {note_name} skipping"
            )

    def _write_positive_chemo_mentions(self, cas, positive_chemo_mentions):
        print("in _write_positive_chemo_mentions")
        timex_type = cas.typesystem.get_type(ctakes_types.TimeMention)
        cas_source_data = cas.select(ctakes_types.Metadata)[0].sourceData
        # in its normalized string form, maybe need some exceptions
        # for if it's missing describing the file spec
        document_creation_time = cas_source_data.sourceOriginalDate

        base_tokens, token_map = tokens_and_map(cas)
        begin2token, end2token = invert_map(token_map)
        # print("Base tokens")
        # print(base_tokens)
        # print("token map")
        # print(token_map)
        # print("inverse token map")
        # print(char2token)

        dtr_instances = (
            get_dtr_instance(chemo, base_tokens, begin2token, end2token)
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
                event, cas, timex_type, begin2token, end2token, token_map
            )
            tlink_instances = (
                get_tlink_instance(event, w_timex, base_tokens, begin2token, end2token)
                for w_timex in window_timexes
            )
            return {
                timex: result["label"]
                for w_timex, result in zip(
                    window_timexes, self.tlink_classifier(tlink_instances)
                )
            }

        tlink_classifications = {
            chemo: tlink_result_dict(chemo) for chemo in positive_chemo_mentions
        }

        document_path_collection = cas.select(ctakes_types.DocumentPath)
        document_path = list(document_path_collection)[0].documentPath
        patient_id = os.path.basename(os.path.dirname(document_path))
        note_name = os.path.basename(document_path).split(".")[0]
        # patient_id = "THE_DUD"
        timexes = cas.select(timex_type)
        if len(timexes) == 0:
            print(f"WARNING: No timexes discovered in {patient_id} {note_name} verify")
        else:
            print([timex.get_covered_text() for timex in timexes])
        for chemo in positive_chemo_mentions:
            chemo_text = (chemo.get_covered_text() if chemo is not None else "ERROR",)
            chemo_dtr = dtr_classifications[chemo]
            print(f"chemo: {chemo_text}, DTR: {chemo_dtr}")
            for timex, chemo_timex_rel in tlink_classifications[chemo]:
                instance = [
                    document_creation_time,
                    chemo_text,
                    chemo_dtr,
                    timex.get_covered_text() if timex is not None else "ERROR",
                    chemo_timex_rel,
                    note_name,
                ]
                print(instance)
                self.raw_events[patient_id].append(instance)

    def collection_process_complete(self):
        print("In collection_process_complete")
        # Per 12/6/23 meeting, summarization is done
        # outside the Docker to maximize the ability for the user
        # to customize everything.  So we just write the raw results
        # to tsv
        for pt_id, records in self.raw_events.items():
            pt_df = pd.DataFrame.from_records(
                records,
                columns=["DCT", "chemo_text", "dtr", "timex", "tlink", "note_name"],
            )
            print(pt_df)
            pt_df.to_csv(f"{pt_id}_raw.tsv", index=False, sep="\t")
