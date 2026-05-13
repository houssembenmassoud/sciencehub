package io.qzz.tbsciencehubproject.pipeline.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.junit.jupiter.api.Test;

class TopologicalSortOrderingTest {

  @Test
  public void testSort() {
    SimpleDirectedGraph<String, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);

    graph.addVertex("A");
    graph.addVertex("B");
    graph.addVertex("C");
    graph.addVertex("D");
    graph.addVertex("E");

    graph.addEdge("A", "B");
    graph.addEdge("A", "C");
    graph.addEdge("B", "C");
    graph.addEdge("D", "E");
    graph.addEdge("C", "E");

    var iterator = new TopologicalOrderIterator<>(graph);

    var arr = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator, Spliterator.SIZED | Spliterator.DISTINCT | Spliterator.ORDERED),
        false)
        .mapToInt(graph::inDegreeOf)
        .distinct().toArray();

    boolean isSorted = IntStream.range(0, arr.length - 1)
        .allMatch(i -> arr[i] < arr[i + 1]);

    assertTrue(isSorted);
  }

}