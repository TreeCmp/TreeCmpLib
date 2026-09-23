#!/usr/bin/env python3
"""Trajectory Certificate Verifier and Macro-Benchmark Statistics Aggregator.

Verifies strictly that every consecutive topology transformation corresponds
to exactly 1-NNI move (DendroPy symmetric difference == 2).
"""

import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional
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
class TrajectoryResult:
    filename: str
    is_valid_nni_chain: bool
    is_full_convergence: bool
    final_distance: float
    steps_count: int
    metric_name: str
    variant_name: str


def parse_vnd_runs(
    file_path: str, tns: dendropy.TaxonNamespace
) -> (List[List[TrajectoryStep]], Optional[int], Optional[float]):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    pattern = re.compile(
        r"(?:KROK|STEP)\s+([0-9]+(?:\.[0-9]+)?)\s+\[(.*?)\]\s+-\s+(?:Dystans|Distance):\s+([0-9,.]+)\s*\n\s*(\(.*?\);)",
        re.DOTALL,
    )

    final_match = re.search(
        r"\[FINAL\]\s+Total Steps:\s*(\d+)\s*\|\s*Final Distance:\s*([0-9,.]+)",
        content,
    )
    reported_total_steps = int(final_match.group(1)) if final_match else None
    reported_final_dist = float(final_match.group(2).replace(",", ".")) if final_match else None

    runs = []
    current_run = []

    for match in pattern.finditer(content):
        step_raw = match.group(1).strip()
        step_val = float(step_raw)
        name = match.group(2).strip()
        dist = float(match.group(3).replace(",", "."))
        newick_raw = match.group(4).replace("\n", "").replace("\r", "").strip()

        try:
            tree_rooted = dendropy.Tree.get(
                data=newick_raw,
                schema="newick",
                taxon_namespace=tns,
                preserve_underscores=True,
                suppress_internal_node_taxa=True,
            )
            is_rooted = bool(
                tree_rooted.seed_node
                and len(tree_rooted.seed_node.child_nodes()) == 2
            )
            tree_rooted.is_rooted = is_rooted
            tree_rooted.encode_bipartitions()

            tree_unrooted = dendropy.Tree.get(
                data=newick_raw,
                schema="newick",
                taxon_namespace=tns,
                preserve_underscores=True,
                suppress_internal_node_taxa=True,
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
            parse_error=parse_err,
        )

        if step_val == 0.0 or (
            current_run and step_val <= current_run[-1].step_val
        ):
            if current_run:
                runs.append(current_run)
            current_run = [step]
        else:
            current_run.append(step)

    if current_run:
        runs.append(current_run)

    return runs, reported_total_steps, reported_final_dist


def verify_run(
    steps: List[TrajectoryStep],
    run_index: int,
    total_runs: int,
    filename: str,
    errors_only: bool = False,
    allow_collapse_noops: bool = False,
) -> (bool, bool, float, int):
    run_tag = f"[Run {run_index}/{total_runs}] " if total_runs > 1 else ""

    if len(steps) < 2:
        if len(steps) == 1:
            dist = steps[0].reported_dist
            return True, dist == 0.0, dist, 0
        return False, False, -1.0, 0

    all_nni_ok = True
    valid_step_count = 0

    effective_steps = [steps[0]]
    for s in steps[1:]:
        if (
            allow_collapse_noops
            and effective_steps[-1].tree_unrooted is not None
            and s.tree_unrooted is not None
        ):
            rf_u = treecompare.symmetric_difference(
                effective_steps[-1].tree_unrooted, s.tree_unrooted
            )
            is_both_r = effective_steps[-1].is_rooted and s.is_rooted
            rf_r = (
                treecompare.symmetric_difference(
                    effective_steps[-1].tree_rooted, s.tree_rooted
                )
                if is_both_r
                else None
            )

            if rf_u == 0 and (not is_both_r or rf_r == 0):
                continue
        effective_steps.append(s)

    for i in range(len(effective_steps) - 1):
        curr = effective_steps[i]
        nxt = effective_steps[i + 1]

        if (
            curr.tree_unrooted is None
            or nxt.tree_unrooted is None
            or curr.tree_rooted is None
            or nxt.tree_rooted is None
        ):
            status = "ERROR (Corrupted Newick)"
            all_nni_ok = False
            rf_dist = -1
        else:
            rf_unrooted = treecompare.symmetric_difference(
                curr.tree_unrooted, nxt.tree_unrooted
            )
            is_both_rooted = curr.is_rooted and nxt.is_rooted
            rf_rooted = (
                treecompare.symmetric_difference(curr.tree_rooted, nxt.tree_rooted)
                if is_both_rooted
                else None
            )

            if rf_unrooted == 2:
                rf_dist = 2
                status = "OK (Pure 1-NNI)"
                valid_step_count += 1
            elif is_both_rooted and rf_rooted == 2:
                rf_dist = 2
                status = "OK (Pure 1-NNI rooted)"
                valid_step_count += 1
            elif rf_unrooted == 0 and (not is_both_rooted or rf_rooted == 0):
                rf_dist = 0
                status = "ERROR (Identical trees: RF=0)"
                all_nni_ok = False
            else:
                candidate_rfs = [rf_unrooted]
                if is_both_rooted and rf_rooted is not None:
                    candidate_rfs.append(rf_rooted)
                rf_dist = min(candidate_rfs)
                status = f"ERROR (Jump RF={rf_dist} != 2)"
                all_nni_ok = False

        if not errors_only or not status.startswith("OK"):
            sys.stdout.write("\r\033[K")
            print(
                f"[{filename}] {run_tag}Step {curr.step_num:>3} -> {nxt.step_num:<3} |"
                f" Operator: {nxt.name:<32} | RF_diff: {rf_dist:<2} | Distance:"
                f" {curr.reported_dist:.4f} -> {nxt.reported_dist:.4f} | [{status}]"
            )

    final_dist = effective_steps[-1].reported_dist
    is_converged = abs(final_dist) < 1e-6
    return all_nni_ok, is_converged, final_dist, valid_step_count


def extract_metadata_from_name(filename: str) -> (str, str):
    m_new = re.match(
        r"proof_([^_]+)_(.+)_N(\d+)_pair(\d+)_\d{8}_\d{6}_\d{3}_[0-9A-Fa-f]+\.txt",
        filename,
    )
    if m_new:
        return m_new.group(1), m_new.group(2)

    m_old = re.search(r"proof_(?:pair_)?(.*?)(?:_\d{8}_\d{6}|\.txt)", filename)
    tag = m_old.group(1) if m_old else filename
    metric = "UNKNOWN"
    for cand in [
        "RFCluster",
        "RFC",
        "MC_RF",
        "MP_RF",
        "MS_RF",
        "M3_RF",
        "MC",
        "MP",
        "RF",
        "MS",
        "M3",
    ]:
        if tag.endswith(cand) or f"_{cand}_" in tag or tag.endswith(f"_{cand}"):
            metric = cand
            break

    variant = tag.replace(f"_{metric}", "")
    return metric, variant


def print_progress_bar(
    iteration: int,
    total: int,
    errors_count: int,
    current_file: str,
    bar_len: int = 20,
):
    percent = (iteration / total) * 100
    filled_len = int(bar_len * iteration // total)
    bar = "=" * filled_len + ("-" * (bar_len - filled_len))
    sys.stdout.write(
        f"\r[{iteration}/{total}] [{bar}] {percent:5.1f}% | Errors:"
        f" {errors_count:<4} | {current_file}\033[K"
    )
    sys.stdout.flush()


def main():
    if len(sys.argv) < 2 or "-h" in sys.argv or "--help" in sys.argv:
        print(
            "Usage: python TrajectoryStep.py <logs_directory_or_file> [--errors-only]"
            " [--summary] [--allow-noops]"
        )
        sys.exit(0)

    input_path = Path(sys.argv[1])
    errors_only = "--errors-only" in sys.argv
    show_summary = "--summary" in sys.argv
    allow_collapse = "--allow-noops" in sys.argv

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
    total_errors = 0

    print(f"Starting verification of {total_files} trajectory certificate files...")

    for idx, f in enumerate(files, 1):
        print_progress_bar(idx, total_files, total_errors, f.name)

        shared_tns = dendropy.TaxonNamespace()
        runs, rep_steps, rep_fdist = parse_vnd_runs(str(f), shared_tns)
        metric, variant = extract_metadata_from_name(f.name)

        file_has_error = False
        for r_idx, steps in enumerate(runs, 1):
            nni_ok, conv, f_dist, n_steps = verify_run(
                steps,
                r_idx,
                len(runs),
                f.name,
                errors_only=errors_only,
                allow_collapse_noops=allow_collapse,
            )
            if not nni_ok:
                file_has_error = True

            final_steps = rep_steps if rep_steps is not None else n_steps
            final_d = rep_fdist if rep_fdist is not None else f_dist
            final_conv = conv if rep_fdist is None else (rep_fdist == 0.0)

            results.append(
                TrajectoryResult(
                    f.name, nni_ok, final_conv, final_d, final_steps, metric, variant
                )
            )

        if file_has_error:
            total_errors += 1

    sys.stdout.write("\r\033[K")
    sys.stdout.flush()

    total = len(results)
    valid_chains = sum(1 for r in results if r.is_valid_nni_chain)
    converged = sum(1 for r in results if r.is_valid_nni_chain and r.is_full_convergence)

    print("=" * 80)
    print(
        f"TRAJECTORY STEP VALIDATION (RF=2): {valid_chains}/{total} ({valid_chains/total*100:.1f}%)"
    )
    print(f"FULL CONVERGENCE (D=0.0):          {converged}/{total}")
    print("=" * 80)

    if show_summary:
        print("\nBENCHMARK METRICS SUMMARY (FROM RECOVERED TRAJECTORY LOGS):")
        print(
            f"{'Metric':<10} | {'Heuristic Variant':<30} | {'Successes':<10} |"
            f" {'Avg Steps':<12} | {'Log Count'}"
        )
        print("-" * 80)

        groups = defaultdict(list)
        for r in results:
            groups[(r.metric_name, r.variant_name)].append(r)

        for (m, v), items in sorted(groups.items()):
            succ = sum(1 for x in items if x.is_full_convergence)
            count = len(items)
            succ_steps = [x.steps_count for x in items if x.is_full_convergence]
            avg_steps = (
                f"{sum(succ_steps)/len(succ_steps):.4f}" if succ_steps else "None(Inf)"
            )
            print(f"{m:<10} | {v:<30} | {succ:>3}/{count:<6} | {avg_steps:<12} | {count}")
        print("-" * 80)


if __name__ == "__main__":
    main()