#!/usr/bin/env python3
"""
Universal Trajectory Certificate Verifier (NNI, 2-sECR, 3-sECR, rSPR, uSPR, rTBR, uTBR)
and Macro-Benchmark Statistics Aggregator.

Verifies strictly that:
1. Every consecutive topology transformation (Step i -> Step i+1) is a valid single move
   of the heuristic declared in the certificate (1-NNI, 2-sECR, 3-sECR, 1-rSPR, 1-uSPR, 1-TBR).
2. The final tree (Step K) is topologically identical (RF == 0) to the TARGET TREE.
Generates detailed error reports in txt files for every failed trajectory.
"""

import sys
import re
from pathlib import Path
from dataclasses import dataclass
from typing import List, Optional, Tuple, Set
from collections import defaultdict

import dendropy
from dendropy.calculate import treecompare


@dataclass
class TrajectoryStep:
    step_num: str
    step_val: float
    name: str
    reported_dist: float
    newick: str
    tree_rooted: Optional[dendropy.Tree] = None
    tree_unrooted: Optional[dendropy.Tree] = None
    is_rooted: bool = False
    parse_error: Optional[str] = None


@dataclass
class StepError:
    step_from: str
    step_to: str
    operator: str
    dist_from: float
    dist_to: float
    rf_unrooted: Optional[int]
    rf_rooted: Optional[int]
    status: str
    newick_from: str
    newick_to: str
    parse_err_from: Optional[str] = None
    parse_err_to: Optional[str] = None


@dataclass
class TrajectoryResult:
    filename: str
    move_type: str
    is_valid_chain: bool
    is_full_convergence: bool
    final_distance: float
    steps_count: int
    metric_name: str
    variant_name: str
    errors: List[StepError]


def parse_newick_pair(newick_raw: str, tns: dendropy.TaxonNamespace, force_rooted: Optional[bool] = None):
    try:
        tree_rooted = dendropy.Tree.get(
            data=newick_raw,
            schema="newick",
            taxon_namespace=tns,
            preserve_underscores=True,
            suppress_internal_node_taxa=True
        )
        is_rooted = force_rooted if force_rooted is not None else bool(
            tree_rooted.seed_node and len(tree_rooted.seed_node.child_nodes()) == 2
        )
        tree_rooted.is_rooted = is_rooted
        tree_rooted.encode_bipartitions()

        tree_unrooted = dendropy.Tree.get(
            data=newick_raw,
            schema="newick",
            taxon_namespace=tns,
            preserve_underscores=True,
            suppress_internal_node_taxa=True
        )
        tree_unrooted.deroot()
        tree_unrooted.is_rooted = False
        tree_unrooted.encode_bipartitions()

        return tree_rooted, tree_unrooted, is_rooted, None
    except Exception as e:
        return None, None, False, str(e)


# ==============================================================================
# FAST SET-BASED MATHEMATICAL VERIFIERS FOR 1-rSPR, 1-uSPR, AND 1-TBR
# ==============================================================================

def extract_tree_clades(tree: dendropy.Tree) -> Tuple[frozenset, List[frozenset]]:
    """Extracts all node leaf-sets (clades) in O(N) without re-parsing Newick."""
    node_clades = {}
    clades_list = []
    for node in tree.postorder_node_iter():
        if node.is_leaf():
            c = frozenset([node.taxon.label]) if node.taxon else frozenset()
        else:
            c = frozenset().union(*(node_clades[ch] for ch in node.child_nodes()))
        node_clades[node] = c
        if node != tree.seed_node and c:
            clades_list.append(c)
    all_leaves = node_clades.get(tree.seed_node, frozenset())
    return all_leaves, clades_list


def induced_rooted_clades(clades: List[frozenset], keep_subset: frozenset) -> Set[frozenset]:
    """Computes rooted topology on `keep_subset` purely via set intersections."""
    res = set()
    for c in clades:
        inter = c & keep_subset
        if len(inter) > 1:
            res.add(inter)
    return res


def induced_unrooted_splits(clades: List[frozenset], keep_subset: frozenset) -> Set[frozenset]:
    """Computes canonical unrooted splits on `keep_subset` purely via set intersections."""
    if len(keep_subset) <= 3:
        return set()
    anchor = min(keep_subset)
    splits = set()
    for c in clades:
        side1 = c & keep_subset
        if len(side1) >= 2 and len(keep_subset) - len(side1) >= 2:
            canon = side1 if anchor in side1 else (keep_subset - side1)
            splits.add(canon)
    return splits


def cut_rooted_clades_on_subset(clades: List[frozenset], all_leaves: frozenset, pruned_B: frozenset) -> Optional[Set[frozenset]]:
    """
    Checks if `pruned_B` is a valid single-edge cut in the unrooted tree,
    and returns the rooted clades of `pruned_B` rooted at the cut edge.
    """
    if len(pruned_B) <= 1:
        return {pruned_B} if pruned_B else set()

    comp_A = all_leaves - pruned_B
    is_below = None
    for c in clades:
        if c == pruned_B:
            is_below = True
            break
        elif c == comp_A:
            is_below = False
            break

    if is_below is None:
        return None  # Not a valid single-edge cut

    res = {pruned_B}
    if is_below:
        for c in clades:
            if len(c) > 1 and c.issubset(pruned_B):
                res.add(c)
    else:
        for c in clades:
            if c.isdisjoint(comp_A):
                if len(c) > 1:
                    res.add(c)
            elif comp_A.issubset(c):
                rem = pruned_B - c
                if len(rem) > 1:
                    res.add(rem)
    return res


def is_valid_1_uspr(t1_u: dendropy.Tree, t2_u: dendropy.Tree, rf_u: int) -> bool:
    if rf_u == 2:
        return True
    all_leaves, clades1 = extract_tree_clades(t1_u)
    _, clades2 = extract_tree_clades(t2_u)

    candidates_B = set()
    for c in clades1:
        comp = all_leaves - c
        if c and comp:
            candidates_B.add(c)
            candidates_B.add(comp)

    for B in candidates_B:
        clades_B_t2 = cut_rooted_clades_on_subset(clades2, all_leaves, B)
        if clades_B_t2 is None:
            continue
        clades_B_t1 = cut_rooted_clades_on_subset(clades1, all_leaves, B)
        if clades_B_t1 != clades_B_t2:
            continue
        A = all_leaves - B
        if induced_unrooted_splits(clades1, A) == induced_unrooted_splits(clades2, A):
            return True
    return False


def is_valid_1_rspr(t1_r: dendropy.Tree, t2_r: dendropy.Tree,
                    t1_u: dendropy.Tree, t2_u: dendropy.Tree,
                    rf_r: Optional[int], rf_u: int) -> bool:
    if rf_r == 2 or rf_u == 2:
        return True
    all_leaves, clades1 = extract_tree_clades(t1_r)
    _, clades2 = extract_tree_clades(t2_r)

    for B in clades1:
        A = all_leaves - B
        if not A or not B:
            continue
        if induced_rooted_clades(clades1, B) == induced_rooted_clades(clades2, B):
            if induced_rooted_clades(clades1, A) == induced_rooted_clades(clades2, A) or \
               induced_unrooted_splits(clades1, A) == induced_unrooted_splits(clades2, A):
                return True
    return False

def is_valid_1_tbr(t1_u: dendropy.Tree, t2_u: dendropy.Tree, rf_u: int) -> bool:
    if rf_u == 2:
        return True
    all_leaves, clades1 = extract_tree_clades(t1_u)
    _, clades2 = extract_tree_clades(t2_u)

    parts2 = set()
    for c in clades2:
        parts2.add(c)
        parts2.add(all_leaves - c)

    seen = set()
    for c in clades1:
        B = c if len(c) <= len(all_leaves) // 2 else (all_leaves - c)
        if B in seen or not B:
            continue
        seen.add(B)
        if B not in parts2:
            continue
        A = all_leaves - B
        if induced_unrooted_splits(clades1, A) == induced_unrooted_splits(clades2, A) and \
           induced_unrooted_splits(clades1, B) == induced_unrooted_splits(clades2, B):
            return True
    return False


# ==============================================================================
# PARSING BOTH LEGACY VND LOGS AND NEW STANDALONE PROOF CERTIFICATES
# ==============================================================================

def parse_certificate_file(file_path: str, tns: dendropy.TaxonNamespace):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    tree_type_match = re.search(r'Tree Type\s*:\s*(ROOTED|UNROOTED)', content)
    metric_match = re.search(r'Topological Metric\s*:\s*(\S+)', content)
    variant_match = re.search(r'Heuristic Variant\s*:\s*(.+)', content)
    total_steps_match = re.search(r'Total\s+.*?\s*Steps\s*:\s*(\d+)', content)
    final_dist_match = re.search(r'Final Distance\s*:\s*([0-9,.]+)', content)
    target_tree_match = re.search(r'TARGET TREE:\s*\n\s*(\(.*?\);)', content, re.DOTALL)

    force_rooted = None
    if tree_type_match:
        force_rooted = (tree_type_match.group(1).strip().upper() == "ROOTED")

    move_match = re.search(r'Step Operation\s*:\s*([^\r\n]+)', content)
    if move_match:
        raw_op = move_match.group(1).strip()
        move_type = re.sub(r'\s*\(each.*$', '', raw_op).strip()
    else:
        move_type = "1-NNI"

    header_metric = metric_match.group(1).strip() if metric_match else None
    header_variant = variant_match.group(1).strip() if variant_match else None
    target_newick = None
    if target_tree_match:
        target_newick = target_tree_match.group(1).replace('\n', '').replace('\r', '').strip()

    # Supports multi-line Newick strings (wrapped by PAL's TreeUtils.printNH for N >= 20)
    new_pattern = re.compile(
        r'\[Step\s+(\d+)\]\s+\(([^=]+)=\s*([0-9,.]+)\)\s*(\(.*?\);)',
        re.DOTALL
    )
    new_matches = list(new_pattern.finditer(content))

    if new_matches:
        steps = []
        for match in new_matches:
            step_raw = match.group(1).strip()
            step_val = float(step_raw)
            dist = float(match.group(3).replace(',', '.'))
            newick_raw = match.group(4).replace('\n', '').replace('\r', '').strip()

            tr_r, tr_u, is_r, p_err = parse_newick_pair(newick_raw, tns, force_rooted)
            steps.append(TrajectoryStep(
                step_num=step_raw,
                step_val=step_val,
                name=move_type,
                reported_dist=dist,
                newick=newick_raw,
                tree_rooted=tr_r,
                tree_unrooted=tr_u,
                is_rooted=is_r,
                parse_error=p_err
            ))

        rep_steps = int(total_steps_match.group(1)) if total_steps_match else (len(steps) - 1)
        rep_fdist = float(final_dist_match.group(1).replace(',', '.')) if final_dist_match else steps[-1].reported_dist
        return [steps], rep_steps, rep_fdist, move_type, header_metric, header_variant, target_newick, force_rooted

    # Legacy VND format
    legacy_pattern = re.compile(
        r'(?:KROK|STEP)\s+([0-9]+(?:\.[0-9]+)?)\s+\[(.*?)\]\s+-\s+(?:Dystans|Distance):\s+([0-9,.]+)\s*\n\s*(\(.*?\);)',
        re.DOTALL
    )
    final_match = re.search(r'\[FINAL\]\s+Total Steps:\s*(\d+)\s*\|\s*Final Distance:\s*([0-9,.]+)', content)
    reported_total_steps = int(final_match.group(1)) if final_match else None
    reported_final_dist = float(final_match.group(2).replace(',', '.')) if final_match else None

    runs = []
    current_run = []
    for match in legacy_pattern.finditer(content):
        step_raw = match.group(1).strip()
        step_val = float(step_raw)
        name = match.group(2).strip()
        dist = float(match.group(3).replace(',', '.'))
        newick_raw = match.group(4).replace('\n', '').replace('\r', '').strip()

        tr_r, tr_u, is_r, p_err = parse_newick_pair(newick_raw, tns)
        step = TrajectoryStep(
            step_num=step_raw, step_val=step_val, name=name,
            reported_dist=dist, newick=newick_raw,
            tree_rooted=tr_r, tree_unrooted=tr_u, is_rooted=is_r, parse_error=p_err
        )

        if step_val == 0.0 or (current_run and step_val <= current_run[-1].step_val):
            if current_run:
                runs.append(current_run)
            current_run = [step]
        else:
            current_run.append(step)

    if current_run:
        runs.append(current_run)

    return runs, reported_total_steps, reported_final_dist, "1-NNI", None, None, None, None


def verify_single_step(curr: TrajectoryStep, nxt: TrajectoryStep, move_type: str,
                       rf_u: int, rf_r: Optional[int], is_both_rooted: bool) -> Tuple[bool, str]:
    if rf_u == 0 and (not is_both_rooted or rf_r == 0):
        return False, f"ERROR (Identical trees: RF=0, Expected valid {move_type} move)"

    if move_type == "1-NNI":
        if rf_u == 2 or (is_both_rooted and rf_r == 2):
            return True, "OK"
        min_rf = min([rf_u] + ([rf_r] if is_both_rooted and rf_r is not None else []))
        return False, f"ERROR (Jump RF={min_rf} != 2 for 1-NNI)"

    if move_type in ("1-(2-sECR)", "2-sECR"):
        valid_rfs = {2, 4}
        if rf_u in valid_rfs or (is_both_rooted and rf_r in valid_rfs):
            return True, "OK"
        rf_disp = f"RF_u={rf_u}, RF_r={rf_r}" if is_both_rooted else f"RF={rf_u}"
        return False, f"ERROR (Jump {rf_disp} not in {{2, 4}} for 2-sECR)"

    if move_type in ("1-(3-sECR)", "3-sECR"):
        valid_rfs = {2, 4, 6}
        if rf_u in valid_rfs or (is_both_rooted and rf_r in valid_rfs):
            return True, "OK"
        rf_disp = f"RF_u={rf_u}, RF_r={rf_r}" if is_both_rooted else f"RF={rf_u}"
        return False, f"ERROR (Jump {rf_disp} not in {{2, 4, 6}} for 3-sECR)"

    if move_type == "1-uSPR":
        if is_valid_1_uspr(curr.tree_unrooted, nxt.tree_unrooted, rf_u):
            return True, "OK"
        return False, f"ERROR (Transition is NOT a valid 1-uSPR move; RF_unrooted={rf_u})"

    if move_type == "1-rSPR":
        if is_valid_1_rspr(curr.tree_rooted, nxt.tree_rooted, curr.tree_unrooted, nxt.tree_unrooted, rf_r, rf_u):
            return True, "OK"
        return False, f"ERROR (Transition is NOT a valid 1-rSPR move; RF_rooted={rf_r})"

    if move_type in ("1-uTBR", "1-rTBR", "1-TBR"):
        if is_valid_1_tbr(curr.tree_unrooted, nxt.tree_unrooted, rf_u):
            return True, "OK"
        return False, f"ERROR (Transition is NOT a valid 1-TBR move; RF_unrooted={rf_u})"

    return (rf_u == 2), "OK" if rf_u == 2 else f"ERROR (Unknown move {move_type}, RF={rf_u})"

def verify_run(steps: List[TrajectoryStep], move_type: str, target_newick: Optional[str],
               force_rooted: Optional[bool], tns: dendropy.TaxonNamespace,
               allow_collapse_noops: bool = False) -> Tuple[bool, bool, float, int, List[StepError]]:
    if len(steps) < 2:
        if len(steps) == 1:
            dist = steps[0].reported_dist
            return True, dist == 0.0, dist, 0, []
        return False, False, -1.0, 0, []

    all_steps_ok = True
    valid_step_count = 0
    errors: List[StepError] = []

    effective_steps = [steps[0]]
    for s in steps[1:]:
        if allow_collapse_noops and effective_steps[-1].tree_unrooted is not None and s.tree_unrooted is not None:
            rf_u = treecompare.symmetric_difference(effective_steps[-1].tree_unrooted, s.tree_unrooted)
            is_both_r = effective_steps[-1].is_rooted and s.is_rooted
            rf_r = treecompare.symmetric_difference(effective_steps[-1].tree_rooted, s.tree_rooted) if is_both_r else None
            if rf_u == 0 and (not is_both_r or rf_r == 0):
                continue
        effective_steps.append(s)

    for i in range(len(effective_steps) - 1):
        curr = effective_steps[i]
        nxt = effective_steps[i + 1]

        if curr.tree_unrooted is None or nxt.tree_unrooted is None or curr.tree_rooted is None or nxt.tree_rooted is None:
            all_steps_ok = False
            errors.append(StepError(
                step_from=curr.step_num, step_to=nxt.step_num, operator=nxt.name,
                dist_from=curr.reported_dist, dist_to=nxt.reported_dist,
                rf_unrooted=None, rf_rooted=None, status="ERROR (Corrupted Newick / Parse Failure)",
                newick_from=curr.newick, newick_to=nxt.newick,
                parse_err_from=curr.parse_error, parse_err_to=nxt.parse_error
            ))
            continue

        rf_u = treecompare.symmetric_difference(curr.tree_unrooted, nxt.tree_unrooted)
        is_both_rooted = curr.is_rooted and nxt.is_rooted
        rf_r = treecompare.symmetric_difference(curr.tree_rooted, nxt.tree_rooted) if is_both_rooted else None

        ok, status = verify_single_step(curr, nxt, move_type, rf_u, rf_r, is_both_rooted)
        if ok:
            valid_step_count += 1
        else:
            all_steps_ok = False
            errors.append(StepError(
                step_from=curr.step_num, step_to=nxt.step_num, operator=nxt.name,
                dist_from=curr.reported_dist, dist_to=nxt.reported_dist,
                rf_unrooted=rf_u, rf_rooted=rf_r, status=status,
                newick_from=curr.newick, newick_to=nxt.newick
            ))

    last_step = effective_steps[-1]
    final_dist = last_step.reported_dist
    is_converged = abs(final_dist) < 1e-6

    if target_newick and last_step.tree_unrooted is not None:
        t_r, t_u, is_r, p_err = parse_newick_pair(target_newick, tns, force_rooted)
        if t_u is not None:
            rf_target_u = treecompare.symmetric_difference(last_step.tree_unrooted, t_u)
            rf_target_r = treecompare.symmetric_difference(last_step.tree_rooted, t_r) if (force_rooted and t_r) else None
            target_ok = (rf_target_r == 0) if force_rooted else (rf_target_u == 0)
            if not target_ok:
                all_steps_ok = False
                is_converged = False
                errors.append(StepError(
                    step_from=last_step.step_num, step_to="TARGET", operator="TargetCheck",
                    dist_from=last_step.reported_dist, dist_to=0.0,
                    rf_unrooted=rf_target_u, rf_rooted=rf_target_r,
                    status=f"ERROR (Final tree does NOT match TARGET TREE: RF_u={rf_target_u}, RF_r={rf_target_r})",
                    newick_from=last_step.newick, newick_to=target_newick
                ))

    return all_steps_ok, is_converged, final_dist, valid_step_count, errors


def extract_metadata_from_name(filename: str) -> Tuple[str, str]:
    m_standalone = re.match(r'proof_(?:SPR|TBR|NNI|ECR2|ECR3)_([^_]+)_(.+)_N(\d+)_pair(\d+)_\d{8}_\d{6}_[0-9A-Fa-f]+\.txt', filename)
    if m_standalone:
        return m_standalone.group(1), m_standalone.group(2).strip("_")

    m_new = re.match(r'proof_([^_]+)_(.+)_N(\d+)_pair(\d+)_\d{8}_\d{6}(?:_\d{3})?_[0-9A-Fa-f]+\.txt', filename)
    if m_new:
        return m_new.group(1), m_new.group(2)

    m_old = re.search(r'proof_(?:pair_)?(.*?)(?:_\d{8}_\d{6}|\.txt)', filename)
    tag = m_old.group(1) if m_old else filename
    metric = "UNKNOWN"
    for cand in ["RFCluster", "RFC", "MC_RF", "MP_RF", "MS_RF", "M3_RF", "MC", "MP", "RF", "MS", "M3"]:
        if tag.endswith(cand) or f"_{cand}_" in tag or tag.endswith(f"_{cand}"):
            metric = cand
            break

    variant = tag.replace(f"_{metric}", "")
    return metric, variant


def write_error_report(output_dir: Path, source_file: Path, move_type: str, metric: str, variant: str,
                       run_idx: int, total_runs: int, rep_steps: Optional[int],
                       rep_fdist: Optional[float], errors: List[StepError]):
    output_dir.mkdir(parents=True, exist_ok=True)
    report_file = output_dir / f"error_{source_file.stem}.txt"

    with open(report_file, "w", encoding="utf-8") as out:
        out.write("=" * 100 + "\n")
        out.write(f"TRAJECTORY {move_type} STEP VERIFICATION ERROR REPORT\n")
        out.write("=" * 100 + "\n")
        out.write(f"Source File      : {source_file.name}\n")
        out.write(f"Move Operation   : {move_type}\n")
        out.write(f"Metric / Variant : {metric} | {variant}\n")
        out.write(f"Run Index        : {run_idx} / {total_runs}\n")
        out.write(f"Total Steps      : {rep_steps if rep_steps is not None else 'N/A'}\n")
        out.write(f"Final Distance   : {rep_fdist if rep_fdist is not None else 'N/A'}\n")
        out.write(f"Error Count      : {len(errors)}\n")
        out.write("=" * 100 + "\n\n")

        for idx, err in enumerate(errors, 1):
            out.write(f"--- [ERROR #{idx}] -----------------------------------------------------------------\n")
            out.write(f"Transition : Step {err.step_from} -> Step {err.step_to}\n")
            out.write(f"Operator   : {err.operator}\n")
            out.write(f"Distance   : {err.dist_from:.4f} -> {err.dist_to:.4f}\n")
            out.write(f"Status     : {err.status}\n")
            out.write(f"Measured RF: Unrooted RF = {err.rf_unrooted}, Rooted RF = {err.rf_rooted}\n")

            if err.parse_err_from or err.parse_err_to:
                out.write(f"Parse Errors: From='{err.parse_err_from}', To='{err.parse_err_to}'\n")

            out.write("\nTree BEFORE step:\n")
            out.write(f"{err.newick_from}\n")
            out.write("\nTree AFTER step:\n")
            out.write(f"{err.newick_to}\n")
            out.write("-" * 100 + "\n\n")

    return report_file


def print_progress_bar(iteration: int, total: int, errors_count: int, current_file: str, bar_len: int = 20):
    percent = (iteration / total) * 100
    filled_len = int(bar_len * iteration // total)
    bar = '=' * filled_len + ('-' * (bar_len - filled_len))
    sys.stdout.write(f"\r[{iteration}/{total}] [{bar}] {percent:5.1f}% | Errors: {errors_count:<4} | {current_file[:65]:<65}\033[K")
    sys.stdout.flush()


def main():
    if len(sys.argv) < 2 or "-h" in sys.argv or "--help" in sys.argv:
        print("Usage: python TrajectoryStep.py <logs_directory_or_file> [--summary] [--allow-noops] [--error-dir <dir>]")
        sys.exit(0)

    input_path = Path(sys.argv[1])
    show_summary = "--summary" in sys.argv
    allow_collapse = "--allow-noops" in sys.argv

    error_dir = Path("trajectory_errors")
    if "--error-dir" in sys.argv:
        try:
            idx = sys.argv.index("--error-dir")
            error_dir = Path(sys.argv[idx + 1])
        except (IndexError, ValueError):
            pass

    if input_path.is_file():
        files = [input_path]
    elif input_path.is_dir():
        files = sorted(list(input_path.rglob("*.txt")))
    else:
        print(f"Path {input_path} does not exist!")
        sys.exit(1)

    total_files = len(files)
    if total_files == 0:
        print(f"No .txt log files found in: {input_path}")
        sys.exit(0)

    results: List[TrajectoryResult] = []
    generated_error_reports: List[Path] = []
    total_errors = 0

    print(f"Starting verification of {total_files} trajectory certificate files...")

    for idx, f in enumerate(files, 1):
        print_progress_bar(idx, total_files, total_errors, f.name)

        shared_tns = dendropy.TaxonNamespace()
        runs, rep_steps, rep_fdist, move_type, h_metric, h_variant, target_newick, force_rooted = parse_certificate_file(str(f), shared_tns)
        f_metric, f_variant = extract_metadata_from_name(f.name)
        metric = h_metric if h_metric else f_metric
        variant = h_variant if h_variant else f_variant

        file_has_error = False
        all_file_errors: List[StepError] = []

        for r_idx, steps in enumerate(runs, 1):
            chain_ok, conv, f_dist, n_steps, step_errors = verify_run(
                steps, move_type, target_newick, force_rooted, shared_tns, allow_collapse_noops=allow_collapse
            )
            if not chain_ok:
                file_has_error = True
                all_file_errors.extend(step_errors)

            final_steps = rep_steps if rep_steps is not None else n_steps
            final_d = rep_fdist if rep_fdist is not None else f_dist
            final_conv = conv and (final_d == 0.0)

            results.append(TrajectoryResult(
                f.name, move_type, chain_ok, final_conv, final_d, final_steps, metric, variant, step_errors
            ))

        if file_has_error:
            total_errors += 1
            report_path = write_error_report(
                error_dir, f, move_type, metric, variant, 1, len(runs), rep_steps, rep_fdist, all_file_errors
            )
            generated_error_reports.append(report_path)

    sys.stdout.write("\r\033[K")
    sys.stdout.flush()

    total = len(results)
    if total == 0:
        print("No valid trajectory steps parsed from the provided files.")
        sys.exit(1)

    valid_chains = sum(1 for r in results if r.is_valid_chain)
    converged = sum(1 for r in results if r.is_valid_chain and r.is_full_convergence)

    print("=" * 80)
    print(f"TRAJECTORY STEP VALIDATION : {valid_chains}/{total} ({valid_chains/total*100:.2f}%)")
    print(f"FULL CONVERGENCE (D=0.0)   : {converged}/{total}")
    print(f"TOTAL FAILED TRAJECTORIES  : {total_errors}")
    print("=" * 80)

    if generated_error_reports:
        print("\n" + "!" * 80)
        print(f"DETAILED ERROR REPORTS GENERATED ({len(generated_error_reports)} files in '{error_dir.resolve()}'):")
        print("!" * 80)
        for rep in generated_error_reports:
            print(f"  -> {rep}")
        print("!" * 80)

    if show_summary:
        print("\nBENCHMARK METRICS SUMMARY (FROM VERIFIED TRAJECTORY CERTIFICATES):")
        print(f"{'Move':<10} | {'Metric':<10} | {'Heuristic Variant':<25} | {'Valid/Total':<11} | {'Avg Steps'}")
        print("-" * 80)
        groups = defaultdict(list)
        for r in results:
            groups[(r.move_type, r.metric_name, r.variant_name)].append(r)

        for (mv, m, v), items in sorted(groups.items()):
            succ = sum(1 for x in items if x.is_valid_chain and x.is_full_convergence)
            count = len(items)
            succ_steps = [x.steps_count for x in items if x.is_valid_chain and x.is_full_convergence]
            avg_steps = f"{sum(succ_steps)/len(succ_steps):.4f}" if succ_steps else "None(Inf)"
            print(f"{mv:<10} | {m:<10} | {v:<25} | {succ:>4}/{count:<6} | {avg_steps}")
        print("-" * 80)


if __name__ == "__main__":
    main()