package io.qzz.tbsciencehubproject.pipeline;

import io.qzz.tbsciencehubproject.pipeline.pipeline.Artifact;

class PipelineDemo {


//  void demoPipelineConfigure() throws ExecutionException, InterruptedException {
//    Pipeline publishingPipeline = null;
//    PipelineStep protocolGeneration = null;
//    PipelineStep actGeneration = null;
//
//    Artifact<String> articleName = null; // publishingRecipe.getArticleNameArtifact()
//    Artifact<String> journalName = null; // publishingRecipe.getJournalNameArtifact()
//
//    protocolGeneration.dependOnArtifact(articleName);
//    protocolGeneration.dependOnArtifact(journalName);
//
//    actGeneration.dependOnArtifact(articleName);
//    actGeneration.dependOnArtifact(journalName);
//
//    var pipeline = publishingPipeline.createPipeline();
//
//    var result = pipeline.run();
//    if (result.isDone()) {
//      var pr = result.get();
//      System.out.println(pr);
//    }
//  }

}