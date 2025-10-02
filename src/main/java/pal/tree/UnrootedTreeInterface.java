// UnrootedTreeInterface.java
//
// (c) 1999-2003 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.tree;

/**
 * <p>Title: UnrootedTreeInterface </p>
 * <p>Description: An interface to construction of an Unrooted Tree</p>
 * @author Matthew Goode
 * @version 1.0
 */

public interface UnrootedTreeInterface {

	public BaseBranch createBase();

	// ================
	// == Instructee ==
	// ================
	public static interface Instructee {
		public void instruct(UnrootedTreeInterface treeInterface);
	}

	// ==========
	// == Node ==
	// ==========
	public static interface UNode {
        /**
         * Retrieves the upstream branch connected to the current branch, which is typically the branch
         * closer to the tree's root or base.
         *
         * @return The parent {@code UBranch} object, which represents the branch connecting this node's parent (or the base of the tree).
         */
        public UBranch getParentUBranch();
		public void setLabel(String label);
		public void setAnnotation(Object annotation);
		public void resetChildren();

        /**
         * Creates a new child node ({@code UNode}) and attaches it to the current node,
         * effectively extending the branch further away from the base (root) of the tree structure.
         *
         * @return The newly created child {@code UNode} object.
         */
        public UNode createUChild();

	}
	// ====================
	// == General Branch ==
	// ====================
	public static interface GeneralBranch {
		public void setLength(double length);
		public void setAnnotation(Object annotation);
	}

	// ============
	// == Branch ==
	// ============
	public static interface UBranch extends GeneralBranch {
		public UNode getCloserNode();
		public UNode getFartherNode();
	}
	// =======================
	// == Idea Base Branch ==
	// =======================

	public static interface BaseBranch extends GeneralBranch {
		public UNode getLeftNode();
		public UNode getRightNode();
	}

	public static final class Utils {
        /**
         * Recursively builds the structure of the display tree (represented by UNode objects)
         * based on the topology and branch lengths of the standard PAL tree nodes.
         *
         * @param palNode The current node from the source PAL tree being processed.
         * @param uNode The corresponding UNode in the display tree structure being built.
         */
        private final static void create(Node palNode, UNode uNode) {

            int numberOfChildren = palNode.getChildCount();
            uNode.resetChildren();
            if(numberOfChildren==0) {
                uNode.setLabel(palNode.getIdentifier().getName());
            } else {
                for( int i = 0; i<numberOfChildren; i++ ) {
                    Node palChild = palNode.getChild( i );
                    UNode displayChild = uNode.createUChild();
                    UBranch b = displayChild.getParentUBranch();
                    b.setLength( palChild.getBranchLength() );
                    create( palChild, displayChild );
                }
            }
        }
        /**
         * Builds the {@code UnrootedTreeInterface} display structure based on a normal PAL tree's root node.
         * This method ensures the input node is effectively unrooted (trifurcating root) before conversion.
         *
         * @param root The root node of the source PAL tree.
         * @param treeInterface The {@code UnrootedTreeInterface} object that will receive the new tree structure.
         * Note: If the provided root has only two children (rooted), it is first converted to a midpoint-rooted, three-child structure.
         */
        public static final void instruct(Node root, UnrootedTreeInterface treeInterface) {
            if(root.getChildCount()!=2) {
                root = new TreeManipulator(root).getMidPointRooted();
            }
            BaseBranch b = treeInterface.createBase();
            Node palLeft = root.getChild(0);
            Node palRight = root.getChild(1);
            b.setLength(palLeft.getBranchLength()+palRight.getBranchLength());
            create(palLeft, b.getLeftNode());
            create(palRight, b.getRightNode());

        }
	}
}
