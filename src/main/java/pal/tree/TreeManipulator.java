// TreeManimulator.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.tree;

/**
 * <b>Was TreeRooter.</b>
 * A class to provide all your tree rooting and unrooting needs plus more. Allows
 * Unrooting, Midpoint Rooting (reasonably efficiently), General Rooting, and
 * obtaining every root. Also allows for the collapsing and uncollapsing of short branches, and the attachment of sub trees.
 *
 * This class replaces methods in TreeUtil (and is more swanky)
 *
 * In general just use the static access methods. (eg TreeManipulator.getUnrooted(myTree); )
 *
 * @version $Id: TreeManipulator.java,v 1.3 2004/08/02 05:22:04 matt Exp $
 *
 * @author Matthew Goode
 * Note: REDUCE_CONSTRUCTION functioning (relatively untested) as of 18 September 2003
 *
 * <br><br><em>History</em>
 * <ul>
 *  <li> 18/9/2003 MG:Corrected rooting for complex case, added in getAllRoot methods, REDUCED_CONSTRUCTION stuff working, added in ingroup branch length stuff to rooting (to help make pretty pictures), added getAsInput() methods </li>
 *  <li> 25/10/2003 MG:Fixed bug with EXPAND_CONSTRUCTION on a unrooted tree </li>
 *  <li> 16/4/2003 MG:Changed name (TreeRooter -&gt; TreeManipulator), added branch access stuff
 * </ul>
 */
import pal.util.AlgorithmCallback;
import pal.misc.Units;
import pal.misc.BranchLimits;
import pal.misc.Identifier;

import java.util.ArrayList;


public class TreeManipulator implements UnrootedTreeInterface.Instructee, RootedTreeInterface.Instructee {


	/**
	 * Construct tree with same multification as original
	 */
	public static final int MIMIC_CONSTRUCTION = 100;
	/**
	 * Construct tree, but convert general multifications to a series of bifications
	 */
	public static final int EXPAND_CONSTRUCTION = 200 ;
	/**
	 * Construct tree, but convert bificating nodes that appear as multifications (due to very short branchlengths) to multifications
	 */
	public static final int REDUCE_CONSTRUCTION = 300;

	private Connection unrootedTree_;
	private final int units_;
	/**
	 * Only used by getBinaryTree()
	 */
	private final double firstChildNodeLength_;
	private final boolean inputTreeUnrooted_;

	/**
	 * Construct a TreeManipulator based around a normal tree
	 * @param base The base tree, which can be rooted or unrooted (will be treated as unrooted either way)
	 * @param constructionMode the way in which the internal tree representation is constructed
	 * Note: The base tree is never altered
	 */
	public TreeManipulator(Tree base, int constructionMode) {
		this(base.getRoot(), base.getUnits(),constructionMode);
	}
	/**
	 * Construct a TreeManipulator based around a normal tree
	 * @param base The base tree, which can be rooted or unrooted (will be treated as unrooted either way)
	 * Note: The base tree is never altered
	 */
	public TreeManipulator(Tree base) {
		this(base.getRoot(), base.getUnits());
	}
    /**
     * Constructs a {@code TreeManipulator} based around a normal tree, setting the units to {@code Units.UNKNOWN}.
     * This is a convenience constructor for internal use when the exact units of generated trees are not critical.
     *
     * @param base The base node defining the tree structure.
     */
    public TreeManipulator(Node base) {
        this(base,Units.UNKNOWN);
    }
    /**
     * Constructs a {@code TreeManipulator} based around a normal tree using the default construction mode ({@code MIMIC_CONSTRUCTION}).
     *
     * @param base The base node defining the tree, which can be rooted or unrooted (it will be treated as unrooted for manipulation).
     * Note: The base tree is never altered.
     * @param units The units (e.g., branch length type) that generated trees will be expressed in.
     */
    public TreeManipulator(Node base, int units) {
        this(base,units,MIMIC_CONSTRUCTION);
    }
    /**
     * Constructs a {@code TreeManipulator} based around a normal tree, specifying the construction mode.
     * The manipulator is initialized to wrap the base tree structure for unrooted manipulation.
     *
     * @param base The base node defining the tree, which can be rooted or unrooted (it will be treated as unrooted for manipulation).
     * Note: The base tree is never altered.
     * @param units The units (e.g., branch length type) that generated trees will be expressed in.
     * @param constructionMode An integer flag specifying how the internal unrooted tree representation should be constructed (e.g., {@code MIMIC_CONSTRUCTION}).
     */
    public TreeManipulator(Node base, int units, int constructionMode) {
        SimpleNode simpleBase = new PALNodeWrapper(base);
        this.unrootedTree_ = construct(simpleBase, constructionMode);
        this.inputTreeUnrooted_ = base.getChildCount()>2;
        this.firstChildNodeLength_ = base.getChild(0).getBranchLength();
        this.units_ = units;
        this.unrootedTree_.clearPathInfo();
    }

	public TreeManipulator(UnrootedTreeInterface.Instructee base, int units, int constructionMode) {
	  UnrootedInterfaceImpl ui = new UnrootedInterfaceImpl();
		base.instruct(ui);
		SimpleBranch root = ui.getSimpleRootBranch();
		this.unrootedTree_ = new Connection(root,constructionMode);
		this.firstChildNodeLength_ = root.getBranchLength()/2;
		this.units_ = units;
		this.unrootedTree_.clearPathInfo();
		this.inputTreeUnrooted_ = true;
	}

	public TreeManipulator(RootedTreeInterface.Instructee base, int units, int constructionMode) {
	  RootedInterfaceImpl ri = new RootedInterfaceImpl();
		base.instruct(ri);
		SimpleNode root = ri.getSimpleRoot();
		this.unrootedTree_ = construct(root, constructionMode);
		this.inputTreeUnrooted_ = false;
		this.firstChildNodeLength_ = root.getChild(0).getParentBranchLength();
		this.units_ = units;
		this.unrootedTree_.clearPathInfo();

	}
	/**
	 * Attachment constructor
	 * @param base The basis TreeManipulator
	 * @param baseSubTreeConnector The connection in the base that the sub tree will be attached
	 * @param subTree the sub tree to attach
	 * @param constructionMode the construction mode for the new sub tree (construction will match current for other parts of the tree)
	 */
	private TreeManipulator(TreeManipulator base, Connection baseSubTreeConnector, Node subTree, int constructionMode) {
		SimpleNode simpleSubTree = new PALNodeWrapper(subTree);
		this.unrootedTree_ = base.unrootedTree_.getAttached(baseSubTreeConnector,simpleSubTree, constructionMode);
		this.inputTreeUnrooted_ = (base.unrootedTree_==baseSubTreeConnector ? true : base.inputTreeUnrooted_);
		this.firstChildNodeLength_ = base.firstChildNodeLength_;
		this.units_ = base.units_;
		this.unrootedTree_.clearPathInfo();
	}

	private static final Connection construct(SimpleNode n, int constructionMode) {
		if(n.isLeaf()) {
		  throw new IllegalArgumentException("Tree must contain more than a single OTU!");
		}
		if(n.getNumberOfChildren()==2) {
			return new Connection(n.getChild(0), n.getChild(1),constructionMode);
		}

		UndirectedNode un = new UndirectedNode(n,constructionMode);
		return un.getPeerParentConnection();
	}


	/**
	 * @return the MidPoint rooted tree (as root node);
	 */
	public Node getMidPointRooted() {
		Node n = unrootedTree_.getMidPointRooted();
		NodeUtils.lengths2Heights(n);
		return n;
	}
	/**
	 * @return a tree rooted around the node it was originally rooted around (if originally rooted),
	 * Note: With
	 */
	public Node getDefaultRoot() {
		Node n = unrootedTree_.getRootedAround(firstChildNodeLength_);
		NodeUtils.lengths2Heights(n);
		return n;
	}

	/**
	 * Tests if the given clade memebers form an exact clade that does not include any other members other
	 * than the ones listed. If there are members that are not actually in the tree, they will be ignored.
	 * @param possibleCladeMembers the names of the members in the clade of interest
	 * @return true if the conditions are met
	 * Note: not currently correctly implemented
	 */
	private boolean isFormsFormsExactClade(String[] possibleCladeMembers) {
		return unrootedTree_.isFormsExactClade(possibleCladeMembers);
	}

	/**
	 * A method for recovering the input (construction) tree (with the EXPANSION/MIMIC/REDUCED differences)
	 * @return An unrooted tree if the input tree was unrooted, otherwise the default rooting
	 */
	public Node getAsInputRooting() {
		if(inputTreeUnrooted_) {
			return getUnrooted();
		}
		return getDefaultRoot();
	}
	/**
	 * A method for recovering the input (construction) tree (with the EXPANSION/MIMIC/REDUCED differences)
	 * @return An unrooted tree if the input tree was unrooted, otherwise the default rooting
	 */
	public Tree getAsInputRootingTree() {
		return constructTree(getAsInputRooting(),units_);
	}
	/**
	 * @return a tree rooted around the node it was originally rooted around (if originally rooted),
	 * Note: With
	 */
	public Tree getDefaultRootTree() {
		return constructTree(getDefaultRoot(),units_);
	}
	/**
	 * @return the MidPoint rooted tree
	 */
	public Tree getMidPointRootedTree() {
		return constructTree(getMidPointRooted(),units_);
	}

    /**
     * Returns an unrooted node representation of the tree being manipulated.
     * Node heights are calculated from branch lengths before returning.
     *
     * @return The root {@code Node} of the unrooted tree structure.
     */
    public Node getUnrooted() {
        Node n = unrootedTree_.getUnrooted();
        NodeUtils.lengths2Heights(n);
        return n;
    }
    /**
     * Returns a new {@code Tree} object representing the unrooted topology.
     *
     * @return A new {@code Tree} instance of the unrooted tree.
     */
    public Tree getUnrootedTree() {
        return constructTree(getUnrooted(),units_);
    }

    /**
     * Returns an array of all connections (branches) in the unrooted tree structure.
     * These connections represent potential rooting points.
     *
     * @return An array of {@code Connection} objects representing all branches in the tree.
     */
    private Connection[] getAllConnections() {
        return unrootedTree_.getAllConnections();
    }

    /**
     * Roots the unrooted tree by finding the Most Recent Common Ancestor (MRCA) of the specified outgroup members,
     * making the branch leading to that MRCA the new root.
     * Node heights are calculated from branch lengths before returning.
     *
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * @return The new {@code Node} representing the root of the rooted tree.
     * Note: If the outgroup is not well defined (e.g., spans a polytomy), this may not be the only rooting.
     * @throws IllegalArgumentException If outgroup names does not contain any valid node names in the tree.
     */
    public Node getRootedBy(String[] outgroupNames) {
        Node n = unrootedTree_.getRootedAroundMRCA(outgroupNames);
        NodeUtils.lengths2Heights(n);
        return n;
    }
    /**
     * Instructs a {@code RootedTreeInterface} to construct the tree rooted by the Most Recent Common Ancestor (MRCA)
     * of the specified outgroup members.
     *
     * @param rootedInterface The interface object responsible for constructing the rooted tree display/model.
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * Note: If the outgroup is not well defined (e.g., spans a polytomy), this may not be the only rooting.
     * @throws IllegalArgumentException If outgroup names does not contain any valid node names in the tree.
     */
    public void instructRootedBy(RootedTreeInterface rootedInterface, String[] outgroupNames) {
        unrootedTree_.instructRootedAroundMRCA(rootedInterface, outgroupNames);
    }
    /**
     * Roots the unrooted tree by the MRCA of the specified outgroup and assigns a specific length to the branch
     * leading to the ingroup clade.
     *
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * @param ingroupBranchLength The maximum length to assign to the branch connecting the ingroup to the outgroup.
     * @return The new {@code Node} representing the root of the rooted tree.
     * Note: If the outgroup is not well defined (e.g., spans a polytomy), this may not be the only rooting.
     * @throws IllegalArgumentException If outgroup names does not contain any valid node names in the tree.
     */
    public Node getRootedBy(String[] outgroupNames,double ingroupBranchLength) {
        return unrootedTree_.getRootedAroundMRCA(outgroupNames,ingroupBranchLength);
    }

    /**
     * Returns all possible rootings (if multiple are defined) by the Most Recent Common Ancestor (MRCA)
     * of the specified outgroup members.
     *
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * @return An array of {@code Node} objects, each representing the root of a possible rooted tree.
     * @throws IllegalArgumentException If outgroup names does not contain any valid node names in the tree.
     */
    public Node[] getAllRootedBy(String[] outgroupNames) {
        return unrootedTree_.getAllRootedAroundMRCA(outgroupNames);
    }

    /**
     * Returns a new {@code Tree} object rooted by the Most Recent Common Ancestor (MRCA) of the specified outgroup members.
     *
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * @return A new {@code Tree} instance of the rooted tree.
     * Note: If the outgroup is not well defined, this may not be the only rooting.
     */
    public Tree getTreeRootedBy(String[] outgroupNames) {
        return constructTree(getRootedBy(outgroupNames),units_);
    }
    /**
     * Returns a new {@code Tree} object rooted by the MRCA of the specified outgroup members,
     * with a specified length for the ingroup branch.
     *
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * @param ingroupBranchLength The maximum length of the branch leading to the ingroup clade.
     * @return A new {@code Tree} instance of the rooted tree.
     * Note: If the outgroup is not well defined, this may not be the only rooting.
     */
    public Tree getTreeRootedBy(String[] outgroupNames, double ingroupBranchLength) {
        return constructTree(getRootedBy(outgroupNames,ingroupBranchLength),units_);
    }

    /**
     * Returns all possible {@code Tree} objects that can be rooted by the outgroup defined by the MRCA of the specified members.
     *
     * @param outgroupNames The names (identifiers) of the members belonging to the outgroup.
     * @return An array of all possible rooted {@code Tree} objects defined by the outgroup.
     */
    public Tree[] getAllTreesRootedBy(String[] outgroupNames) {
        Node[] nodes = getAllRootedBy(outgroupNames);
        Tree[] trees = new Tree[nodes.length];
        for(int i = 0 ; i < nodes.length ;i++) {
            trees[i] = constructTree(nodes[i],units_);
        }
        return trees;
    }
    /**
     * Returns an iterator that provides access to each possible rooting of the base tree as a new {@code Tree} object.
     * This method is memory efficient as trees are constructed one at a time upon request.
     *
     * @return A {@code TreeIterator} that iterates through every possible rooted tree.
     */
    public TreeIterator getEveryRootIterator() {
        return new RootIterator(getAllConnections(),units_);
    }

    /**
     * Instructs an {@code UnrootedTreeInterface} to construct the unrooted representation of the tree being manipulated.
     *
     * @param treeInterface The interface object responsible for constructing the unrooted tree display/model.
     */
    public void instruct(UnrootedTreeInterface treeInterface) {
        UnrootedTreeInterface.BaseBranch base = treeInterface.createBase();
        unrootedTree_.instruct(base);
    }
    /**
     * Instructs a {@code RootedTreeInterface} to construct a rooted representation of the tree.
     *
     * @param treeInterface The interface object responsible for constructing the rooted tree display/model.
     */
    public void instruct(RootedTreeInterface treeInterface) {
        RootedTreeInterface.RNode base = treeInterface.createRoot();
        unrootedTree_.instruct(base,firstChildNodeLength_);

    }
    /**
     * Obtains an array of access objects, each corresponding to a branch in the unrooted tree, allowing
     * external access and manipulation of individual branch properties.
     *
     * @return An array of {@code BranchAccess} objects for all branches in the tree.
     */
    public BranchAccess[] getBranchAccess() {
        final Connection[] connections = getAllConnections();
        final BranchAccess[] results = new BranchAccess[connections.length];
        for(int i = 0 ; i < connections.length ; i++) {
            results[i] = new BranchAccessImpl(this,connections[i], units_);
        }
        return results;
    }
    /**
     * Returns an array containing every possible rooted {@code Tree} that can be derived from the base unrooted tree.
     * Warning: This constructs all trees at once and may be memory intensive. Use {@code getEveryRootIterator()} instead for large trees.
     *
     * @return An array of all possible rooted {@code Tree} objects.
     */
    public Tree[] getEveryRoot() {
        final Connection[] connections = getAllConnections();
        final Tree[] results = new Tree[connections.length];
        for(int i = 0 ; i < connections.length ; i++) {
            results[i] = constructTree(connections[i].getRootedAround(), units_);
        }
        return results;
    }
    /**
     * Reroots the tree above the branch leading to the specified node from the original base tree.
     * Node heights are calculated from branch lengths before returning.
     *
     * @param base A node from the original base tree that this {@code TreeManipulator} was constructed on.
     * @return The new {@code Node} representing the root of the resulting tree.
     * @throws IllegalArgumentException If the input node was not found in the original base tree structure.
     */
    public Node getRootedAbove(Node base) {
        UndirectedNode match = unrootedTree_.getRelatedNode(base);
        if(match==null) {
            throw new IllegalArgumentException("Parameter node not found in original tree");
        }
        Node n = match.getPeerParentConnection().getRootedAround();
        NodeUtils.lengths2Heights(n);
        return n;
    }
    /**
     * Returns a new {@code Tree} object rooted above the branch leading to the specified node from the original base tree.
     *
     * @param n A node from the original base tree that this {@code TreeManipulator} was constructed on.
     * @return A new {@code Tree} instance rooted above the specified node.
     * @throws IllegalArgumentException If the input node was not found in the original base tree structure.
     */
    public Tree getTreeRootedAbove(Node n) {
        return constructTree(getRootedAbove(n),units_);
    }

// -=-==--=-==-=--=-=-=-=-=-=-=-=-=-=-====--=-=-=--=====-=-=-=-=-=---====-=-=-=
// Static access methods
    /**
     * Unroots a tree by internally converting it to an unrooted representation and returning a new tree object.
     * The resulting tree will have a trifurcation (three outgoing branches) at the base.
     *
     * @param base The input tree that may or may not be rooted.
     * @return A new {@code Tree} object representing the unrooted topology.
     */
    public static final Tree getUnrooted(Tree base) {
        return new TreeManipulator(base).getUnrootedTree();
    }

    /**
     * Returns the tree rooted using the **midpoint rooting** method. This method places the root halfway along
     * the longest path between any two terminal taxa (leaves) in the tree, effectively dividing the data between
     * the two most distinct taxa.
     *
     * @see <a href="http://www.mun.ca/biology/scarr/Panda_midpoint_rooting.htm">Midpoint Rooting Explanation</a>
     * @param base The input tree that may or may not be rooted.
     * @return A new {@code Tree} object rooted at the midpoint.
     */
    public static final Tree getMidpointRooted(Tree base) {
        return new TreeManipulator(base).getMidPointRootedTree();
    }

    /**
     * Obtains an array containing every possible rooting of the base tree.
     * Warning: This constructs all trees at once and may be memory intensive for large trees.
     *
     * @param base The input tree that may or may not be rooted.
     * @return An array of {@code Tree} objects, where each element is a unique rooted version of the base tree.
     */
    public static final Tree[] getEveryRoot(Tree base) {
        return new TreeManipulator(base).getEveryRoot();
    }

    /**
     * Obtains an iterator that yields every possible rooting of the base tree as a new {@code Tree} object upon request.
     * This method is generally preferred over {@code getEveryRoot(Tree)} for memory efficiency.
     *
     * @param base The input tree that may or may not be rooted.
     * @return A {@code TreeIterator} that provides access to all possible rooted trees sequentially.
     */
    public static final TreeIterator getEveryRootIterator(Tree base) {
        return new TreeManipulator(base).getEveryRootIterator();
    }

    /**
     * Roots a tree using an **outgroup** defined by a set of taxon names.
     * The tree is rooted by placing the root on the branch leading to the Most Recent Common Ancestor (MRCA)
     * of the specified outgroup members.
     *
     * @param base The input tree that may or may not be rooted.
     * @param outgroupNames The names of the members of the outgroup. Names not matching taxa in the tree are ignored.
     * @return A new rooted {@code Tree} object.
     * Note: If the outgroup is not well defined (e.g., spans a polytomy), the returned tree may not be the only possible rooting.
     * @throws IllegalArgumentException If no members of the tree appear in the outgroup.
     */
    public static final Tree getRootedBy(Tree base, String[] outgroupNames) {
        return new TreeManipulator(base).getTreeRootedBy(outgroupNames);
    }
    /**
     * Roots a tree using an **outgroup** and sets a specific length for the branch connecting the ingroup to the outgroup.
     *
     * @param base The input tree that may or may not be rooted.
     * @param outgroupNames The names of the members of the outgroup. Names not matching taxa in the tree are ignored.
     * @param ingroupBranchLength The maximum length to assign to the branch leading to the ingroup clade (the new root branch length).
     * @return A new rooted {@code Tree} object.
     * Note: If the outgroup is not well defined (e.g., spans a polytomy), the returned tree may not be the only possible rooting.
     * @throws IllegalArgumentException If no members of the tree appear in the outgroup.
     */
    public static final Tree getRootedBy(Tree base, String[] outgroupNames, double ingroupBranchLength) {
        return new TreeManipulator(base).getTreeRootedBy(outgroupNames,ingroupBranchLength);
    }

    /**
     * Returns an array of every possible interpretation of rooting a tree by the given outgroup.
     * If the outgroup is well defined (i.e., monophyletic relative to the ingroup), the array will typically contain only one tree.
     *
     * @param base The input tree that may or may not be rooted.
     * @param outgroupNames The names of the members of the outgroup. Names not matching taxa in the tree are ignored.
     * @return An array of all possible rooted {@code Tree} objects defined by the outgroup.
     * @throws IllegalArgumentException If no members of the tree appear in the outgroup.
     */
    public static final Tree[] getAllRootingsBy(Tree base, String[] outgroupNames) {
        return new TreeManipulator(base).getAllTreesRootedBy(outgroupNames);
    }

// -=-==--=-==-=--=-=-=-=-=-=-=-=-=-=-====--=-=-=--=====-=-=-=-=-=---====-=-=-=
	/**
	 * A connection between two nodes
	 */
	private static final class Connection {
		private UndirectedNode firstNode_;
		private double maximumPathLengthToLeafViaFirstNode_;
		private boolean isFirstPathInfoFound_ = false;

		private UndirectedNode secondNode_;
		private double maximumPathLengthToLeafViaSecondNode_;
		private boolean isSecondPathInfoFound_ = false;

		private double distance_;

		private Object annotation_;

		public Connection(UndirectedNode firstNode, UndirectedNode secondNode, SimpleBranch connectingBranch) {
			this.firstNode_ = firstNode;
			this.secondNode_ = secondNode;
			this.distance_ = connectingBranch.getBranchLength();
			this.annotation_ = connectingBranch.getAnnotation();
		}


		public Connection(UndirectedNode baseNode, SimpleNode parent, int startingIndex, double branchLength, Object annotation) {
			this.firstNode_ = baseNode;
			this.secondNode_ = new UndirectedNode(this,startingIndex, parent);
			this.distance_ = branchLength;
			this.annotation_ = annotation;
		}

		public Connection(UndirectedNode parentNode, SimpleNode child, int constructionMode) {
			this.firstNode_ = parentNode;
			SimpleBranch connectingBranch = child.getParentBranch();
			this.distance_ = connectingBranch.getBranchLength();
			this.annotation_ = connectingBranch.getAnnotation();
			this.secondNode_ = new UndirectedNode(constructionMode, this,child);
		}
		public Connection(SimpleNode first, SimpleNode second,int constructionMode) {
			this.distance_ = first.getParentBranchLength()+second.getParentBranchLength();
			this.firstNode_ = new UndirectedNode(constructionMode, this,first);
			this.secondNode_ = new UndirectedNode(constructionMode, this,second);
		}
		/**
		 * The root branch constructor
		 * @param branch The simple root branch
		 * @param constructionMode the construction mode
		 */
		public Connection(SimpleBranch branch,int constructionMode) {
			SimpleNode first = branch.getParentNode();
			SimpleNode second = branch.getChildNode();

			this.distance_ = branch.getBranchLength();
			this.annotation_ = branch.getAnnotation();

			this.firstNode_ = new UndirectedNode(constructionMode, this,first);
			this.secondNode_ = new UndirectedNode(constructionMode, this,second);
		}
		private Connection(Connection original, Connection attachmentPoint, SimpleNode subTree, int constructionMode) {
		  if(original==attachmentPoint) {
				throw new RuntimeException("Not implemented yet!");
			} else {
				this.distance_ = original.distance_;
				this.annotation_ = original.annotation_;
				this.firstNode_ = original.firstNode_.getAttached( attachmentPoint, subTree, constructionMode, this );
				this.secondNode_ = original.secondNode_.getAttached( attachmentPoint, subTree, constructionMode, this );
			}
		}
		public final Connection getAttached(Connection attachmentPoint, SimpleNode subTree, int constructionMode) {
		  return new Connection(this,attachmentPoint,subTree,constructionMode);
		}
		public final String[][] getLabelSplit() { throw new RuntimeException("Not implemented yet!"); }
		public final void setDistance(double distance) {  this.distance_ = distance;		}

		public final UndirectedNode getFirst() { return firstNode_; }
		public final UndirectedNode getSecond() { return secondNode_; }

		public final int getExactCladeCount(String[] possibleCladeMembers, UndirectedNode caller) {
		  if(caller==firstNode_) {
			  return secondNode_.getExactCladeCount(possibleCladeMembers,this);
			} else if(caller==secondNode_) {
			  return firstNode_.getExactCladeCount(possibleCladeMembers,this);
			} else {
			  throw new RuntimeException("Assertion erro : unknown caller");
			}
		}

		public final boolean isFormsExactClade(String[] possibleCladeMembers) {
		  int leftCount = firstNode_.getExactCladeCount(possibleCladeMembers,this);
			int rightCount = secondNode_.getExactCladeCount(possibleCladeMembers,this);
			if(leftCount<0||rightCount<0) { return false; }
			return (leftCount>0&&rightCount==0)||(rightCount>0&&leftCount==0);
		}
		public final int getNumberOfMatchingLeaves(String[] leafSet) {
		  return firstNode_.getNumberOfMatchingLeaves(leafSet,this)
						+ secondNode_.getNumberOfMatchingLeaves(leafSet,this);
		}
		public final int getNumberOfMatchingLeaves(String[] leafSet, UndirectedNode caller) {
		  if(caller==firstNode_) {
				return secondNode_.getNumberOfMatchingLeaves( leafSet, this );
			} else  if(caller==secondNode_) {
				return firstNode_.getNumberOfMatchingLeaves( leafSet, this );
			}
			throw new RuntimeException("Assertion error : unknown caller");
		}
		public final UndirectedNode getRelatedNode(Node n) {
			UndirectedNode fromFirst = firstNode_.getRelatedNode(n,this);
			if(fromFirst!=null) {
				return fromFirst;
			}
			return secondNode_.getRelatedNode(n,this);
		}
		/**
		 * @return a new node rooted on the first node of this tree
		 */
		public Node getUnrooted() {
			if(firstNode_.isLeaf()) {
				return secondNode_.buildUnrootedTree();
			}
			return firstNode_.buildUnrootedTree();
		}
		public final double getMaximumPathLengthToLeafViaFirst() {
			if(!isFirstPathInfoFound_) {
				maximumPathLengthToLeafViaFirstNode_ = firstNode_.getMaximumPathLengthToLeaf(this);
				isFirstPathInfoFound_ = true;
			}
			return maximumPathLengthToLeafViaFirstNode_;
		}
		public final double getMaximumPathLengthToLeafViaSecond() {
			if(!isSecondPathInfoFound_) {
				maximumPathLengthToLeafViaSecondNode_ = secondNode_.getMaximumPathLengthToLeaf(this);
				isSecondPathInfoFound_ = true;
			}
			return maximumPathLengthToLeafViaSecondNode_;
		}
		public final void addLabels(ArrayList store, UndirectedNode callingNode) {
		  if(callingNode==firstNode_) {
			  secondNode_.addLabels(store,this);
			} else if(callingNode==secondNode_) {
			  firstNode_.addLabels(store,this);
			} else {
			  throw new RuntimeException("Assertion error : unknown calling node!");
			}
		}
		public void setAnnotation(Object annotation) {
		  this.annotation_ = annotation;
		}
		public void instruct(UnrootedTreeInterface.BaseBranch base) {
		  base.setLength(this.distance_);
			if(annotation_!=null) {
				base.setAnnotation( annotation_ );
			}
			firstNode_.instruct(base.getLeftNode(),this);
			secondNode_.instruct(base.getRightNode(),this);
		}
		public void instruct(RootedTreeInterface.RNode base, double firstChildLength) {
		  base.resetChildren();
			RootedTreeInterface.RNode left = base.createRChild();
			RootedTreeInterface.RNode right = base.createRChild();
		  RootedTreeInterface.RBranch leftBranch = left.getParentRBranch();
			RootedTreeInterface.RBranch rightBranch = right.getParentRBranch();
		  leftBranch.setLength(firstChildLength);
			rightBranch.setLength(distance_-firstChildLength);

			if(annotation_!=null) {
				leftBranch.setAnnotation( annotation_ );
				rightBranch.setAnnotation( annotation_ );
			}
			firstNode_.instruct(left,this);
			secondNode_.instruct(right,this);
		}
		public void instruct(UnrootedTreeInterface.UBranch base, UndirectedNode callingNode) {
		  base.setLength(this.distance_);
			if(annotation_!=null) {
				base.setAnnotation( annotation_ );
			}
			if(callingNode==firstNode_) {
			  secondNode_.instruct(base.getFartherNode(),this);
			} else if(callingNode==secondNode_) {
			  firstNode_.instruct(base.getFartherNode(),this);
			} else {
				throw new IllegalArgumentException("Calling node is unknown!");
			}
		}
		public void instruct(RootedTreeInterface.RBranch base, UndirectedNode callingNode) {

			base.setLength(this.distance_);
			if(annotation_!=null) {			base.setAnnotation( annotation_ );		}
			if(callingNode==firstNode_) {
				//We are fanning out towards more recent tips
			  secondNode_.instruct(base.getMoreRecentNode(),this);
			} else if(callingNode==secondNode_) {
			  firstNode_.instruct(base.getMoreRecentNode(),this);
			} else {
				throw new IllegalArgumentException("Calling node is unknown!");
			}
		}

		/**
		 * @return the difference between the maximum path length to leaf via first node
		 * and the maximum path lenght to leaf via second node
		 */
		public final double getMaximumPathDifference() {
			return Math.abs(getMaximumPathLengthToLeafViaFirst()-getMaximumPathLengthToLeafViaSecond());
		}
		public Connection getMRCAConnection(String[] nodeNames) {
			return getMRCAConnection(null, nodeNames);
		}

		public Node getRootedAroundMRCA(String[] nodeNames) {
			Connection mrca = getMRCAConnectionBaseTraverse(nodeNames);
			if(mrca!=null) {
				return mrca.getRootedAround();
			}
			throw new IllegalArgumentException("Non existent outgroup:"+pal.misc.Utils.toString(nodeNames));
		}
		public void instructRootedAroundMRCA(RootedTreeInterface rootedInterface, String[] nodeNames) {
			Connection mrca = getMRCAConnectionBaseTraverse(nodeNames);
			if(mrca!=null) {
				mrca.instructRootedAround(rootedInterface);
			} else{
				throw new IllegalArgumentException( "Non existent outgroup:"+pal.misc.Utils.toString( nodeNames ) );
			}
		}
		public Node[] getAllRootedAroundMRCA(String[] nodeNames) {
			Connection[] mrca = getAllMRCAConnectionBaseTraverse(nodeNames);
			if(mrca.length==0) {
				throw new IllegalArgumentException( "Non existent outgroup:"+
																						pal.misc.Utils.toString( nodeNames ) );
			}
			Node[] nodes = new Node[mrca.length];
			for(int i = 0 ; i < nodes.length ; i++) {
			  nodes[i] = mrca[i].getRootedAround();
			}
			return nodes;
		}
		public Node getRootedAroundMRCA(String[] nodeNames, double ingroupBranchLength) {
		  Connection mrca = getMRCAConnectionBaseTraverse(nodeNames);
			if(mrca!=null) {
				return mrca.getRootedAround(ingroupBranchLength,nodeNames);
			}
			if(getNumberOfMatchingLeaves(nodeNames)>0) {
				//Basically the node names includes all of the taxa!
			  return getRootedAround(ingroupBranchLength,nodeNames);
			}

			throw new IllegalArgumentException("Non existent outgroup:"+pal.misc.Utils.toString(nodeNames));
		}
        /**
         * Recursively searches the unrooted tree structure, starting from the nodes of this connection,
         * to find the {@code Connection} that corresponds to the Most Recent Common Ancestor (MRCA) of the specified nodes.
         *
         * @param blockingNode The {@code UndirectedNode} that represents the direction *not* to search (i.e., the node from which the call originated, typically one of {@code firstNode_} or {@code secondNode_}).
         * @param nodeNames An array of strings containing the names of the nodes (taxa) whose MRCA is sought.
         * @return The {@code Connection} object that defines the branch leading to the MRCA of the specified nodes, or {@code null} if the MRCA is not found within the subtrees accessible from this connection (excluding the direction of the {@code blockingNode}).
         */
        public Connection getMRCAConnection(UndirectedNode blockingNode, String[] nodeNames) {

            Connection first = (firstNode_!=blockingNode) ? firstNode_.getMRCAConnection(this,nodeNames) : null;
            Connection second = (secondNode_!=blockingNode) ? secondNode_.getMRCAConnection(this,nodeNames) : null;
            if(first!=null) {
                if(second!=null) { return this; }
                return first;
            }
            //Second may be null
            return second;
        }
		public Connection getMRCAConnectionBaseTraverse(String[] nodeNames) {

			return getMRCAConnectionBaseTraverse(null,nodeNames);

		}
		public Connection[] getAllMRCAConnectionBaseTraverse(String[] nodeNames) {
			Connection[] store = new Connection[getNumberOfConnections()];
			int total = getAllMRCAConnectionBaseTraverse(nodeNames, store,0);
			Connection[] result = new Connection[total];
			System.arraycopy(store,0,result,0,total);
			return result;
		}
		public int getAllMRCAConnectionBaseTraverse(String[] nodeNames, Connection[] store, int numberInStore) {
			return getAllMRCAConnectionBaseTraverse(null,nodeNames, store, numberInStore);
		}
		public Connection getMRCAConnectionBaseTraverse(UndirectedNode callingNode, String[] nodeNames) {

			Connection first = firstNode_.getMRCAConnection(this,nodeNames) ;
			Connection second = secondNode_.getMRCAConnection(this,nodeNames) ;
			System.out.println("Traverse:"+first+"   "+second);

			if(first!=null) {
			  if(second==null) { return first; }

				//If the MRCA of either sides is not us, then the true MRCA has not been found
				//(because the outgroup is distributed on both sides of this base).
				//We try a different base (by traversing tree, so we will eventually get a suitable base)
			  if(firstNode_!=callingNode) {
					Connection attempt = firstNode_.getMRCAConnectionBaseTraverse( this, nodeNames);
					if( attempt!=null ) {	return attempt; }
				}
				if(secondNode_!=callingNode) {
					Connection attempt = secondNode_.getMRCAConnectionBaseTraverse( this, nodeNames );
					if( attempt!=null ) {	return attempt; }
				}
				return null;
			} else {
				//Second may be null
				return second;
			}
		}
		private final int addToStore(Connection c, Connection[] store, int numberInStore) {
		  for(int i = 0 ; i < numberInStore ; i++) {
			  if(store[i]==c) {
					return numberInStore;
				}
			}
			store[numberInStore++] = c;
			return numberInStore;
		}
		public int getAllMRCAConnectionBaseTraverse(UndirectedNode callingNode, String[] nodeNames, Connection[] store, int numberInStore) {
			Connection first = firstNode_.getMRCAConnection(this,nodeNames) ;
			Connection second = secondNode_.getMRCAConnection(this,nodeNames) ;
			if(first!=null) {
				if(second==null) { return addToStore(first,store,numberInStore); }
				//Both left and right attempts return a connection,
				if(first==second&&second==this) {
				  //If the MRCA of either side is us then we are the MRCA
					return addToStore(this,store,numberInStore);
				}
				//If the MRCA of either sides is not us, then the true MRCA has not been found
				//(because the outgroup is distributed on both sides of this base).
				//We try a different base (by traversing tree, so we will eventually get a suitable base)
			  if(firstNode_!=callingNode) {
					numberInStore = firstNode_.getAllMRCAConnectionBaseTraverse( this, nodeNames,store, numberInStore );
				}
				if(secondNode_!=callingNode) {
					numberInStore = secondNode_.getAllMRCAConnectionBaseTraverse( this, nodeNames,store, numberInStore );
				}
			}
			return numberInStore;
		}

		/**
		 * @return the total number of connections in the tree that this connection is part of
		 */
		public final int getNumberOfConnections() {
			return getNumberOfConnections(null);
		}
		protected final int getNumberOfConnections(UndirectedNode blockingNode) {
			int count = 0;
			if(firstNode_!=blockingNode) {
				count+=firstNode_.getNumberOfConnections(this);
			}
			if(secondNode_!=blockingNode) {
				count+=secondNode_.getNumberOfConnections(this);
			}
			return count+1; //Plus one for me!
		}
		/**
		 * @return all connections in the tree that includes this connection
		 */
		public final Connection[] getAllConnections() {
			int size = getNumberOfConnections();
			Connection[] array = new Connection[size];
			getConnections(array,0);
			return array;
		}
		protected final int getConnections(Connection[] array, int index) {
			return getConnections(null,array,index);
		}
		protected final int getConnections(UndirectedNode blockingNode, Connection[] array, int index) {
			array[index++] = this;
			if(firstNode_!=blockingNode) {
				index=firstNode_.getConnections(this,array,index);
			}
			if(secondNode_!=blockingNode) {
				index=secondNode_.getConnections(this,array,index);
			}
			return index; //Plus one for me!
		}

		public final Connection getMidPointConnection(final UndirectedNode blockingNode, Connection best) {
			if(blockingNode==secondNode_) {
				best = firstNode_.getMidPointConnection(this,best);
			} else if(blockingNode==firstNode_) {
				best = secondNode_.getMidPointConnection(this,best);
			} else {
				throw new RuntimeException("Assertion error : getMidPointConnection called with invalid blockingNode");
			}
			final double myPathDiff = getMaximumPathDifference();
			final double bestDiff = best.getMaximumPathDifference();

			return (myPathDiff<bestDiff) ? this : best;
		}

		public Connection getMidPointConnection() {
			Connection best = this;
			best = getMidPointConnection(firstNode_,best);
			best = getMidPointConnection(secondNode_,best);
			return best;
		}
		public Node getMidPointRooted() {
			return getMidPointConnection().getRootedAround();
		}
        /**
         * Calculates the maximum path length from the non-blocking node to any of its descendant leaves,
         * without traversing back along this connection. This is typically used in unrooted tree contexts
         * where a connection defines a split, and one side of the split is being queried.
         *
         * @param blockingNode The {@code UndirectedNode} on this connection that represents the direction *not* to search.
         * @return The maximum cumulative branch length from the non-blocking node to any leaf in the subtree,
         * excluding the length of this connection itself.
         */
        public double getMaxLengthToLeaf(UndirectedNode blockingNode) {
            if(secondNode_==blockingNode) {
                return getMaximumPathLengthToLeafViaFirst();
            }
            if(firstNode_==blockingNode) {
                return getMaximumPathLengthToLeafViaSecond();
            }
            throw new RuntimeException("Connection.GetMaxLengthToLeaf() called from unknown asking node");
        }

        /**
         * Triggers a complete recalculation of the maximum path lengths and related path information
         * for this connection and recursively for all connected parts of the unrooted tree.
         */
        public void recalculateMaximumPathLengths() {
            clearPathInfo();
            updatePathInfo();
            assertPathInfo();
        }

        /**
         * Asserts that the path information has been correctly calculated and stored for this connection.
         * This is a convenience method that calls the full assertion with no blocking node.
         *
         * @throws RuntimeException if not all nodes have path information set up correctly.
         */
        public void assertPathInfo() {
            assertPathInfo(null);
        }

        /**
         * Recursively asserts that the path information has been correctly calculated and stored for this connection and its subtrees.
         * The assertion skips the direction indicated by the blocking node.
         *
         * @param blockingNode The {@code UndirectedNode} on this connection that represents the direction *not* to check. Can be {@code null} to check both directions.
         * @throws RuntimeException if the path information is missing (i.e., {@code isFirstPathInfoFound_} or {@code isSecondPathInfoFound_} is false) or if a recursive check fails.
         */
        public void assertPathInfo(UndirectedNode blockingNode) {
            if(isFirstPathInfoFound_&&isSecondPathInfoFound_) {
                if(blockingNode!=firstNode_) {
                    firstNode_.callMethodOnConnections(this,ASSERT_PATH_INFO_CALLER);
                }
                if(blockingNode!=secondNode_) {
                    secondNode_.callMethodOnConnections(this,ASSERT_PATH_INFO_CALLER);
                }
            } else {
                throw new RuntimeException("Assertion error : assertPathInfo failed!");
            }
        }
		public void updatePathInfo() {
			updatePathInfo(null);
		}
		public void updatePathInfo(UndirectedNode blockingNode) {
			if(!isFirstPathInfoFound_) {
				this.maximumPathLengthToLeafViaFirstNode_ = firstNode_.getMaximumPathLengthToLeaf(this);
				isFirstPathInfoFound_ = true;
			}
			if(blockingNode!=firstNode_) {
				firstNode_.callMethodOnConnections(this,UPDATE_PATH_INFO_CALLER);
			}

			if(!isSecondPathInfoFound_) {
				this.maximumPathLengthToLeafViaSecondNode_ = secondNode_.getMaximumPathLengthToLeaf(this);
				isSecondPathInfoFound_ = true;
			}
			if(blockingNode!=secondNode_) {
				secondNode_.callMethodOnConnections(this,UPDATE_PATH_INFO_CALLER);
			}
		}
		public void clearPathInfo() {
			clearPathInfo(null);
		}
		public void clearPathInfo(UndirectedNode blockingNode) {
			this.isFirstPathInfoFound_ = false;
			this.isSecondPathInfoFound_ = false;
			if(blockingNode!=firstNode_) {
				this.firstNode_.callMethodOnConnections(this,CLEAR_PATH_INFO_CALLER);
			}
			if(blockingNode!=secondNode_) {
				this.secondNode_.callMethodOnConnections(this,CLEAR_PATH_INFO_CALLER);
			}
		}
		public final double getDistance() { return distance_; }
		public final boolean isConnectedTo(final UndirectedNode node) {
			return(node==firstNode_)||(node==secondNode_);
		}
		public final UndirectedNode getOtherEnd(final UndirectedNode oneEnd) {
			if(oneEnd==firstNode_) {		return secondNode_;		}
			if(oneEnd==secondNode_) {		return firstNode_;		}
			throw new RuntimeException("Assertion error : getOtherEnd called with non connecting node");
		}
		public final void instructRootedAround(RootedTreeInterface rootedInterface) {
			RootedTreeInterface.RNode root = rootedInterface.createRoot();
			instructRootedAround(root);
		}
		public final void instructRootedAround(RootedTreeInterface.RNode peer) {

			double leftDist = getMaximumPathLengthToLeafViaFirst();
			double rightDist = getMaximumPathLengthToLeafViaSecond();

			double diff = leftDist-rightDist;
			if(diff>distance_) {
				diff = 0;//distance_;
			} else if(diff<-distance_) {
				diff = 0;//-distance_;
			}
		  peer.resetChildren();
			RootedTreeInterface.RNode left = peer.createRChild();
			RootedTreeInterface.RNode right = peer.createRChild();
		  RootedTreeInterface.RBranch leftBranch = left.getParentRBranch();
			RootedTreeInterface.RBranch rightBranch = right.getParentRBranch();
		  leftBranch.setLength((distance_-diff)/2);
			rightBranch.setLength((distance_+diff)/2);

			if(annotation_!=null) {
				leftBranch.setAnnotation( annotation_ );
				rightBranch.setAnnotation( annotation_ );
			}
			firstNode_.instruct(left, this);
			secondNode_.instruct(right, this);
		}
		public final Node getRootedAround() {
			double leftDist = getMaximumPathLengthToLeafViaFirst();
			double rightDist = getMaximumPathLengthToLeafViaSecond();

			double diff = leftDist-rightDist;
			if(diff>distance_) {
				diff = 0;//distance_;
			} else if(diff<-distance_) {
				diff = 0;//-distance_;
			}

			Node left = firstNode_.buildTree(this, (distance_-diff)/2);
			Node right = secondNode_.buildTree(this, (distance_+diff)/2);
			Node n = NodeFactory.createNode(new Node[] { left, right});
			return n;

		}
		public final Node getRootedAround(double distanceForFirstChild) {
			double distanceForSecondChild = distance_-distanceForFirstChild;
			if(distanceForSecondChild<0) {
				distanceForFirstChild = distance_;
				distanceForSecondChild = 0;
			}
			Node left = firstNode_.buildTree(this, distanceForFirstChild);
			Node right = secondNode_.buildTree(this, distanceForSecondChild);
			Node n = NodeFactory.createNode(new Node[] { left, right});
			return n;
		}
        /**
         * Roots the tree around the Most Recent Common Ancestor (MRCA) of the specified outgroup members,
         * but explicitly restricts the distance of the ingroup branch to a maximum specified length
         * (often done for aesthetic reasons in visualization).
         * Note: This rooting method is not the most efficient available.
         *
         * @param ingroupDistance The desired maximum length of the branch leading to the ingroup clade (the new root branch length).
         * @param outgroupMembers An array of strings containing the names of the members defining the outgroup.
         * @return The new {@code Node} representing the root of the rooted tree, with the ingroup branch length adjusted.
         */
        public final Node getRootedAround(double ingroupDistance, String[] outgroupMembers) {
            final UndirectedNode ingroup, outgroup;
            if(firstNode_.getMRCA(this,outgroupMembers)!=null) {
                outgroup = firstNode_;
                ingroup = secondNode_;
            }  else {
                ingroup = firstNode_;
                outgroup = secondNode_;
            }
            double distanceForOutgroup = distance_-ingroupDistance;
            if(distanceForOutgroup<0) {
                ingroupDistance = distance_;
                distanceForOutgroup = 0;
            }
            Node left = ingroup.buildTree(this, ingroupDistance);
            Node right = outgroup.buildTree(this, distanceForOutgroup);
            return NodeFactory.createNode(new Node[] { left, right});
        }
	}


// =-=-=-=-=-=-=----==-=-=--==--=-==-=--=-==--==--==--=-=--=----==-=-=-=-=-==-=
// ==== Static methods
// -------------------
	/**
	 * @return a new tree constructions with node n as root
	 */
	private final static Tree constructTree(Node n, int units) {
		SimpleTree st = new SimpleTree(n);
		st.setUnits(units);
		return st;
	}
	/**
	 * @return tre if name is in names
	 */
	private static final boolean contains(String[] names, String name) {
		for(int i = 0 ; i < names.length ; i++) {
			if(name.equals(names[i])) { return true; }
		}
		return false;
	}
// =-=-=-=-=-=-=----==-=-=--==--=-==-=--=-==--==--==--=-=--=----==-=-=-=-=-==-=

	private static interface ConnectionMethodCaller {
		public void callOn(Connection c, UndirectedNode callingNode);
	}

	private static final ConnectionMethodCaller ASSERT_PATH_INFO_CALLER =
		new ConnectionMethodCaller() {
			public void callOn(Connection c, UndirectedNode callingNode) {	c.assertPathInfo(callingNode);	}
		};

	private static final ConnectionMethodCaller CLEAR_PATH_INFO_CALLER =
		new ConnectionMethodCaller() {
			public void callOn(Connection c, UndirectedNode callingNode) {	c.clearPathInfo(callingNode);	}
		};

	private static final ConnectionMethodCaller UPDATE_PATH_INFO_CALLER =
		new ConnectionMethodCaller() {
			public void callOn(Connection c, UndirectedNode callingNode) {	c.updatePathInfo(callingNode);	}
		};

	private static final ConnectionMethodCaller GET_NUMBER_OF_CONNECTIONS_CALLER =
		new ConnectionMethodCaller() {
			public void callOn(Connection c, UndirectedNode callingNode) {	c.getNumberOfConnections(callingNode);	}
		};

// =-=-=-=-=-=-=----==-=-=--==--=-==-=--=-==--==--==--=-=--=----==-=-=-=-=-==-=

	/**
	 * A node with no set idea of parent and children (just sibling connections)
	 */
	private static final class UndirectedNode {
		private Connection[] connectedNodes_;
		private final Node palPeer_;
		private final String label_;
		private final Object annotation_;

		/**
		 * Auto expands
		 */
		private UndirectedNode(Connection connection, int childStartingIndex, SimpleNode parent) {
			this.palPeer_ = null;
			this.label_ = null;
			this.annotation_ = null;
			this.connectedNodes_ = new Connection[3];
			int numberOfChildren = parent.getNumberOfChildren();
			this.connectedNodes_[0] = connection;
			if((numberOfChildren-childStartingIndex)==2)  {
				this.connectedNodes_[1] =
					new Connection(this,parent.getChild(childStartingIndex), EXPAND_CONSTRUCTION);
				this.connectedNodes_[2] =
					new Connection(this,parent.getChild(childStartingIndex+1), EXPAND_CONSTRUCTION);

			} else {
				this.connectedNodes_[1] = new Connection(this,parent.getChild(childStartingIndex), EXPAND_CONSTRUCTION);
				this.connectedNodes_[2] = new Connection(this,parent,childStartingIndex+1,0,null);
			}
		}
		/**
		 * The already unrooted tree constructor.
		 * @param peer The root of the tree (expects three or more children)
		 * @param constructionMode The construction mode
		 * @throws IllegalArgumentException if peer has less than three children
		 */
		public UndirectedNode(SimpleNode peer, int constructionMode) {

			final int numberOfChildren = peer.getNumberOfChildren();
			if(numberOfChildren<=2) {
			  throw new IllegalArgumentException("Peer must have at least three children!");
			}
			this.palPeer_ = peer.getPALPeer();
			this.label_ = peer.getLabel();
			this.annotation_ = peer.getLabel();
			if(constructionMode==REDUCE_CONSTRUCTION) {
				int numberOfReducedChildren = countReducedChildren(peer);
				this.connectedNodes_ = new Connection[numberOfReducedChildren];
				for(int i = 0 ;i < numberOfReducedChildren ; i++) {

					Connection c = new Connection(this,getReducedChild(peer, i), REDUCE_CONSTRUCTION);
					this.connectedNodes_[i] = c;
				}
			}	else if((constructionMode==MIMIC_CONSTRUCTION)||(numberOfChildren<=3))  {

				//Plus one for parent connection
				this.connectedNodes_ = new Connection[numberOfChildren];
				for(int i = 0 ; i< numberOfChildren ; i++) {
					this.connectedNodes_[i] = new Connection(this,peer.getChild(i),constructionMode);
				}
			} else  {
				//Expand construction
				this.connectedNodes_ = new Connection[3];
				this.connectedNodes_[0] = new Connection(this,peer.getChild(0), constructionMode);
				this.connectedNodes_[1] = new Connection(this,peer.getChild(1), constructionMode);
				this.connectedNodes_[2] = new Connection(this,peer,2, 0,null);
			}
		}
		private UndirectedNode( int constructionMode, Connection parentConnection, SimpleNode peer) {
			this.palPeer_ = peer.getPALPeer();
			this.label_ = peer.getLabel();
			this.annotation_ = peer.getAnnotation();
			final int numberOfChildren = peer.getNumberOfChildren();
			if(constructionMode==REDUCE_CONSTRUCTION) {

				int numberOfReducedChildren = countReducedChildren(peer);
			  this.connectedNodes_ = new Connection[numberOfReducedChildren+1];
				this.connectedNodes_[0] = parentConnection;
				for(int i = 0 ;i < numberOfReducedChildren ; i++) {
					Connection c = new Connection(this, getReducedChild(peer, i),REDUCE_CONSTRUCTION);
					this.connectedNodes_[i+1] = c;
				}
			} else if((constructionMode==MIMIC_CONSTRUCTION)||(numberOfChildren<=2))  {
				//Plus one for parent connection
				this.connectedNodes_ = new Connection[numberOfChildren+1];
				this.connectedNodes_[0] = parentConnection;
				for(int i = 0 ; i< numberOfChildren ; i++) {
					this.connectedNodes_[i+1] = new Connection(this,peer.getChild(i),constructionMode);
				}
			} else {
				this.connectedNodes_ = new Connection[3];
				this.connectedNodes_[0] = parentConnection;
				this.connectedNodes_[1] = new Connection(this,peer.getChild(0), constructionMode);
				this.connectedNodes_[2] = new Connection(this, peer, 1,0, null);
			}
		}
		private UndirectedNode(UndirectedNode orginal, Connection attachmentPoint, SimpleNode subTree, int constructionModel, Connection parent) {
		 throw new RuntimeException("Not implemented yet!");
		}
		public final UndirectedNode getAttached( Connection attachmentPoint, SimpleNode subTree, int constructionMode, Connection parent ) {
		  return new UndirectedNode(this,attachmentPoint,subTree, constructionMode,parent);
		}
		private static final int countReducedChildren(SimpleNode base) {
			int count = 0;
			int childCount = base.getNumberOfChildren();
			for(int  i = 0 ; i < childCount ; i++) {
			  SimpleNode c = base.getChild(i);
				if(!c.isLeaf()&&c.getParentBranchLength()<=BranchLimits.MINARC) {
				  count+=countReducedChildren(c);
				} else {
				  count++;
				}
			}
			return count;
	  }

	  private static final SimpleNode getReducedChild(SimpleNode base, int childIndex){
			int childCount = base.getNumberOfChildren();
			for(int  i = 0 ; i < childCount ; i++) {
			  SimpleNode c = base.getChild(i);
				if(!c.isLeaf()&&c.getParentBranchLength()<=BranchLimits.MINARC) {
				  SimpleNode rc = getReducedChild(c,childIndex);
					if(rc!=null) {
						return rc;
					}
					childIndex-=countReducedChildren(c);
				} else {
					if(childIndex == 0) {
					  return c;
					}
					childIndex--;
				}
			}
			return null;
	  }
		public void instruct(UnrootedTreeInterface.UNode node, Connection callingConnection) {

			if(label_!=null) {			  node.setLabel(label_);			}
			if(annotation_!=null) {			node.setAnnotation(annotation_);			}

			for(int i = 0 ; i < connectedNodes_.length ; i++) {
			  Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
				  c.instruct(node.createUChild().getParentUBranch(),this);
				}
			}
		}
		public final int getNumberOfMatchingLeaves(String[] leafSet, Connection caller)  {
		  if(isLeaf()) {
			  return contains(leafSet,label_) ? 1 : 0;
			} else{
				int count = 0;
		  	for(int i = 0 ; i < connectedNodes_.length ; i++) {
			    Connection c = connectedNodes_[i];
			  	if(c!=caller) {
						count+=c.getNumberOfMatchingLeaves(leafSet, this);
				  }
			  }
				return count;
			}
		}

		public int getExactCladeCount(String[] possibleCladeMembers,Connection caller) {
		  if(isLeaf()) {
			  return (pal.misc.Utils.isContains(possibleCladeMembers, label_) ? 1 : 0 );
			}
			int count = 0;
			for(int i = 0 ; i < connectedNodes_.length ; i++) {
			  Connection c = connectedNodes_[i];
				if(c!=caller) {
				  int subCount = c.getExactCladeCount(possibleCladeMembers,this);
					if(subCount<0) { return -1; }
					if(subCount==0) {
					  if(count>0) { return -1; }
					} else if(i==0) {
						count=subCount;
					} else if(count==0) {
						return -1;
					} else {
					  count+=subCount;
					}
				}
			}
			return count;
		}

		public void instruct(RootedTreeInterface.RNode base, Connection callingConnection) {

			if(label_!=null) {  base.setLabel(label_);		}
			if(annotation_!=null) { base.setAnnotation(annotation_); }

			for(int i = 0 ; i < connectedNodes_.length ; i++) {
			  Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
				  c.instruct(base.createRChild().getParentRBranch(),this);
				}
			}
		}
		public Connection getPeerParentConnection() { return connectedNodes_[0];	}
		private void assertCallingConnection(final Connection callingConnection) {
			boolean found = false;
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				if(connectedNodes_[i]==callingConnection) {
					found = true; break;
				}
			}
			if(!found) {
				throw new RuntimeException("Assertion error : calling connection not one of my connections");
			}
		}
		public void callMethodOnConnections(Connection callingConnection, ConnectionMethodCaller caller) {
			assertCallingConnection(callingConnection);
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				if(connectedNodes_[i]!=callingConnection) {
					caller.callOn(connectedNodes_[i],this);
				}
			}
		}
		public int getNumberOfConnections() {
			return getNumberOfConnections(null);
		}

		public int getNumberOfConnections(Connection callingConnection) {
			int count = 0;
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
					count+=c.getNumberOfConnections(this);
				}
			}
			return count;
		}

		public final void addLabels(ArrayList store, Connection callingConnection) {
			int count = 0;
			if(connectedNodes_.length==1) {
			  if(callingConnection!=connectedNodes_[0]) {
				  throw new RuntimeException("Assertion error : calling connection not recognised");
				}
				store.add(label_);
			} else {
				for( int i = 0; i<connectedNodes_.length; i++ ) {
					Connection c = connectedNodes_[i];
					if( c!=callingConnection ) {
						c.addLabels( store, this );
					}
				}
			}
		}
		/**
		 * Get all the connections of the tree that includes this node
		 */
		public Connection[] getAllConnections() {
			int size = getNumberOfConnections();
			Connection[] array = new Connection[size];
			getConnections(array,0);
			return array;
		}
		/**
		 * Fill in all connections fanning out from this node
		 */
		public int getConnections(Connection[] array, int index) {
			return getConnections(null, array, index);
		}
		/**
		 * @return new index for inserting connections into array. Assumes array is large enough
		 */
		public int getConnections(Connection callingConnection, Connection[] array, int index) {
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
					index=c.getConnections(this, array,index);
				}
			}
			return index;
		}

		private final Connection getMidPointConnection(Connection callingConnection, Connection best) {
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
					best = c.getMidPointConnection(this,best);
				}
			}
			return best;
		}

		public final Connection getMRCAConnectionBaseTraverse(Connection callingConnection,String[] nodeNames) {
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
					Connection mrca = c.getMRCAConnectionBaseTraverse(this,nodeNames);
					if(mrca !=null) {
						return mrca;
					}
				}
			}
			return null;
		}
		public final int getAllMRCAConnectionBaseTraverse(Connection callingConnection,String[] nodeNames, Connection[] store, int numberInStore) {
			for(int i = 0 ; i < connectedNodes_.length ; i++ ){
				Connection c = connectedNodes_[i];
				if(c!=callingConnection) {
					numberInStore = c.getAllMRCAConnectionBaseTraverse(this,nodeNames,store,numberInStore);
				}
			}
			return numberInStore;
		}
		/**
		 * @return true if this is a leaf node (has only one connection at most)
		 */
		public final boolean isLeaf() {
			return connectedNodes_.length<=1;
		}


		/**
		 * @return the maximum path length to a leaf without following the blocking connection
		 */
		public double getMaximumPathLengthToLeaf(Connection blockingConnection) {
			double maxPathLength = 0;
			for(int i = 0 ; i < connectedNodes_.length ; i++) {
				final Connection c = connectedNodes_[i];
				if(connectedNodes_[i]!=blockingConnection) {
					UndirectedNode other = c.getOtherEnd(this);
					double length = c.getMaxLengthToLeaf(this)+c.getDistance();
					maxPathLength = Math.max(maxPathLength,length);
				}
			}
			return maxPathLength;
		}
		/**
		 * Build a tree starting at this node and not going via blockingNode, with a branch length as set by distance
		 * @param blockingNode The sibling node not to include in the tree
		 * @param distance the branch length for the root
		 */
		public Node buildTree(Connection blockingConnection, double distance) {
			if(connectedNodes_.length==1) {
				return NodeFactory.createNodeBranchLength(distance, new Identifier(label_));
			}
			Node[] children = new Node[connectedNodes_.length-1];
			int addIndex = 0;
			for(int i = 0 ; i < connectedNodes_.length ; i++) {
				if(blockingConnection!=connectedNodes_[i]) {
					UndirectedNode other = connectedNodes_[i].getOtherEnd(this);
					children[addIndex++] = other.buildTree(connectedNodes_[i], connectedNodes_[i].distance_);
				}
			}
			return NodeFactory.createNodeBranchLength(distance, children);
		}
		/**
		 * @return an unrooted tree around this node
		 * Note: does not work if this node is a leaf node (one connection)! (should check and use another node)
		 */
		public Node buildUnrootedTree() {
			if(connectedNodes_.length==1) {
				return NodeFactory.createNode(new Identifier(label_));
			}
			Node[] children = new Node[connectedNodes_.length];
			for(int i = 0 ; i < connectedNodes_.length ; i++) {
				UndirectedNode other = connectedNodes_[i].getOtherEnd(this);
				children[i] = other.buildTree(connectedNodes_[i], connectedNodes_[i].distance_);
			}
			return NodeFactory.createNode(children);
		}

		public UndirectedNode getMRCA(Connection callingConnection, String[] nodeNames ) {
			if(isLeaf()) {
				if(contains(nodeNames,label_)) {
					return this;
				}
				return null;
			}
			int count = 0;
			UndirectedNode lastMRCA = null;

			for(int i = 0 ; i < connectedNodes_.length ; i++) {
				final Connection c = connectedNodes_[i];
				if(callingConnection!=c) {
					UndirectedNode other = c.getOtherEnd(this);
					UndirectedNode mrca = other.getMRCA(c,nodeNames);
					if(mrca!=null) {
						count++;
						lastMRCA = mrca;
					}
				}
			}
			switch(count) {
				case 0 : { return null; } //Leafs aren't here
				case 1 : { return lastMRCA; } //We are no better than last MRCA
				default : { return this; } //We are intersection of multiple paths to outgroups
			}
		}
		public Connection getMRCAConnection(Connection callingConnection, String[] nodeNames ) {
			if(isLeaf()) {
				if(contains(nodeNames,label_)) {
					return callingConnection;
				}
				return null;
			}
			int count = 0;
			Connection lastMRCA = null;

			for(int i = 0 ; i < connectedNodes_.length ; i++) {
				final Connection c = connectedNodes_[i];
				if(callingConnection!=c) {
					Connection mrca = c.getMRCAConnection(this,nodeNames);
					if(mrca!=null) {
						count++;
						lastMRCA = mrca;
					}
				}
			}
			switch(count) {
				case 0 : { return null; } //Leafs aren't here
				case 1 : { return lastMRCA; } //We are no better than last MRCA
				default : { return callingConnection; } //We are intersection of multiple paths to outgroups
			}
		}
		private UndirectedNode getRelatedNode(Node peer, Connection callerConnection) {
			if(palPeer_ == peer) { return this; }
			for(int i = 0 ; i < connectedNodes_.length ; i++) {
				final Connection c = connectedNodes_[i];
				if(c!=callerConnection) {
					UndirectedNode n = connectedNodes_[i].getOtherEnd(this).getRelatedNode(peer,c);
					if(n!=null) { return n; }
				}
			}
			return null;
		}
		public UndirectedNode getRelatedNode(Node peer) {
			return getRelatedNode(peer,null);
		}
	}
	// =-=-=-==--=-=-=-=-=-==--=-=-==--==-=--==--==--=-=-==-=-=-=--==--==--==--==
	/**
	 * Root Iterator
	 */
	private static final class RootIterator implements TreeIterator {
		private final Connection[] connections_;
		private final int units_;
		private int currentConnection_;

		public RootIterator(Connection[] connections, int units) {
			this.connections_ = connections;
			this.units_ = units;
			this.currentConnection_ = 0;
		}
		public Tree getNextTree(AlgorithmCallback callback) {
			return constructTree(connections_[currentConnection_++].getRootedAround(),units_);
		}
		public boolean isMoreTrees() { return currentConnection_!=connections_.length; }
	} //End of class RootIterator
	// ===================================================================================================
	// ===================================================================================================
	/**
	 * The actual, hidden implementation of BranchAccess
	 */
	private static final class BranchAccessImpl implements BranchAccess {
	  private final Connection connection_;
		private final int units_;
		private final TreeManipulator parent_;
		public BranchAccessImpl(TreeManipulator parent, Connection connection, int units) {
			this.connection_ = connection;
			this.units_ = units;
			this.parent_ = parent;
	  }
		public TreeManipulator attachSubTree(Node subTree, int constructionMode) {
			return new TreeManipulator(parent_,connection_,subTree,constructionMode);
		}
		public String[][] getLabelSplit() {
			return connection_.getLabelSplit();
		}
		public void setAnnotation(Object annotation) {
		  connection_.setAnnotation(annotation);
		}
	}
	// ===================================================================================================
	// ===================================================================================================
	// ===================================================================================================
	/**
	 * The branch access objects allow specific operations on a particular branch (refered to as connections
	 * internally to confuse and bewilder)
	 */
	public static interface BranchAccess {
        /**
         * Creates a new {@code TreeManipulator} object resulting from **grafting** a specified subtree
         * onto the middle of the branch represented by this connection. The original {@code TreeManipulator} is not modified.
         *
         * @param subTree The subtree to be attached, provided as a normal {@code Node} object.
         * @param constructionMode An integer flag specifying how the internal unrooted tree representation should be constructed (e.g., {@code MIMIC_CONSTRUCTION}).
         * @return A new {@code TreeManipulator} instance containing the modified tree structure.
         */
        public TreeManipulator attachSubTree(Node subTree, int constructionMode);

        /**
         * Obtains the partition of the tree's tip labels (taxa) into two sets, based on the split defined by this branch.
         *
         * @return A two-dimensional array of string arrays. The first element (index 0) is a string array of label names on one side of the split, and the second element (index 1) is a string array of the remaining label names.
         */
        public String[][] getLabelSplit();

        /**
         * Sets an annotation object associated with this specific branch.
         * This annotation will be passed to a {@code TreeInterface} when instructing it to build a tree representation,
         * allowing external visualization tools or models to apply branch-specific metadata.
         *
         * @param annotation The annotation object. The type and meaning of this object depend on the specific {@code TreeInterface} used.
         */
        public void setAnnotation(Object annotation);
	}
// ==============================================================================================
// ================================== Support Classes ===========================================
// ==============================================================================================

	private static interface SimpleNode {
		public boolean isLeaf();
		public int getNumberOfChildren();
		public SimpleNode getChild(int child);
		public SimpleBranch getParentBranch();
		public Object getAnnotation();

		public String getLabel();
        /**
         * Returns the branch length connecting this node to its parent.
         *
         * @return The length of the branch leading to the parent node, or {@code 0.0} if this node is the root and has no parent branch defined.
         */
        public double getParentBranchLength();
		public Node getPALPeer();
	}
	private static interface SimpleBranch {
		public double getBranchLength();
		public Object getAnnotation();
		public SimpleNode getParentNode();
		public SimpleNode getChildNode();
	}
	private static final class RootedInterfaceImpl implements RootedTreeInterface {
		private InstructableNode root_;
		public RNode createRoot() {
			this.root_ = new InstructableNode();
			return root_;
		}
		public SimpleNode getSimpleRoot() { return root_; }
	}
	private static final class UnrootedInterfaceImpl implements UnrootedTreeInterface {
		private InstructableBranch root_;
		public BaseBranch createBase() {
			this.root_ = new InstructableBranch();
			return root_;
		}
		public SimpleBranch getSimpleRootBranch() { return root_; }


	}

	private static final class InstructableNode implements SimpleNode, RootedTreeInterface.RNode, UnrootedTreeInterface.UNode {
		private final InstructableBranch parent_;
	  private String label_;
		private ArrayList children_ = null;

		public Object annotation_;

		public InstructableNode() {
			this((InstructableBranch)null);
		}
		public String getLabel() { return label_; }
		public InstructableNode(InstructableNode parent) {
			this.parent_ = new InstructableBranch(parent,this);
		}
		public InstructableNode(InstructableBranch parent) {
			this.parent_ = parent;
		}
		public void setAnnotation(Object annotation) {
			this.annotation_ = annotation;
		}
		public Object getAnnotation() { return annotation_; }
		// Simple Node stuff
		public boolean isLeaf() {
			return children_ == null || children_.size()==0;
		}
		public int getNumberOfChildren() {
			return children_ == null ? 0 : children_.size();
		}
		public SimpleNode getChild(int child) {
			return (SimpleNode)children_.get(child);
		}
		public SimpleBranch getParentBranch() { return parent_;		}
		public double getParentBranchLength() { return (parent_ == null ? 0 : parent_.getBranchLength()); }
		public Node getPALPeer() { return null; }

		// General stuff
		public void setLabel(String label) { this.label_ = label; }

		public void resetChildren() {
			if(children_!=null) { children_.clear(); }
		}
		private final InstructableNode createChildImpl() {
			InstructableNode child = new InstructableNode(this);
			if(children_ ==null) {
				children_ = new ArrayList();
			}
			children_.add(child);
			return child;
		}

		// Rooted stuff
		public RootedTreeInterface.RBranch getParentRBranch() { return parent_; 		}
		public RootedTreeInterface.RNode createRChild() {  return createChildImpl();		}

		// Unrooted stuuff
		public UnrootedTreeInterface.UBranch getParentUBranch() {		return parent_;	}
		public UnrootedTreeInterface.UNode createUChild() {  return createChildImpl();		}



	} //End of class InstructableNode

	private static final class InstructableBranch implements SimpleBranch, RootedTreeInterface.RBranch, UnrootedTreeInterface.UBranch,UnrootedTreeInterface.BaseBranch {
		private final InstructableNode parent_;
		private final InstructableNode child_;

		private double length_;
		private Object annotation_;
		public InstructableBranch() {
			this.parent_ = new InstructableNode(this);
			this.child_ = new InstructableNode(this);
		}
		public SimpleNode getParentNode() { return parent_; }

		public SimpleNode getChildNode() { return child_; }


		public InstructableBranch(InstructableNode parent, InstructableNode child) {
			this.parent_ = parent;
			this.child_ = child;
		}
		public double getBranchLength() { return length_; }
		public Object getAnnotation() { return annotation_; }
		public void setLength(double length) {	this.length_ = length;		}
		public void setAnnotation(Object annotation) {	this.annotation_ = annotation;	}
		public RootedTreeInterface.RNode getMoreRecentNode() { return child_; }
		public RootedTreeInterface.RNode getLessRecentNode() { return parent_; }
		public UnrootedTreeInterface.UNode getCloserNode() { return parent_; }
		public UnrootedTreeInterface.UNode getFartherNode() { return child_; }
		public UnrootedTreeInterface.UNode getLeftNode() { return parent_; }
		public UnrootedTreeInterface.UNode getRightNode() { return child_; }
	}
	public static final class PALBranchWrapper implements SimpleBranch {
		private final PALNodeWrapper parent_;
		private final PALNodeWrapper child_;
		private final double branchLength_;

		public PALBranchWrapper(PALNodeWrapper parent, PALNodeWrapper child, double branchLength) {
		  this.parent_ = parent;
			this.child_ = child;
			this.branchLength_ = branchLength;
		}
		public SimpleNode getParentNode() { return parent_; }

		public SimpleNode getChildNode() { return child_; }

		public final double getBranchLength() {		return branchLength_;		}
		public final Object getAnnotation() { return null; }
	}
	public static final class PALNodeWrapper implements SimpleNode {
		private final Node peer_;
		private final PALNodeWrapper[] children_;
		private final PALBranchWrapper parentBranch_;

		public PALNodeWrapper(Node peer) {
			this(peer,null);
		}
		public PALNodeWrapper(Node peer, PALNodeWrapper parent) {

			this.peer_ = peer;
			if(parent==null) {
				this.parentBranch_ = null;
			} else {
				this.parentBranch_ = new PALBranchWrapper(parent,this, peer.getBranchLength());
			}
			this.children_ = new PALNodeWrapper[peer.getChildCount()];
		  for(int i = 0 ; i < children_.length ; i++) {
				this.children_[i] = new PALNodeWrapper(peer.getChild(i), this);
			}
		}
		public Object getAnnotation() { return null; }
		public String getLabel() {
		  Identifier id = peer_.getIdentifier();
			if(id!=null) {
			  return id.getName();
			}
			return null;
		}
		public boolean isLeaf() { return children_.length == 0; }
		public int getNumberOfChildren() { return children_.length; }
		public SimpleNode getChild(int child) { return children_[child]; }
		public SimpleBranch getParentBranch() {	return parentBranch_;		}
		public Node getPALPeer() { return peer_; }
		public double getParentBranchLength() {
			return parentBranch_==null ? 0 : parentBranch_.getBranchLength();
		}
	}

}