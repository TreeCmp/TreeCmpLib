// GroupLeader.java
//
// (c) 1999-2004 PAL Development Core Team
//
// This package may be distributed under the
// terms of the Lesser GNU General Public License (LGPL)

package pal.treesearch;

/**
 * <p>Title: GroupLeader </p>
 * <p>Description: A group leader controls a group of constrained clades in a tree</p>
 * @author Matthew Goode
 * @version 1.0
 */

public interface GroupLeader {

	public void obtainLeafInformation(HeightInformationUser user);

    /**
     * Notifies the group leader (the manager of a set of constrained nodes) that the initial
     * setup of parameters (such as branch lengths or rates) related to the group's constraints
     * has been completed.
     *
     * This signal allows the group manager to proceed with subsequent calculations,
     * such as determining internal node heights or other group-dependent values.
     *
     * @param groupConstraints The {@code GroupManager} instance that is being notified of the completion of the setup phase.
     */
    public void postSetupNotify(ConstraintModel.GroupManager groupConstraints);
//	public void recursivelyMarkHeights(double[] currentHeightComponents);
//
//	public void recursivelyUpdateHeightFromMark(double[] heightComponentsDifferences);
//
//	public void setLeafHeightsAndValidateInternalHeights(double[] heightComponents);

}