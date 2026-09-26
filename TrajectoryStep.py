#!/usr/bin/env python3
"""
Trajectory Certificate Verifier and Macro-Benchmark Statistics Aggregator.

Verifies strictly that every consecutive topology transformation corresponds
to exactly 1-NNI move (DendroPy symmetric difference == 2).
Generates detailed error reports in txt files for every failed trajectory.
"""

import sys
import re
from pathlib import Path
from dataclasses import dataclass
from typing import List, Optional
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
    is_valid_nni_chain: bool
    is_full_convergence: bool
    final_distance: float
    steps_count: int
    metric_name: str
    variant_name: str
    errors: List[StepError]


def parse_vnd_runs(file_path: str, tns: dendropy.TaxonNamespace) -> (List[List[TrajectoryStep]], Optional[int], Optional[float]):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    pattern = re.compile(
        r'(?:KROK|STEP)\s+([0-9]+(?:\.[0-9]+)?)\s+\[(.*?)\]\s+-\s+(?:Dystans|Distance):\s+([0-9,.]+)\s*\n\s*(\(.*?\);)',
        re.DOTALL
    )

    final_match = re.search(r'\[FINAL\]\s+Total Steps:\s*(\d+)\s*\|\s*Final Distance:\s*([0-9,.]+)', content)
    reported_total_steps = int(final_match.group(1)) if final_match else None
    reported_final_dist = float(final_match.group(2).replace(',', '.')) if final_match else None

    runs = []
    current_run = []

    for match in pattern.finditer(content):
        step_raw = match.group(1).strip()
        step_val = float(step_raw)
        name = match.group(2).strip()
        dist = float(match.group(3).replace(',', '.'))
        newick_raw = match.group(4).replace('\n', '').replace('\r', '').strip()

        try:
            tree_rooted = dendropy.Tree.get(
                data=newick_raw,
                schema="newick",
                taxon_namespace=tns,
                preserve_underscores=True,
                suppress_internal_node_taxa=True
            )
            is_rooted = bool(tree_rooted.seed_node and len(tree_rooted.seed_node.child_nodes()) == 2)
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

            parse_err = None
        except Exception as e:
            tree_rooted = None
            tree_unrooted = None
            is_rooted = False
            parse_err = str(e)

        step = TrajectoryStep(
            step_num=step_raw,
            step_val=step_val,
            name=name,
            reported_dist=dist,
            newick=newick_raw,
            tree_rooted=tree_rooted,
            tree_unrooted=tree_unrooted,
            is_rooted=is_rooted,
            parse_error=parse_err
        )

        if step_val == 0.0 or (current_run and step_val <= current_run[-1].step_val):
            if current_run:
                runs.append(current_run)
            current_run = [step]
        else:
            current_run.append(step)

    if current_run:
        runs.append(current_run)

    return runs, reported_total_steps, reported_final_dist


def verify_run(steps: List[TrajectoryStep], run_index: int, total_runs: int, filename: str,
               allow_collapse_noops: bool = False) -> (bool, bool, float, int, List[StepError]):
    if len(steps) < 2:
        if len(steps) == 1:
            dist = steps[0].reported_dist
            return True, dist == 0.0, dist, 0, []
        return False, False, -1.0, 0, []

    all_nni_ok = True
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

        rf_u = None
        rf_r = None

        if curr.tree_unrooted is None or nxt.tree_unrooted is None or curr.tree_rooted is None or nxt.tree_rooted is None:
            status = "ERROR (Corrupted Newick / Parse Failure)"
            all_nni_ok = False
            errors.append(StepError(
                step_from=curr.step_num, step_to=nxt.step_num, operator=nxt.name,
                dist_from=curr.reported_dist, dist_to=nxt.reported_dist,
                rf_unrooted=None, rf_rooted=None, status=status,
                newick_from=curr.newick, newick_to=nxt.newick,
                parse_err_from=curr.parse_error, parse_err_to=nxt.parse_error
            ))
        else:
            rf_u = treecompare.symmetric_difference(curr.tree_unrooted, nxt.tree_unrooted)
            is_both_rooted = curr.is_rooted and nxt.is_rooted
            rf_r = treecompare.symmetric_difference(curr.tree_rooted, nxt.tree_rooted) if is_both_rooted else None

            if rf_u == 2 or (is_both_rooted and rf_r == 2):
                valid_step_count += 1
            elif rf_u == 0 and (not is_both_rooted or rf_r == 0):
                status = "ERROR (Identical trees: RF=0, Expected RF=2)"
                all_nni_ok = False
                errors.append(StepError(
                    step_from=curr.step_num, step_to=nxt.step_num, operator=nxt.name,
                    dist_from=curr.reported_dist, dist_to=nxt.reported_dist,
                    rf_unrooted=rf_u, rf_rooted=rf_r, status=status,
                    newick_from=curr.newick, newick_to=nxt.newick
                ))
            else:
                candidate_rfs = [rf_u]
                if is_both_rooted and rf_r is not None:
                    candidate_rfs.append(rf_r)
                min_rf = min(candidate_rfs)
                status = f"ERROR (Jump RF={min_rf} != 2)"
                all_nni_ok = False
                errors.append(StepError(
                    step_from=curr.step_num, step_to=nxt.step_num, operator=nxt.name,
                    dist_from=curr.reported_dist, dist_to=nxt.reported_dist,
                    rf_unrooted=rf_u, rf_rooted=rf_r, status=status,
                    newick_from=curr.newick, newick_to=nxt.newick
                ))

    final_dist = effective_steps[-1].reported_dist
    is_converged = abs(final_dist) < 1e-6
    return all_nni_ok, is_converged, final_dist, valid_step_count, errors


def write_error_report(output_dir: Path, source_file: Path, metric: str, variant: str,
                       run_idx: int, total_runs: int, rep_steps: Optional[int],
                       rep_fdist: Optional[float], errors: List[StepError]):
    output_dir.mkdir(parents=True, exist_ok=True)
    report_file = output_dir / f"error_{source_file.stem}.txt"

    with open(report_file, "w", encoding="utf-8") as out:
        out.write("=" * 100 + "\n")
        out.write("TRAJECTORY NNI STEP VERIFICATION ERROR REPORT\n")
        out.write("=" * 100 + "\n")
        out.write(f"Source File      : {source_file.name}\n")
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
            out.write(f"Measured RF: Unrooted RF = {err.rf_unrooted}, Rooted RF = {err.rf_rooted} (Expected: 2)\n")

            if err.parse_err_from or err.parse_err_to:
                out.write(f"Parse Errors: From='{err.parse_err_from}', To='{err.parse_err_to}'\n")

            out.write("\nTree BEFORE step:\n")
            out.write(f"{err.newick_from}\n")
            out.write("\nTree AFTER step:\n")
            out.write(f"{err.newick_to}\n")
            out.write("-" * 100 + "\n\n")

    return report_file


def extract_metadata_from_name(filename: str) -> (str, str):
    m_new = re.match(r'proof_([^_]+)_(.+)_N(\d+)_pair(\d+)_\d{8}_\d{6}_\d{3}_[0-9A-Fa-f]+\.txt', filename)
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
        runs, rep_steps, rep_fdist = parse_vnd_runs(str(f), shared_tns)
        metric, variant = extract_metadata_from_name(f.name)

        file_has_error = False
        all_file_errors: List[StepError] = []

        for r_idx, steps in enumerate(runs, 1):
            nni_ok, conv, f_dist, n_steps, step_errors = verify_run(
                steps, r_idx, len(runs), f.name, allow_collapse_noops=allow_collapse
            )

            if not nni_ok:
                file_has_error = True
                all_file_errors.extend(step_errors)

            final_steps = rep_steps if rep_steps is not None else n_steps
            final_d = rep_fdist if rep_fdist is not None else f_dist
            final_conv = conv if rep_fdist is None else (rep_fdist == 0.0)

            results.append(TrajectoryResult(
                f.name, nni_ok, final_conv, final_d, final_steps, metric, variant, step_errors
            ))

        if file_has_error:
            total_errors += 1
            report_path = write_error_report(
                error_dir, f, metric, variant, 1, len(runs), rep_steps, rep_fdist, all_file_errors
            )
            generated_error_reports.append(report_path)

    sys.stdout.write("\r\033[K")
    sys.stdout.flush()

    total = len(results)
    valid_chains = sum(1 for r in results if r.is_valid_nni_chain)
    converged = sum(1 for r in results if r.is_valid_nni_chain and r.is_full_convergence)

    print("=" * 80)
    print(f"TRAJECTORY STEP VALIDATION (RF=2): {valid_chains}/{total} ({valid_chains/total*100:.2f}%)")
    print(f"FULL CONVERGENCE (D=0.0):          {converged}/{total}")
    print(f"TOTAL FAILED TRAJECTORIES:         {total_errors}")
    print("=" * 80)

    if generated_error_reports:
        print("\n" + "!" * 80)
        print(f"DETAILED ERROR REPORTS GENERATED ({len(generated_error_reports)} files in '{error_dir.resolve()}'):")
        print("!" * 80)
        for rep in generated_error_reports:
            print(f"  -> {rep}")
        print("!" * 80)

    if show_summary:
        print("\nBENCHMARK METRICS SUMMARY (FROM RECOVERED TRAJECTORY LOGS):")
        print(f"{'Metric':<10} | {'Heuristic Variant':<30} | {'Successes':<10} | {'Avg Steps':<12} | {'Log Count'}")
        print("-" * 80)

        groups = defaultdict(list)
        for r in results:
            groups[(r.metric_name, r.variant_name)].append(r)

        for (m, v), items in sorted(groups.items()):
            succ = sum(1 for x in items if x.is_full_convergence)
            count = len(items)
            succ_steps = [x.steps_count for x in items if x.is_full_convergence]
            avg_steps = f"{sum(succ_steps)/len(succ_steps):.4f}" if succ_steps else "None(Inf)"
            print(f"{m:<10} | {v:<30} | {succ:>3}/{count:<6} | {avg_steps:<12} | {count}")
        print("-" * 80)


if __name__ == "__main__":
    main()