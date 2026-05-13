module io.qzz.tbsciencehubproject.sciencehub.core {
  exports io.qzz.tbsciencehubproject.resource;
  exports io.qzz.tbsciencehubproject.pipeline.pipeline;
  exports io.qzz.tbsciencehubproject.user;
  exports io.qzz.tbsciencehubproject.utils;
  exports io.qzz.tbsciencehubproject.pipeline.validate;

  requires static lombok;
  requires org.jgrapht.core;
  requires jdk.httpserver;
}