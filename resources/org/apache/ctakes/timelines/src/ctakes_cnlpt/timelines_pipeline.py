import warnings

from ctakes_pbj.component.pbj_receiver import start_receiver
from ctakes_pbj.component.pbj_sender import PBJSender
from ctakes_pbj.pipeline.pbj_pipeline import PBJPipeline

from ctakes_cnlpt.ae.conmod_delegator import ConmodDelegator
from ctakes_cnlpt.ae.doctimerel_delegator import DocTimeRelDelegator
from ctakes_cnlpt.ae.temporal_delegator import TemporalDelegator


warnings.filterwarnings("ignore")


def main():

    # Create a new PBJ Pipeline, add a class that interacts with cNLPT to add Negation to Events.
    pipeline = PBJPipeline()
    # will have to confirm with Jiarui and Guergana but this order should work
    pipeline.add(ConmodDelegator())
    pipeline.add(DocTimeRelDelegator())
    pipeline.add(TemporalDelegator())
    # Add a PBJ Sender to the end of the pipeline to send the processed cas back to cTAKES and initialize the pipeline.
    pipeline.add(PBJSender())
    pipeline.initialize()
    # Start a PBJ receiver to accept cas objects from Artemis and process them in the pipeline.
    start_receiver(pipeline)


main()

