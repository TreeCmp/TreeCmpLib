package pal.algorithmics;

/**
 * <p>Title: UndoableAction</p>
 * <p>Description: A stateful, single thread object</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author Matthew Goode
 * @version 1.0
 */
import java.util.*;
public interface UndoableAction {
    /**
     * Perform an action.
     *
     * @param currentScore     The current score before doing the action.
     * @param desparationValue An indication by the processing machines of willingness to do more extreme actions.
     *                         A value of 0 means not desperate at all, a value of 1 means very desperate.
     * @return the current score after doing the action (or the input score if not successful).
     */
  public double doAction(double currentScore, double desparationValue);

  /**
   * Was the last action deterministic? That is, if it wasn't chosen and state is still as
   * before is it worth doing it again?
   * @return true if last action deterministic
   */
  public boolean isActionDeterministic();

  /**
   * Was the last call to doAction() succesful?
   * @return true if last action successful, false otherwise
   */
  public boolean isActionSuccessful();
  /**
   * Undo the last action (if it was successful)
   * Users of undoable actions should accept that sometimes it isn't possible.
   * If an undo was not possible the action should not change any state
   * @return true if undo was successful
   */
  public boolean undoAction();

// -=-=-==-=--=-==--=-==--=-==---==-=--=-=-=-=-==-=-=-=-=-=-=--==-=-=--==-=--=-
// -=-=-= Utils -=-=-=-=-=-=-==--==-=--=-=-=-=-==-=-=-=-=-=-=--==-=-=--==-=--=-
// -=-=-==-=--=-==--=-==--=-==---==-=--=-=-=-=-==-=-=-=-=-=-=--==-=-=--==-=--=-

  public static final class Utils {
      /**
       * Create an action that selects uniformly from a number of sub-actions.
       *
       * @param subActions an array of UndoableAction objects from which one will be selected uniformly.
       * @return a new UndoableAction that represents the uniform selection of one of the sub-actions.
       */
    public static final UndoableAction getSimpleUniformSelection(UndoableAction[] subActions) {
      return new Multi(subActions);
    }
      /**
       * Create an action that selects from sub-actions with given proportions.
       *
       * @param subActions        An array of UndoableAction objects to select from.
       * @param actionProportions An array of doubles representing the proportion for each sub-action.
       * @return a new UndoableAction that selects according to the specified proportions.
       * @throws IllegalArgumentException if the action array and proportion arrays are of different lengths.
       */
    public static final UndoableAction getDistributedSelection(UndoableAction[] subActions, double[] actionProportions) {
      if(subActions.length>actionProportions.length) {
        throw new IllegalArgumentException("Actions and proportion array different lengths");
      }
      return new DistributedMulti(subActions, actionProportions);
    }
  /**
   * Create an action that combines multiple actions in sequence.
   *
   * @param subActions The actions that are performed in turn.
   * @return An UndoableAction that performs all the sub-actions in sequence.
   */
    public static final UndoableAction getCombined(UndoableAction[] subActions) {
      return new Combined(subActions);
    }
      /**
       * A simple tool for changing actions when things get desperate.
       *
       * @param primaryAction       The main action to perform when things are going well.
       * @param desparateAction     The action to perform when things get desperate. The desperation value for this action
       *                            will be scaled according to how much over the limit we are.
       * @param desparationLimit    The desperation value at which we start performing the desperate action.
       * @param desparationInterval The interval between desperate actions once the cutoff is crossed.
       *                            A value of 1 means perform the desperate action continuously after crossing the limit.
       * @return An UndoableAction that handles primary and desperate actions according to the desperation logic.
       */
    public static final UndoableAction getSimpleDesparation(UndoableAction primaryAction, UndoableAction desparateAction, double desparationLimit, int desparationInterval) {
      return new SimpleDesparation(primaryAction,desparateAction,desparationLimit,desparationInterval);
    }

    // -=-==-=--==--=-=-=-=-=-=-==--=-=
    private static class SimpleDesparation implements UndoableAction {
      private final UndoableAction primaryAction_;
      private final UndoableAction desparateAction_;
      private final double desparationLimit_;
      private final int desparationInterval_;
      private int currentDesparateCount_ = 0;
      private UndoableAction lastAction_ = null;
      /**
       * A simple tool for change actions when things get desparate
       * @param primaryAction The main action to do when things are going well
       * @param desparateAction The action to do when things get desparate. The desperation value for the desparate action will be scaled according to how much over the limit we are
       * @param desparationLimit The desparate value at which we start doing the desparate action
       * @param desparationInterval The time between desparate actions when we cross the cutoff (a value of one will mean do all the time after desparation value has crossed cutoff)
       */
      public SimpleDesparation(UndoableAction primaryAction, UndoableAction desparateAction, double desparationLimit, int desparationInterval) {
        this.primaryAction_ = primaryAction;
        this.desparateAction_ = desparateAction;
        this.desparationLimit_ = desparationLimit;
        this.desparationInterval_ = desparationInterval;
      }
      /**
       * @return false
       */
      public boolean isActionDeterministic() {
        return false;
      }
      public double doAction(double currentScore, double desparationValue) {
        if(desparationValue>=desparationLimit_) {
          currentDesparateCount_++;
          if(currentDesparateCount_==desparationInterval_) {
            currentDesparateCount_ = 0;
            lastAction_ = desparateAction_;
            desparationValue = (desparationLimit_-desparationValue)/(1-desparationLimit_);
          } else {
            lastAction_ = primaryAction_;
          }
        } else {
          lastAction_ = primaryAction_;
          currentDesparateCount_ = 0;
        }
        return lastAction_.doAction(currentScore,desparationValue);
      }
      public boolean isActionSuccessful() {
        if(lastAction_!=null) {
          return lastAction_.isActionSuccessful();
        }
        throw new RuntimeException("Assertion error : isActionSuccessful() called when no action has been done recently");
      }
      public boolean undoAction() {
        if(lastAction_!=null) {
          final boolean successful = lastAction_.undoAction();
          lastAction_ = null;
          return successful;
        } else {
          throw new RuntimeException("Assertion error : undoAction() called when no action has been done recently (or has already been undone)");
        }
      }
    } //End of class Multi

    // -=-==-=--==--=-=-=-=-=-=-==--=-=
    private static class Multi implements UndoableAction {
      private final UndoableAction[] subActions_;
      private UndoableAction lastAction_ = null;
      private final Random random_;
      public Multi(UndoableAction[] subActions) {
        this.subActions_ = subActions;
        this.random_ = new Random();
      }
      public double doAction(double currentScore, double desparationValue) {
        lastAction_ = subActions_[random_.nextInt(subActions_.length)];
        return lastAction_.doAction(currentScore,desparationValue);
      }
      public boolean isActionSuccessful() {
        if(lastAction_!=null) {
          return lastAction_.isActionSuccessful();
        }
        throw new RuntimeException("Assertion error : isActionSuccessful() called when no action has been done recently");
      }
      /**
       * @return false
       */
      public boolean isActionDeterministic() {
        return false;
      }
      public boolean undoAction() {
        if(lastAction_!=null) {
          final boolean successful = lastAction_.undoAction();
          lastAction_ = null;
          return successful;
        } else {
          throw new RuntimeException("Assertion error : undoAction() called when no action has been done recently (or has already been undone)");
        }
      }
    } //End of class Multi
// -=-==-=--==--=-=-=-=-=-=-==--=-=
    private static class DistributedMulti implements UndoableAction {
      private final UndoableAction[] subActions_;
      private final double[] probabilities_;
      private UndoableAction lastAction_ = null;
      private final Random random_;

      public DistributedMulti(UndoableAction[] subActions, double[] proportions) {
        this.subActions_ = subActions;
        this.probabilities_ = new double[subActions.length];
        double total = 0;
        for(int i = 0 ; i < subActions.length ; i++) {
          total+=proportions[i];
        }
        for(int i = 0 ; i < subActions.length ; i++) {
          probabilities_[i] = proportions[i]/total;
        }
        this.random_ = new Random();
      }
      /**
       * @return false
       */
      public boolean isActionDeterministic() {
        return false;
      }
      public double doAction(double currentScore, double desparationValue) {
        double v = random_.nextDouble();
        double total = 0;
        int index = subActions_.length-1;
        for(int i = 0 ; i < subActions_.length ; i++) {
          total+=probabilities_[i];
          if(total>v) {
            index = i;
            break;
          }
        }
        lastAction_ = subActions_[index];
        return lastAction_.doAction(currentScore,desparationValue);
      }
      public boolean isActionSuccessful() {
        if(lastAction_!=null) {
          return lastAction_.isActionSuccessful();
        }
        throw new RuntimeException("Assertion error : isActionSuccessful() called when no action has been done recently");
      }
      public boolean undoAction() {
        if(lastAction_!=null) {
          boolean successful = lastAction_.undoAction();
          lastAction_ = null;
          return successful;
        } else {
          throw new RuntimeException("Assertion error : undoAction() called when no action has been done recently (or has already been undone)");
        }
      }
    } //End of class DistributedMulti
		// -=-==-=--==--=-=-=-=-=-=-==--=-=
    private static class Combined implements UndoableAction {
      private final UndoableAction[] subActions_;
      private boolean deterministic_ = true;
      private boolean successful_ = false;
      public Combined(UndoableAction[] subActions) {
        this.subActions_ = subActions;
      }
      /**
       * @return false
       */
      public boolean isActionDeterministic() {  return deterministic_;     }
      public double doAction(double currentScore, double desparationValue) {
        boolean d = true;
				boolean s = true;
				for(int i = 0 ; i < subActions_.length ; i++) {
					UndoableAction a = subActions_[i];
					double score = a.doAction(currentScore, desparationValue);
					if(a.isActionSuccessful()) {
						s = true;
						currentScore = score;
				    d = d & a.isActionDeterministic();
					}
				}
				deterministic_ = d;
				successful_ = s;
				return currentScore;
      }
      public boolean isActionSuccessful() { return successful_; }
      public boolean undoAction() {
				boolean result = true;
				if(successful_) {
			    for(int i = subActions_.length -1 ; i >= 0 ; i++) {
					  UndoableAction a = subActions_[i];
					  if(a.isActionSuccessful()) {
					    result = result & a.undoAction();
					  }
				  }
					successful_ = false;
      		return result;
        } else {
          throw new RuntimeException("Assertion error : undoAction() called when not successful");
        }
		  }
    } //End of class Combined
  } //End of class Utils
}