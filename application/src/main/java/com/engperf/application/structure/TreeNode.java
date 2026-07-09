package com.engperf.application.structure;

import java.util.List;

/**
 * A node of the organization tree returned to the inbound adapter.
 *
 * @param level one of {@code overview}, {@code vertical}, {@code team}, {@code person}
 */
public record TreeNode(String id, String label, String level, List<TreeNode> children) {

  public TreeNode {
    children = List.copyOf(children);
  }
}
