#!/usr/bin/env python3
"""
Skrypt do ścisłej weryfikacji certyfikatów trajektorii NNI/VND z plików logów TreeCmpLib.
Obsługuje przestrzeń drzew ukorzenionych (klastry) oraz bezkorzennych (splity).
Weryfikuje, czy każde kolejne przekształcenie to DOKŁADNIE 1 ruch NNI (RF == 2)
oraz czy sekwencja doprowadziła do pełnej transformacji (Dystans == 0.0).

Wymaga biblioteki DendroPy: pip install dendropy
"""

import re
import sys
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


def parse_vnd_runs(
        file_path: str, tns: dendropy.TaxonNamespace
) -> List[List[TrajectoryStep]]:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    pattern = re.compile(
        r"KROK\s+([0-9]+(?:\.[0-9]+)?)\s+\[(.*?)\]\s+-\s+Dystans:\s+([0-9,]+)\s*\n\s*(\(.*?\);)",
        re.DOTALL,
    )

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

    return runs


def verify_run(
        steps: List[TrajectoryStep],
        run_index: int,
        total_runs: int,
        errors_only: bool = False,
        allow_collapse_noops: bool = True
) -> bool:
    run_tag = f"[Przebieg {run_index}/{total_runs}] " if total_runs > 1 else ""

    if len(steps) < 2:
        if len(steps) == 1 and steps[0].reported_dist == 0.0:
            if not errors_only:
                print(f"  {run_tag}Drzewo początkowe jest identyczne z docelowym (Dystans: 0.0000, 0 kroków NNI)")
            return True
        else:
            print(f"  {run_tag}BŁĄD: Trajektoria zawiera mniej niż 2 drzewa (brak pełnej ścieżki)!")
            return False

    all_ok = True
    valid_step_count = 0

    effective_steps = [steps[0]]
    for s in steps[1:]:
        if allow_collapse_noops and effective_steps[-1].tree_unrooted is not None and s.tree_unrooted is not None:
            rf_u = treecompare.symmetric_difference(effective_steps[-1].tree_unrooted, s.tree_unrooted)
            is_both_r = effective_steps[-1].is_rooted and s.is_rooted
            rf_r = treecompare.symmetric_difference(effective_steps[-1].tree_rooted,
                                                    s.tree_rooted) if is_both_r else None

            if rf_u == 0 and (not is_both_r or rf_r == 0):
                continue
        effective_steps.append(s)

    for i in range(len(effective_steps) - 1):
        curr = effective_steps[i]
        nxt = effective_steps[i + 1]

        dist_diff = curr.reported_dist - nxt.reported_dist
        trend = "↓" if dist_diff > 0 else ("=" if dist_diff == 0 else "↑")

        if curr.tree_unrooted is None or nxt.tree_unrooted is None or curr.tree_rooted is None or nxt.tree_rooted is None:
            err_msg = nxt.parse_error or curr.parse_error or "Błąd parsowania drzewa Newick"
            err_short = err_msg.splitlines()[0] if err_msg else "Błąd Newick"
            status = f"BŁĄD (Uszkodzony Newick: {err_short[:45]})"
            rf_dist = -1
            all_ok = False
        else:
            rf_unrooted = treecompare.symmetric_difference(curr.tree_unrooted, nxt.tree_unrooted)
            is_both_rooted = curr.is_rooted and nxt.is_rooted
            rf_rooted = treecompare.symmetric_difference(curr.tree_rooted, nxt.tree_rooted) if is_both_rooted else None

            if rf_unrooted == 2:
                rf_dist = 2
                status = "OK (Czyste 1-NNI)"
                valid_step_count += 1
            elif is_both_rooted and rf_rooted == 2:
                rf_dist = 2
                status = "OK (Czyste 1-NNI ukorzenione)"
                valid_step_count += 1
            elif rf_unrooted == 0 and (not is_both_rooted or rf_rooted == 0):
                rf_dist = 0
                status = "BŁĄD (Drzewa identyczne: RF=0, brak ruchu NNI!)"
                all_ok = False
            else:
                candidate_rfs = [rf_unrooted]
                if is_both_rooted and rf_rooted is not None:
                    candidate_rfs.append(rf_rooted)
                rf_dist = min(candidate_rfs)
                nni_jumps = max(1, rf_dist // 2)
                status = f"BŁĄD (Skok o ~{nni_jumps} NNI: RF={rf_dist} != 2, brak {nni_jumps - 1} drzew pośrednich!)"
                all_ok = False

        if not errors_only or not status.startswith("OK"):
            rf_str = str(rf_dist) if rf_dist >= 0 else "ERR"
            print(
                f"  {run_tag}Krok {curr.step_num:>4} -> {nxt.step_num:<4} | "
                f"Operator: {nxt.name:<35} | "
                f"RF_diff: {rf_str:<3} | "
                f"Dystans: {curr.reported_dist:.4f} -> {nxt.reported_dist:.4f} ({trend}) | "
                f"[{status}]"
            )

    final_step = effective_steps[-1]
    if final_step.reported_dist > 0.0:
        all_ok = False
        print(
            f"  {run_tag}BŁĄD: Trajektoria nie osiągnęła celu! (Dystans końcowy = {final_step.reported_dist:.4f} != 0.0000)")
    elif all_ok and not errors_only:
        print(
            f"  {run_tag}--> CERTYFIKAT ZATWIERDZONY: Ciągła ścieżka {valid_step_count} kroków 1-NNI doprowadziła do celu (Dystans = 0.0000)\n")

    return all_ok


def print_usage(error_msg: str = None):
    print("\n" + "=" * 70)
    if error_msg:
        print(f"[-][BŁĄD] {error_msg}")
        print("-" * 70)
    print("UŻYCIE SKRYPTU WERYFIKUJĄCEGO:")
    print("  python TrajectoryStep.py <ścieżka_do_pliku_lub_katalogu> [--errors-only] [--strict]\n")
    print("Opcje:")
    print("  --errors-only : Wyświetla wyłącznie niepoprawne kroki i odrzucone pliki.")
    print("  --strict      : Wymusza odrzucanie kroków no-op (wyłącza ich kompaktowanie).")
    print("=" * 70 + "\n")


if __name__ == "__main__":
    if len(sys.argv) < 2 or "-h" in sys.argv or "--help" in sys.argv:
        print_usage()
        sys.exit(0 if len(sys.argv) >= 2 else 1)

    input_path = Path(sys.argv[1])
    errors_only = "--errors-only" in sys.argv
    strict = "--strict" in sys.argv
    allow_collapse_noops = not strict

    extra_args = [
        arg for arg in sys.argv[2:] if arg not in ("--errors-only", "--strict")
    ]
    if extra_args:
        print_usage(f"Nieznany parametr: {' '.join(extra_args)}")
        sys.exit(1)

    if not input_path.exists():
        print_usage(f"Podana ścieżka nie istnieje: '{input_path}'")
        sys.exit(1)

    if input_path.is_file():
        files_to_check = [input_path]
    elif input_path.is_dir():
        files_to_check = sorted(list(input_path.glob("*.txt")))
        if not files_to_check:
            print_usage(f"Brak plików .txt w folderze '{input_path}'")
            sys.exit(1)
    else:
        print_usage(f"Ścieżka '{input_path}' jest nieprawidłowa.")
        sys.exit(1)

    total_files = len(files_to_check)
    passed_files = 0
    total_trajectories = 0
    passed_trajectories = 0

    print(
        f"Rozpoczynam rygorystyczną weryfikację certyfikatów 1-NNI dla {total_files} plików "
        f"(Tryb: {'Tylko Błędy' if errors_only else 'Pełny'} | Kompaktowanie no-opów: {'Wyłączone' if strict else 'Włączone'})...\n"
    )

    for file_idx, file_path in enumerate(files_to_check, 1):
        shared_tns = dendropy.TaxonNamespace()
        runs = parse_vnd_runs(str(file_path), shared_tns)

        if not runs:
            continue

        total_trajectories += len(runs)
        file_all_ok = True

        if errors_only:
            print(
                f"\r[Przetwarzanie: {file_idx}/{total_files} plików ({file_idx / total_files * 100:.1f}%)]",
                end="",
                flush=True,
            )
        else:
            print(
                f"### [{file_idx}/{total_files}] PLIK: {file_path.name} ({len(runs)} trajektorii)"
            )

        for idx, run_steps in enumerate(runs, 1):
            run_ok = verify_run(
                run_steps,
                idx,
                len(runs),
                errors_only=errors_only,
                allow_collapse_noops=allow_collapse_noops,
            )
            if not run_ok:
                file_all_ok = False

        if file_all_ok:
            passed_files += 1
            passed_trajectories += len(runs)
        elif errors_only:
            print(f"\n  ^^^ ODRZUCONY PLIK: {file_path.name}\n")

    if errors_only and total_files > 0:
        print()

    print("=" * 70)
    print(
        f"PODSUMOWANIE PLIKÓW:       Sprawdzono {total_files} | Poprawnych: {passed_files}/{total_files}"
    )
    print(
        f"PODSUMOWANIE TRAJEKTORII:  Sprawdzono {total_trajectories} | Poprawnych: {passed_trajectories}/{total_trajectories}"
    )
    print("=" * 70)

    sys.exit(0 if passed_files == total_files and total_trajectories > 0 else 1)