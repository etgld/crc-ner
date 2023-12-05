import asyncio
import time

import tlink_rest
import dtr_rest
import conmod_rest
from transformers import pipeline
from ctakes_pbj.component import cas_annotator
from ctakes_pbj.pbj_tools import create_type
from ctakes_pbj.pbj_tools.create_relation import create_relation
from ctakes_pbj.pbj_tools.event_creator import EventCreator
from ctakes_pbj.pbj_tools.helper_functions import get_covered_list
from ctakes_pbj.type_system import ctakes_types
from typing import List
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
        pass

    # Called once at the end of the pipeline.
    def collection_process_complete(self):
        # TODO - summarization code here
        pass
