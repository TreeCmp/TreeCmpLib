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


/**
 *
 * The following code is based on Roy Jonker's implementation of LAP Solver,
 * see details below.
 */
/************************************************************************
 *
 *  lap.cpp
 version 1.0 - 4 September 1996
 author: Roy Jonker @ MagicLogic Optimization Inc.
 e-mail: roy_jonker@magiclogic.com

 Code for Linear Assignment Problem, according to

 "A Shortest Augmenting Path Algorithm for Dense and Sparse Linear
 Assignment Problems," Computing 38, 325-340, 1987

 by

 R. Jonker and A. Volgenant, University of Amsterdam.
 *
 *************************************************************************/
package treecmp.common;

import java.util.Arrays;

public class LapSolver {

  static final int BIG = 100000000;
  static final long BIG_LONG = Long.MAX_VALUE;

  /**
   * Prealokowane bufory robocze per-wątek (Zero-Allocation w gorących pętlach NNI/SPR/ECR/TBR).
   */
  private static final class ScratchBuffers {
    int[] free = new int[256];
    int[] collist = new int[256];
    int[] matches = new int[256];
    int[] d = new int[256];
    int[] pred = new int[256];
    long[] dLong = new long[256];

    void ensureCapacity(int dim) {
      if (free.length < dim) {
        int newCap = Math.max(dim, free.length * 2);
        free = new int[newCap];
        collist = new int[newCap];
        matches = new int[newCap];
        d = new int[newCap];
        pred = new int[newCap];
      }
    }

    void ensureLongCapacity(int dim) {
      ensureCapacity(dim);
      if (dLong.length < dim) {
        int newCap = Math.max(dim, dLong.length * 2);
        dLong = new long[newCap];
      }
    }
  }

  private static final ThreadLocal<ScratchBuffers> SCRATCH = ThreadLocal.withInitial(ScratchBuffers::new);

  public static int lap(int dim,
                        int assigncost[][],
                        int rowsol[],
                        int colsol[],
                        int u[],
                        int v[])
  {
    if (dim <= 0) return 0;

    ScratchBuffers sb = SCRATCH.get();
    sb.ensureCapacity(dim);

    int[] free = sb.free;
    int[] collist = sb.collist;
    int[] matches = sb.matches;
    int[] d = sb.d;
    int[] pred = sb.pred;

    boolean unassignedfound;
    int i, imin, numfree = 0, prvnumfree, f, i0, k, freerow;
    int j, j1, j2 = 0, endofpath = 0, last = 0, low, up;
    int min = 0, h, umin, usubmin, v2;

    Arrays.fill(matches, 0, dim, 0);

    // COLUMN REDUCTION
    for (j = dim - 1; j >= 0; j--) {
      min = assigncost[0][j];
      imin = 0;
      for (i = 1; i < dim; i++) {
        if (assigncost[i][j] < min) {
          min = assigncost[i][j];
          imin = i;
        }
      }
      v[j] = min;

      if (++matches[imin] == 1) {
        rowsol[imin] = j;
        colsol[j] = imin;
      } else {
        colsol[j] = -1;
      }
    }

    // REDUCTION TRANSFER
    for (i = 0; i < dim; i++) {
      if (matches[i] == 0) {
        free[numfree++] = i;
      } else if (matches[i] == 1) {
        j1 = rowsol[i];
        min = BIG;
        int[] costRow = assigncost[i];
        for (j = 0; j < dim; j++) {
          if (j != j1) {
            int cand = costRow[j] - v[j];
            if (cand < min) {
              min = cand;
            }
          }
        }
        v[j1] = v[j1] - min;
      }
    }

    // AUGMENTING ROW REDUCTION
    int loopcnt = 0;
    do {
      loopcnt++;
      k = 0;
      prvnumfree = numfree;
      numfree = 0;
      while (k < prvnumfree) {
        i = free[k];
        k++;

        int[] costRow = assigncost[i];
        umin = costRow[0] - v[0];
        j1 = 0;
        usubmin = BIG;
        for (j = 1; j < dim; j++) {
          h = costRow[j] - v[j];
          if (h < usubmin) {
            if (h >= umin) {
              usubmin = h;
              j2 = j;
            } else {
              usubmin = umin;
              umin = h;
              j2 = j1;
              j1 = j;
            }
          }
        }

        i0 = colsol[j1];
        if (umin < usubmin) {
          v[j1] = v[j1] - (usubmin - umin);
        } else if (i0 >= 0) {
          j1 = j2;
          i0 = colsol[j2];
        }

        rowsol[i] = j1;
        colsol[j1] = i;

        if (i0 >= 0) {
          if (umin < usubmin) {
            free[--k] = i0;
          } else {
            free[numfree++] = i0;
          }
        }
      }
    } while (loopcnt < 2);

    // AUGMENT SOLUTION for each free row.
    for (f = 0; f < numfree; f++) {
      freerow = free[f];
      int[] freeCostRow = assigncost[freerow];

      for (j = 0; j < dim; j++) {
        d[j] = freeCostRow[j] - v[j];
        pred[j] = freerow;
        collist[j] = j;
      }

      low = 0;
      up = 0;
      unassignedfound = false;
      do {
        if (up == low) {
          last = low - 1;
          min = d[collist[up++]];
          for (k = up; k < dim; k++) {
            j = collist[k];
            h = d[j];
            if (h <= min) {
              if (h < min) {
                up = low;
                min = h;
              }
              collist[k] = collist[up];
              collist[up++] = j;
            }
          }

          for (k = low; k < up; k++) {
            if (colsol[collist[k]] < 0) {
              endofpath = collist[k];
              unassignedfound = true;
              break;
            }
          }
        }

        if (!unassignedfound) {
          j1 = collist[low];
          low++;
          i = colsol[j1];
          int[] costRow = assigncost[i];
          h = costRow[j1] - v[j1] - min;

          for (k = up; k < dim; k++) {
            j = collist[k];
            v2 = costRow[j] - v[j] - h;
            if (v2 < d[j]) {
              pred[j] = i;
              if (v2 == min) {
                if (colsol[j] < 0) {
                  endofpath = j;
                  unassignedfound = true;
                  break;
                } else {
                  collist[k] = collist[up];
                  collist[up++] = j;
                }
              }
              d[j] = v2;
            }
          }
        }
      } while (!unassignedfound);

      for (k = 0; k <= last; k++) {
        j1 = collist[k];
        v[j1] = v[j1] + d[j1] - min;
      }

      do {
        i = pred[endofpath];
        colsol[endofpath] = i;
        j1 = endofpath;
        endofpath = rowsol[i];
        rowsol[i] = j1;
      } while (i != freerow);
    }

    int lapcost = 0;
    for (i = 0; i < dim; i++) {
      j = rowsol[i];
      int c = assigncost[i][j];
      u[i] = c - v[j];
      lapcost += c;
    }

    return lapcost;
  }

  public static int lapShort(int dim,
                             short assigncost[][],
                             int rowsol[],
                             int colsol[],
                             int u[],
                             int v[])
  {
    if (dim <= 0) return 0;

    ScratchBuffers sb = SCRATCH.get();
    sb.ensureCapacity(dim);

    int[] free = sb.free;
    int[] collist = sb.collist;
    int[] matches = sb.matches;
    int[] d = sb.d;
    int[] pred = sb.pred;

    boolean unassignedfound;
    int i, imin, numfree = 0, prvnumfree, f, i0, k, freerow;
    int j, j1, j2 = 0, endofpath = 0, last = 0, low, up;
    int min = 0, h, umin, usubmin, v2;

    Arrays.fill(matches, 0, dim, 0);

    // COLUMN REDUCTION
    for (j = dim - 1; j >= 0; j--) {
      min = assigncost[0][j];
      imin = 0;
      for (i = 1; i < dim; i++) {
        if (assigncost[i][j] < min) {
          min = assigncost[i][j];
          imin = i;
        }
      }
      v[j] = min;

      if (++matches[imin] == 1) {
        rowsol[imin] = j;
        colsol[j] = imin;
      } else {
        colsol[j] = -1;
      }
    }

    // REDUCTION TRANSFER
    for (i = 0; i < dim; i++) {
      if (matches[i] == 0) {
        free[numfree++] = i;
      } else if (matches[i] == 1) {
        j1 = rowsol[i];
        min = BIG;
        short[] costRow = assigncost[i];
        for (j = 0; j < dim; j++) {
          if (j != j1) {
            int cand = costRow[j] - v[j];
            if (cand < min) {
              min = cand;
            }
          }
        }
        v[j1] = v[j1] - min;
      }
    }

    // AUGMENTING ROW REDUCTION
    int loopcnt = 0;
    do {
      loopcnt++;
      k = 0;
      prvnumfree = numfree;
      numfree = 0;
      while (k < prvnumfree) {
        i = free[k];
        k++;

        short[] costRow = assigncost[i];
        umin = costRow[0] - v[0];
        j1 = 0;
        usubmin = BIG;
        for (j = 1; j < dim; j++) {
          h = costRow[j] - v[j];
          if (h < usubmin) {
            if (h >= umin) {
              usubmin = h;
              j2 = j;
            } else {
              usubmin = umin;
              umin = h;
              j2 = j1;
              j1 = j;
            }
          }
        }

        i0 = colsol[j1];
        if (umin < usubmin) {
          v[j1] = v[j1] - (usubmin - umin);
        } else if (i0 >= 0) {
          j1 = j2;
          i0 = colsol[j2];
        }

        rowsol[i] = j1;
        colsol[j1] = i;

        if (i0 >= 0) {
          if (umin < usubmin) {
            free[--k] = i0;
          } else {
            free[numfree++] = i0;
          }
        }
      }
    } while (loopcnt < 2);

    // AUGMENT SOLUTION for each free row.
    for (f = 0; f < numfree; f++) {
      freerow = free[f];
      short[] freeCostRow = assigncost[freerow];

      for (j = 0; j < dim; j++) {
        d[j] = freeCostRow[j] - v[j];
        pred[j] = freerow;
        collist[j] = j;
      }

      low = 0;
      up = 0;
      unassignedfound = false;
      do {
        if (up == low) {
          last = low - 1;
          min = d[collist[up++]];
          for (k = up; k < dim; k++) {
            j = collist[k];
            h = d[j];
            if (h <= min) {
              if (h < min) {
                up = low;
                min = h;
              }
              collist[k] = collist[up];
              collist[up++] = j;
            }
          }

          for (k = low; k < up; k++) {
            if (colsol[collist[k]] < 0) {
              endofpath = collist[k];
              unassignedfound = true;
              break;
            }
          }
        }

        if (!unassignedfound) {
          j1 = collist[low];
          low++;
          i = colsol[j1];
          short[] costRow = assigncost[i];
          h = costRow[j1] - v[j1] - min;

          for (k = up; k < dim; k++) {
            j = collist[k];
            v2 = costRow[j] - v[j] - h;
            if (v2 < d[j]) {
              pred[j] = i;
              if (v2 == min) {
                if (colsol[j] < 0) {
                  endofpath = j;
                  unassignedfound = true;
                  break;
                } else {
                  collist[k] = collist[up];
                  collist[up++] = j;
                }
              }
              d[j] = v2;
            }
          }
        }
      } while (!unassignedfound);

      for (k = 0; k <= last; k++) {
        j1 = collist[k];
        v[j1] = v[j1] + d[j1] - min;
      }

      do {
        i = pred[endofpath];
        colsol[endofpath] = i;
        j1 = endofpath;
        endofpath = rowsol[i];
        rowsol[i] = j1;
      } while (i != freerow);
    }

    int lapcost = 0;
    for (i = 0; i < dim; i++) {
      j = rowsol[i];
      int c = assigncost[i][j];
      u[i] = c - v[j];
      lapcost += c;
    }

    return lapcost;
  }

  public static long lapLong(int dim,
                             long assigncost[][],
                             int rowsol[],
                             int colsol[],
                             long u[],
                             long v[])
  {
    if (dim <= 0) return 0L;

    ScratchBuffers sb = SCRATCH.get();
    sb.ensureLongCapacity(dim);

    int[] free = sb.free;
    int[] collist = sb.collist;
    int[] matches = sb.matches;
    long[] d = sb.dLong;
    int[] pred = sb.pred;

    boolean unassignedfound;
    int i, imin, numfree = 0, prvnumfree, f, i0, k, freerow;
    int j, j1, j2 = 0, endofpath = 0, last = 0, low, up;
    long min = 0, h, umin, usubmin, v2;

    Arrays.fill(matches, 0, dim, 0);

    // COLUMN REDUCTION
    for (j = dim - 1; j >= 0; j--) {
      min = assigncost[0][j];
      imin = 0;
      for (i = 1; i < dim; i++) {
        if (assigncost[i][j] < min) {
          min = assigncost[i][j];
          imin = i;
        }
      }
      v[j] = min;

      if (++matches[imin] == 1) {
        rowsol[imin] = j;
        colsol[j] = imin;
      } else {
        colsol[j] = -1;
      }
    }

    // REDUCTION TRANSFER
    for (i = 0; i < dim; i++) {
      if (matches[i] == 0) {
        free[numfree++] = i;
      } else if (matches[i] == 1) {
        j1 = rowsol[i];
        min = BIG_LONG;
        long[] costRow = assigncost[i];
        for (j = 0; j < dim; j++) {
          if (j != j1) {
            long cand = costRow[j] - v[j];
            if (cand < min) {
              min = cand;
            }
          }
        }
        v[j1] = v[j1] - min;
      }
    }

    // AUGMENTING ROW REDUCTION
    int loopcnt = 0;
    do {
      loopcnt++;
      k = 0;
      prvnumfree = numfree;
      numfree = 0;
      while (k < prvnumfree) {
        i = free[k];
        k++;

        long[] costRow = assigncost[i];
        umin = costRow[0] - v[0];
        j1 = 0;
        usubmin = BIG_LONG;
        for (j = 1; j < dim; j++) {
          h = costRow[j] - v[j];
          if (h < usubmin) {
            if (h >= umin) {
              usubmin = h;
              j2 = j;
            } else {
              usubmin = umin;
              umin = h;
              j2 = j1;
              j1 = j;
            }
          }
        }

        i0 = colsol[j1];
        if (umin < usubmin) {
          v[j1] = v[j1] - (usubmin - umin);
        } else if (i0 >= 0) {
          j1 = j2;
          i0 = colsol[j2];
        }

        rowsol[i] = j1;
        colsol[j1] = i;

        if (i0 >= 0) {
          if (umin < usubmin) {
            free[--k] = i0;
          } else {
            free[numfree++] = i0;
          }
        }
      }
    } while (loopcnt < 2);

    // AUGMENT SOLUTION for each free row.
    for (f = 0; f < numfree; f++) {
      freerow = free[f];
      long[] freeCostRow = assigncost[freerow];

      for (j = 0; j < dim; j++) {
        d[j] = freeCostRow[j] - v[j];
        pred[j] = freerow;
        collist[j] = j;
      }

      low = 0;
      up = 0;
      unassignedfound = false;
      do {
        if (up == low) {
          last = low - 1;
          min = d[collist[up++]];
          for (k = up; k < dim; k++) {
            j = collist[k];
            h = d[j];
            if (h <= min) {
              if (h < min) {
                up = low;
                min = h;
              }
              collist[k] = collist[up];
              collist[up++] = j;
            }
          }

          for (k = low; k < up; k++) {
            if (colsol[collist[k]] < 0) {
              endofpath = collist[k];
              unassignedfound = true;
              break;
            }
          }
        }

        if (!unassignedfound) {
          j1 = collist[low];
          low++;
          i = colsol[j1];
          long[] costRow = assigncost[i];
          h = costRow[j1] - v[j1] - min;

          for (k = up; k < dim; k++) {
            j = collist[k];
            v2 = costRow[j] - v[j] - h;
            if (v2 < d[j]) {
              pred[j] = i;
              if (v2 == min) {
                if (colsol[j] < 0) {
                  endofpath = j;
                  unassignedfound = true;
                  break;
                } else {
                  collist[k] = collist[up];
                  collist[up++] = j;
                }
              }
              d[j] = v2;
            }
          }
        }
      } while (!unassignedfound);

      for (k = 0; k <= last; k++) {
        j1 = collist[k];
        v[j1] = v[j1] + d[j1] - min;
      }

      do {
        i = pred[endofpath];
        colsol[endofpath] = i;
        j1 = endofpath;
        endofpath = rowsol[i];
        rowsol[i] = j1;
      } while (i != freerow);
    }

    long lapcost = 0;
    for (i = 0; i < dim; i++) {
      j = rowsol[i];
      long c = assigncost[i][j];
      u[i] = c - v[j];
      lapcost += c;
    }

    return lapcost;
  }

  /**
   * Dynamic update for the Linear Assignment Problem (Warm-Start) utilizing int arrays.
   * Zero-Allocation implementation using ThreadLocal scratch buffers.
   */
  public static int lapUpdate(int dim,
                              int assigncost[][],
                              int rowsol[],
                              int colsol[],
                              int u[],
                              int v[],
                              int[] changedRows)
  {
    if (dim <= 0) return 0;

    ScratchBuffers sb = SCRATCH.get();
    sb.ensureCapacity(dim);

    int[] free = sb.free;
    int[] collist = sb.collist;
    int[] d = sb.d;
    int[] pred = sb.pred;
    int numfree = 0;

    // 1. UNASSIGN THE CHANGED ROWS & 2. RESTORE DUAL FEASIBILITY
    for (int idx = 0; idx < changedRows.length; idx++) {
      int i = changedRows[idx];
      int j = rowsol[i];
      if (j >= 0) {
        colsol[j] = -1;
      }
      free[numfree++] = i;

      int minVal = BIG;
      int[] costRow = assigncost[i];
      for (int col = 0; col < dim; col++) {
        int val = costRow[col] - v[col];
        if (val < minVal) {
          minVal = val;
        }
      }
      u[i] = minVal;
    }

    int f, freerow, j, j1, k, low, up, last = 0, endofpath = 0;
    int min = 0, h, v2;
    boolean unassignedfound;

    // 3. FIND AUGMENTING PATHS
    for (f = 0; f < numfree; f++) {
      freerow = free[f];
      int[] freeCostRow = assigncost[freerow];

      for (j = 0; j < dim; j++) {
        d[j] = freeCostRow[j] - v[j];
        pred[j] = freerow;
        collist[j] = j;
      }

      low = 0;
      up = 0;
      unassignedfound = false;

      do {
        if (up == low) {
          last = low - 1;
          min = d[collist[up++]];

          for (k = up; k < dim; k++) {
            j = collist[k];
            h = d[j];
            if (h <= min) {
              if (h < min) {
                up = low;
                min = h;
              }
              collist[k] = collist[up];
              collist[up++] = j;
            }
          }

          for (k = low; k < up; k++) {
            if (colsol[collist[k]] < 0) {
              endofpath = collist[k];
              unassignedfound = true;
              break;
            }
          }
        }

        if (!unassignedfound) {
          j1 = collist[low];
          low++;
          int i_mapped = colsol[j1];
          int[] mappedCostRow = assigncost[i_mapped];
          h = mappedCostRow[j1] - v[j1] - min;

          for (k = up; k < dim; k++) {
            j = collist[k];
            v2 = mappedCostRow[j] - v[j] - h;
            if (v2 < d[j]) {
              pred[j] = i_mapped;
              if (v2 == min) {
                if (colsol[j] < 0) {
                  endofpath = j;
                  unassignedfound = true;
                  break;
                } else {
                  collist[k] = collist[up];
                  collist[up++] = j;
                }
              }
              d[j] = v2;
            }
          }
        }
      } while (!unassignedfound);

      for (k = 0; k <= last; k++) {
        j1 = collist[k];
        v[j1] = v[j1] + d[j1] - min;
      }

      int i_update;
      do {
        i_update = pred[endofpath];
        colsol[endofpath] = i_update;
        j1 = endofpath;
        endofpath = rowsol[i_update];
        rowsol[i_update] = j1;
      } while (i_update != freerow);
    }

    // 4. CALCULATE NEW OPTIMAL COST
    int lapcost = 0;
    for (int i = 0; i < dim; i++) {
      j = rowsol[i];
      int c = assigncost[i][j];
      u[i] = c - v[j];
      lapcost += c;
    }

    return lapcost;
  }

  /**
   * Dynamic update for the Linear Assignment Problem (Warm-Start) on short matrix.
   * Zero-Allocation implementation using ThreadLocal scratch buffers.
   */
  public static int lapShortUpdate(int dim,
                                   short assigncost[][],
                                   int rowsol[],
                                   int colsol[],
                                   int u[],
                                   int v[],
                                   int[] changedRows)
  {
    if (dim <= 0) return 0;

    ScratchBuffers sb = SCRATCH.get();
    sb.ensureCapacity(dim);

    int[] free = sb.free;
    int[] collist = sb.collist;
    int[] d = sb.d;
    int[] pred = sb.pred;
    int numfree = 0;

    // 1. UNASSIGN THE CHANGED ROWS & 2. RESTORE DUAL FEASIBILITY
    for (int idx = 0; idx < changedRows.length; idx++) {
      int i = changedRows[idx];
      int j = rowsol[i];
      if (j >= 0) {
        colsol[j] = -1;
      }
      free[numfree++] = i;

      int minVal = Integer.MAX_VALUE;
      short[] costRow = assigncost[i];
      for (int col = 0; col < dim; col++) {
        int val = costRow[col] - v[col];
        if (val < minVal) {
          minVal = val;
        }
      }
      u[i] = minVal;
    }

    int f, freerow, j, j1, k, low, up, last = 0, endofpath = 0;
    int min = 0, h, v2;
    boolean unassignedfound;

    // 3. FIND AUGMENTING PATHS ONLY FOR THE FREED ROWS
    for (f = 0; f < numfree; f++) {
      freerow = free[f];
      short[] freeCostRow = assigncost[freerow];

      for (j = 0; j < dim; j++) {
        d[j] = freeCostRow[j] - v[j];
        pred[j] = freerow;
        collist[j] = j;
      }

      low = 0;
      up = 0;
      unassignedfound = false;

      do {
        if (up == low) {
          last = low - 1;
          min = d[collist[up++]];

          for (k = up; k < dim; k++) {
            j = collist[k];
            h = d[j];
            if (h <= min) {
              if (h < min) {
                up = low;
                min = h;
              }
              collist[k] = collist[up];
              collist[up++] = j;
            }
          }

          for (k = low; k < up; k++) {
            if (colsol[collist[k]] < 0) {
              endofpath = collist[k];
              unassignedfound = true;
              break;
            }
          }
        }

        if (!unassignedfound) {
          j1 = collist[low];
          low++;
          int i_mapped = colsol[j1];
          short[] mappedCostRow = assigncost[i_mapped];
          h = mappedCostRow[j1] - v[j1] - min;

          for (k = up; k < dim; k++) {
            j = collist[k];
            v2 = mappedCostRow[j] - v[j] - h;
            if (v2 < d[j]) {
              pred[j] = i_mapped;
              if (v2 == min) {
                if (colsol[j] < 0) {
                  endofpath = j;
                  unassignedfound = true;
                  break;
                } else {
                  collist[k] = collist[up];
                  collist[up++] = j;
                }
              }
              d[j] = v2;
            }
          }
        }
      } while (!unassignedfound);

      // Update column prices
      for (k = 0; k <= last; k++) {
        j1 = collist[k];
        v[j1] = v[j1] + d[j1] - min;
      }

      // Reset row and column assignments along the alternating path
      int i_update;
      do {
        i_update = pred[endofpath];
        colsol[endofpath] = i_update;
        j1 = endofpath;
        endofpath = rowsol[i_update];
        rowsol[i_update] = j1;
      } while (i_update != freerow);
    }

    // 4. CALCULATE THE NEW OPTIMAL COST
    int lapcost = 0;
    for (int i = 0; i < dim; i++) {
      j = rowsol[i];
      int c = assigncost[i][j];
      u[i] = c - v[j];
      lapcost += c;
    }

    return lapcost;
  }

  /**
   * Zmodyfikowana wersja lapUpdate z wbudowanym mechanizmem Early Stopping (Zero-Allocation).
   */
  public static int lapUpdateBoundedInt(int dim,
                                        int assigncost[][],
                                        int rowsol[],
                                        int colsol[],
                                        int u[],
                                        int v[],
                                        int[] changedRows,
                                        int maxCostBound)
  {
    if (dim <= 0) return 0;

    ScratchBuffers sb = SCRATCH.get();
    sb.ensureCapacity(dim);

    int[] free = sb.free;
    int[] collist = sb.collist;
    int[] d = sb.d;
    int[] pred = sb.pred;
    int numfree = 0;

    // 1. Odepnij zmienione rzędy ze skojarzenia & 2. Przywróć dopuszczalność zmiennych dualnych
    for (int idx = 0; idx < changedRows.length; idx++) {
      int i = changedRows[idx];
      int j = rowsol[i];
      if (j >= 0) {
        colsol[j] = -1;
      }
      free[numfree++] = i;

      int minVal = BIG;
      int[] costRow = assigncost[i];
      for (int col = 0; col < dim; col++) {
        int val = costRow[col] - v[col];
        if (val < minVal) {
          minVal = val;
        }
      }
      u[i] = minVal;
    }

    int f, freerow, j, j1, k, low, up, last = 0, endofpath = 0;
    int min = 0, h, v2;
    boolean unassignedfound;

    // 3. Szukaj ścieżek powiększających (Algorytm Dijkstry / Jonkera-Volgenanta)
    for (f = 0; f < numfree; f++) {
      freerow = free[f];
      int[] freeCostRow = assigncost[freerow];

      for (j = 0; j < dim; j++) {
        d[j] = freeCostRow[j] - v[j];
        pred[j] = freerow;
        collist[j] = j;
      }

      low = 0;
      up = 0;
      unassignedfound = false;

      do {
        if (up == low) {
          last = low - 1;
          min = d[collist[up++]];

          if (min > maxCostBound) {
            return BIG;
          }

          for (k = up; k < dim; k++) {
            j = collist[k];
            h = d[j];
            if (h <= min) {
              if (h < min) {
                up = low;
                min = h;
              }
              collist[k] = collist[up];
              collist[up++] = j;
            }
          }

          for (k = low; k < up; k++) {
            if (colsol[collist[k]] < 0) {
              endofpath = collist[k];
              unassignedfound = true;
              break;
            }
          }
        }

        if (!unassignedfound) {
          j1 = collist[low];
          low++;
          int i_mapped = colsol[j1];
          int[] mappedCostRow = assigncost[i_mapped];
          h = mappedCostRow[j1] - v[j1] - min;

          for (k = up; k < dim; k++) {
            j = collist[k];
            v2 = mappedCostRow[j] - v[j] - h;
            if (v2 < d[j]) {
              pred[j] = i_mapped;
              if (v2 == min) {
                if (colsol[j] < 0) {
                  endofpath = j;
                  unassignedfound = true;
                  break;
                } else {
                  collist[k] = collist[up];
                  collist[up++] = j;
                }
              }
              d[j] = v2;
            }
          }
        }
      } while (!unassignedfound);

      for (k = 0; k <= last; k++) {
        j1 = collist[k];
        v[j1] = v[j1] + d[j1] - min;
      }

      int i_update;
      do {
        i_update = pred[endofpath];
        colsol[endofpath] = i_update;
        j1 = endofpath;
        endofpath = rowsol[i_update];
        rowsol[i_update] = j1;
      } while (i_update != freerow);
    }

    // 4. Oblicz nowy optymalny koszt
    int lapcost = 0;
    for (int i = 0; i < dim; i++) {
      j = rowsol[i];
      int c = assigncost[i][j];
      u[i] = c - v[j];
      lapcost += c;
    }

    return lapcost;
  }
}