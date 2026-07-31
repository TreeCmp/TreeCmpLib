package treecmp.heuristics.moves;

import pal.tree.Tree;

import java.util.List;

public interface TreeMove {
    String getDescription();
    int getNniEquivalentCost();
    List<Tree> getNniTrajectory(Tree startTree);
}
