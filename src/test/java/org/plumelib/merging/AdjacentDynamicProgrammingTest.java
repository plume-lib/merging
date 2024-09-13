package org.plumelib.merging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class AdjacentDynamicProgrammingTest {

  @Test
  void test1() {
    List<String> l1 = List.of("a", "c", "d");
    List<String> l2 = List.of("a", "c", "d");
    List<String> l3 = List.of("a", "c", "d");
    AdjacentDynamicProgramming adp = new AdjacentDynamicProgramming(l1, l2, l3);
    assertEquals(List.of("a", "c", "d"), adp.compute());
  }

  @Test
  void test2() {
    List<String> l1 = List.of("a", "b", "d");
    List<String> l2 = List.of("a", "c", "d");
    List<String> l3 = List.of("a", "c", "d");
    AdjacentDynamicProgramming adp = new AdjacentDynamicProgramming(l1, l2, l3);
    assertEquals(List.of("a", "b", "d"), adp.compute());
  }

  @Test
  void test3() {
    List<String> l1 = List.of("a", "c", "d");
    List<String> l2 = List.of("a", "b", "d");
    List<String> l3 = List.of("a", "c", "d");
    AdjacentDynamicProgramming adp = new AdjacentDynamicProgramming(l1, l2, l3);
    assertEquals(List.of("a", "c", "d"), adp.compute());
  }

  @Test
  void test4() {
    List<String> l1 = List.of("a", "c", "d");
    List<String> l2 = List.of("a", "c", "d");
    List<String> l3 = List.of("a", "b", "d");
    AdjacentDynamicProgramming adp = new AdjacentDynamicProgramming(l1, l2, l3);
    assertEquals(List.of("a", "b", "d"), adp.compute());
  }

  @Test
  void test10() {
    List<String> l1 = List.of("a", "c", "d");
    List<String> l2 = List.of("b", "d");
    List<String> l3 = List.of("a", "b", "c", "d");
    AdjacentDynamicProgramming adp = new AdjacentDynamicProgramming(l1, l2, l3);
    assertEquals(List.of("a", "c", "d"), adp.compute());
  }
}
