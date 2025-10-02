/** This file is part of TreeCmp, a tool for comparing phylogenetic trees
    using the Matching Split distance and other metrics.
    Copyright (C) 2011,  Damian Bogdanowicz

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */

/** This code comes from the QuartetDist application, see
    Chris Christiansen, Thomas Mailund, Christian NS Pedersen,
    Martin Randers and Martin Stig Stissing, "Fast calculation of the quartet distance between trees of arbitrary
    degrees", Algorithms for Molecular Biology, 1:16, 2006.
*/

/** This is a modified and optimized version of the original code*/

package qt;

import java.util.Iterator;
/**Class for representing directed edges in a tree*/
public class Edge {
  protected int id; //the id of this edge
  protected Node from, to; //the nodes connected to this edge
  protected Edge backedge; //the oppositely directed edge of this edge
  private int size;
  
  /**Constructs new edge, connectiong the given nodes
     @param from the node this edge leads from
     @param to the node this edge leads to
  */
  public Edge(Node from, Node to) {
    this.from = from;
    this.to = to;
    size = Integer.MIN_VALUE;
  }

  
  protected void setSize() {
    if (size > 0)
      return;
    if (to instanceof Leaf)
      size = 1;
    else {
      Iterator it = to.getEdges();
      Edge next;
      size = 0;
      while (it.hasNext()) {
	next = (Edge)it.next();
	if (next.to != this.from) { //we don't want this edge's backedge
	  if (next.size <= 0)
	    next.setSize();
	  size += next.size;
	}
      }
    }
  }

/**
 * Calculates the size of the intersection of the **leaf sets** (descendants) between the
 * subtree defined by this directed edge and the subtree defined by the given {@code other} edge.
 *
 * <p>This method uses **dynamic programming** with a memoization table (`sizes`) to store and reuse
 * computed intersection sizes, which is fundamental to the quartet distance algorithm.
 *
 * @param other The directed edge representing the subtree in the other tree.
 * @param sizes The 2D integer array (table) used for memoization of known intersection sizes.
 * The value {@code Integer.MIN_VALUE} indicates an uncomputed entry.
 * @param row A boolean flag that determines the lookup indices:
 * - If {@code true}: indices are {@code [this.id][other.id]}.
 * - If {@code false}: indices are {@code [other.id][this.id]}.
 * @return The size of the intersection of the leaf sets of the two subtrees.
 */
  public int intSize(Edge other, int[][] sizes, boolean row) {
    int i,j;
    if (row) {
      i = id;
      j = other.id;
    }
    else {
      i = other.id;
      j = id;
    }
    if (sizes[i][j] == Integer.MIN_VALUE) { //compute
      if (to instanceof Leaf) { //no recursion in this tree
	if (other.to instanceof Leaf) //two leaves are easy to compare
	  sizes[i][j] = (to.equals(other.to)) ? 1 : 0;
	else { //recurse on other
	  Iterator otheredges = other.to.getEdges(); //get all outgoing edges from the to-node
	  int sum = 0;
	  Edge next;
	  while(otheredges.hasNext()) {
	    next = (Edge)otheredges.next();
	    if (next.to != other.from) //one of the edges leads back to the from-node
	      sum += next.intSize(this, sizes, !row);
	  }
	  sizes[i][j] = sum;
	}
      }
      else { //recurse on this tree
	Iterator edges = to.getEdges(); //get all outgoing edges from the node this edge points to
	int sum = 0;
	Edge next;
	while(edges.hasNext()) {
	  next = (Edge)edges.next();
	  if (next.to != from) //one of the edges is pointing back to this edge's from-node
	    sum += next.intSize(other, sizes, row);
	}
	sizes[i][j] = sum;
      }    
    }
    return sizes[i][j];
  }
  //Modified by Damian Bogdanowicz
  public int intSize(Edge other, short[][] sizes, boolean row) {
    int i,j;
    if (row) {
      i = id;
      j = other.id;
    }
    else {
      i = other.id;
      j = id;
    }
    if (sizes[i][j] == Short.MIN_VALUE) { //compute
      if (to instanceof Leaf) { //no recursion in this tree
	if (other.to instanceof Leaf) //two leaves are easy to compare
	  sizes[i][j] =(short) ((to.equals(other.to)) ? 1 : 0);
	else { //recurse on other
	  Iterator otheredges = other.to.getEdges(); //get all outgoing edges from the to-node
	  int sum = 0;
	  Edge next;
	  while(otheredges.hasNext()) {
	    next = (Edge)otheredges.next();
	    if (next.to != other.from) //one of the edges leads back to the from-node
	      sum += next.intSize(this, sizes, !row);
	  }
	  if(sum>=Short.MAX_VALUE)
              System.out.println("Error: used short type is too small!!!");
          sizes[i][j] = (short)sum;
	}
      }
      else { //recurse on this tree
	Iterator edges = to.getEdges(); //get all outgoing edges from the node this edge points to
	int sum = 0;
	Edge next;
	while(edges.hasNext()) {
	  next = (Edge)edges.next();
	  if (next.to != from) //one of the edges is pointing back to this edge's from-node
	    sum += next.intSize(other, sizes, row);
	}
	if(sum>=Short.MAX_VALUE)
              System.out.println("Error: used short type is too small!!!");
        sizes[i][j] = (short)sum;
      }
    }
    return sizes[i][j];
  }

  /**Returns the node this edge points to
     @return the node this edge points to */
  public Node pointsTo() {
    return to;
  }

  /**Returns the node this edge points away from
     @return the node this edge points away from */
  public Node pointsAwayFrom() {
    return from;
  }

  /**Returns the id of this edge
     @return the id
   * */
  public int getId() {
    return id;
  }

  /**Returns the edge pointing the opposite way of this edge
     @return the backedge
     * */
  public Edge getBackEdge() {
    return backedge;
  }

  /**Convenience method for getting the id this edges back-edge
     @return the id
     * */
  public int getBackEdgeId() {
    return backedge.id;
  }

    /**
     * Returns the size of the subtree that this edge points to,
     * measured by the number of leaves (terminal nodes) contained within that subtree.
     *
     * @return The integer size of the descendant leaf set of the node pointed to by this edge.
     */
    public int getSubtreeSize() {
        return size;
    }

  /**Computes the number of ways to select two elements from a set of
     size n (also known as 'n choose 2')
     @param n the size of the set
     @return n choose 2
  */
  private long choose2(long n) {
    return (n * (n - 1)) / 2;
  }  

  public int compareTo(Object o) {
    return id - ((Edge)o).id;
  }

}
