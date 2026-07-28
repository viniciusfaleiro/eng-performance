package com.engperf.adapter.inbound.web.auth;

import com.engperf.application.structure.TreeNode;
import com.engperf.domain.access.AccessScope;
import java.util.ArrayList;
import java.util.List;

/** Prunes the organization tree to the nodes an {@link AccessScope} is allowed to see. */
public final class ScopeViews {

  private ScopeViews() {}

  /**
   * Returns a copy of {@code tree} keeping only nodes the scope may view. A node survives when the
   * scope can view it directly or any of its descendants survive. The root ({@code all}) is always
   * returned as the container, even when empty, so the frontend always has a tree to render.
   */
  public static TreeNode prune(TreeNode tree, AccessScope scope) {
    List<TreeNode> keptChildren = pruneChildren(tree.children(), scope);
    return new TreeNode(tree.id(), tree.label(), tree.level(), keptChildren);
  }

  private static List<TreeNode> pruneChildren(List<TreeNode> children, AccessScope scope) {
    List<TreeNode> kept = new ArrayList<>();
    for (TreeNode child : children) {
      List<TreeNode> grandChildren = pruneChildren(child.children(), scope);
      if (scope.canView(child.id()) || !grandChildren.isEmpty()) {
        kept.add(new TreeNode(child.id(), child.label(), child.level(), grandChildren));
      }
    }
    return kept;
  }
}
