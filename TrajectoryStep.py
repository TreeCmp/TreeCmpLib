#!/usr/bin/env python3
"""
Skrypt do weryfikacji trajektorii NNI/VND z plików logów TreeCmpLib.
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
    step_num: int
    name: str
    reported_dist: float
    newick: str
    tree: Optional[dendropy.Tree] = None
    parse_error: Optional[str] = None


def parse_vnd_runs(
    file_path: str, tns: dendropy.TaxonNamespace
) -> List[List[TrajectoryStep]]:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    pattern = re.compile(
        r"KROK\s+(\d+)\s+\[(.*?)\]\s+-\s+Dystans:\s+([0-9,]+)\s*\n(\(.*?\);)",
        re.DOTALL,
    )

    runs = []
    current_run = []

    for match in pattern.finditer(content):
        step_num = int(match.group(1))
        name = match.group(2).strip()
        dist = float(match.group(3).replace(",", "."))
        newick_raw = match.group(4).replace("\n", "").replace("\r", "").strip()

        try:
            tree_obj = dendropy.Tree.get(
                data=newick_raw,
                schema="newick",
                taxon_namespace=tns,
                preserve_underscores=True,
                suppress_internal_node_taxa=True,
            )
            tree_obj.deroot()
            parse_err = None
        except Exception as e:
            tree_obj = None
            parse_err = str(e)

        step = TrajectoryStep(
            step_num=step_num,
            name=name,
            reported_dist=dist,
            newick=newick_raw,
            tree=tree_obj,
            parse_error=parse_err,
        )

        if step_num == 0 or (
            current_run and step_num <= current_run[-1].step_num
        ):
            if current_run:
                runs.append(current_run)
            current_run = [step]
        else:
            current_run.append(step)

    if current_run:
        runs.append(current_run)

    return runs


def get_substep_num(name: str) -> int:
    """Wyciąga numer podkroku z nazwy operatora (np. Substep_2 -> 2). Zwraca -1 jeśli brak."""
    m = re.search(r"Substep_(\d+)", name, re.IGNORECASE)
    return int(m.group(1)) if m else -1


def verify_run(
    steps: List[TrajectoryStep],
    run_index: int,
    total_runs: int,
    errors_only: bool = False,
) -> bool:
    if len(steps) < 2:
        return True

    all_ok = True

    for i in range(len(steps) - 1):
        curr = steps[i]
        nxt = steps[i + 1]

        dist_diff = curr.reported_dist - nxt.reported_dist
        trend = "↓" if dist_diff > 0 else ("=" if dist_diff == 0 else "↑")

        if curr.tree is None or nxt.tree is None:
            err_msg = (
                nxt.parse_error
                or curr.parse_error
                or "Błąd parsowania Newick"
            )
            err_short = err_msg.splitlines()[0] if err_msg else "Błąd Newick"
            status = f"BŁĄD (Uszkodzony Newick: {err_short[:45]})"
            rf_dist = -1
            all_ok = False
        else:
            rf_dist = treecompare.symmetric_difference(curr.tree, nxt.tree)

            name_upper = nxt.name.upper()

            is_tbr_step = "TBR" in name_upper
            is_tbr_substep = is_tbr_step and ("SUBSTEP" in name_upper)

            is_ecr_substep = ("ECR" in name_upper or ("SUBSTEP" in name_upper and not is_tbr_step))
            is_spr_step = "SPR" in name_upper

            is_pure_nni = (
                "NNI" in name_upper
                and not is_ecr_substep
                and not is_tbr_step
                and not is_spr_step
            )

            curr_sub_num = get_substep_num(curr.name)
            nxt_sub_num = get_substep_num(nxt.name)

            is_entry_into_ecr = is_ecr_substep and (
                curr_sub_num == -1 or nxt_sub_num <= curr_sub_num
            )
            is_entry_into_tbr = is_tbr_substep and (
                curr_sub_num == -1 or nxt_sub_num <= curr_sub_num
            )

            status = "OK"

            # 1. Czyste NNI: różnica podziałów to max 4 (pojedyncza rotacja krawędzi)
            if is_pure_nni and rf_dist > 4:
                status = f"BŁĄD (Czyste NNI: RF={rf_dist} > 4)"
                all_ok = False

            # 2. Wejście w klaster ECR (inicjalna przebudowa): limit RF <= 16
            elif is_entry_into_ecr and rf_dist > 16:
                status = f"BŁĄD (Wejście w ECR: RF={rf_dist} > 16)"
                all_ok = False
            elif is_entry_into_ecr:
                status = f"OK (Wejście w klaster ECR ~{max(1, rf_dist // 2)} NNI)"

            # 3. Wewnętrzny podkrok ECR: rygorystyczna kontrola (RF <= 6)
            elif is_ecr_substep and rf_dist > 6:
                status = f"BŁĄD (Podkrok ECR: RF={rf_dist} > 6)"
                all_ok = False

            # 4. Wejście w sekwencję podkroków TBR (inicjalne odcięcie / pierwszy krok)
            elif is_entry_into_tbr and rf_dist > 16:
                status = f"BŁĄD (Wejście w TBR: RF={rf_dist} > 16)"
                all_ok = False
            elif is_entry_into_tbr:
                status = f"OK (Wejście w TBR ~{max(1, rf_dist // 2)} NNI)"

            # 5. Wewnętrzny podkrok TBR (kolejny atomowy krok wzdłuż ścieżki): RF <= 6
            elif is_tbr_substep and rf_dist > 6:
                status = f"BŁĄD (Podkrok TBR: RF={rf_dist} > 6)"
                all_ok = False

            # 6. Ruch SPR (makrokrok): skok topologiczny proporcjonalny do odległości regraftu
            elif is_spr_step:
                status = f"OK (SPR ~{max(1, rf_dist // 2)} NNI)"

            # 7. Ruch TBR (makrokrok bez podkroków): skok proporcjonalny do bisekcji i rekonfiguracji
            elif is_tbr_step:
                status = f"OK (TBR ~{max(1, rf_dist // 2)} NNI)"

            # 8. Nieznany operator przekraczający limit elementarnego NNI
            elif (
                not is_pure_nni
                and not is_ecr_substep
                and not is_spr_step
                and not is_tbr_step
                and rf_dist > 4
            ):
                status = f"BŁĄD (Nieoczekiwany skok: RF={rf_dist})"
                all_ok = False

        if not errors_only or not status.startswith("OK") or not all_ok:
            if errors_only and all_ok:
                print()
            run_tag = (
                f"[Przebieg {run_index}/{total_runs}] "
                if total_runs > 1
                else ""
            )
            rf_str = str(rf_dist) if rf_dist >= 0 else "ERR"
            print(
                f"  {run_tag}Krok {curr.step_num:02d} -> {nxt.step_num:02d} | "
                f"Operator: {nxt.name:<35} | "
                f"RF_diff: {rf_str:<3} | "
                f"Dystans: {curr.reported_dist:.4f} -> {nxt.reported_dist:.4f} ({trend}) | "
                f"[{status}]"
            )

    return all_ok


def print_usage(error_msg: str = None):
    print("\n" + "=" * 70)
    if error_msg:
        print(f"[-][BŁĄD] {error_msg}")
        print("-" * 70)
        print(">>> SUGESTIA SZYBKIEGO URUCHOMIENIA:")
        print(">>>   python TrajectoryStep.py logs/. --errors-only")
        print("=" * 70 + "\n")
        return

    print("UŻYCIE SKRYPTU WERYFIKUJĄCEGO:")
    print("  python TrajectoryStep.py <ścieżka> [--errors-only]\n")
    print("=" * 70 + "\n")


if __name__ == "__main__":
    if len(sys.argv) < 2 or "-h" in sys.argv or "--help" in sys.argv:
        print_usage()
        sys.exit(0 if len(sys.argv) >= 2 else 1)

    input_path = Path(sys.argv[1])
    errors_only = "--errors-only" in sys.argv

    extra_args = [arg for arg in sys.argv[2:] if arg != "--errors-only"]
    if extra_args:
        print_usage(f"Nieznany parametr: {' '.join(extra_args)}.")
        sys.exit(1)

    if not input_path.exists():
        print_usage(f"Podana ścieżka nie istnieje: '{input_path}'.")
        sys.exit(1)

    if input_path.is_file():
        files_to_check = [input_path]
    elif input_path.is_dir():
        files_to_check = sorted(list(input_path.glob("*.txt")))
        if not files_to_check:
            print_usage(f"Brak plików .txt w folderze '{input_path}'.")
            sys.exit(1)
    else:
        print_usage(f"Ścieżka '{input_path}' jest nieprawidłowa.")
        sys.exit(1)

    total_files = len(files_to_check)
    passed_files = 0
    total_trajectories = 0
    passed_trajectories = 0

    print(
        f"Rozpoczynam weryfikację {total_files} plików (Tryb: {'Tylko Błędy' if errors_only else 'Pełny'})...\n"
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
                run_steps, idx, len(runs), errors_only=errors_only
            )
            if not run_ok:
                file_all_ok = False

        if file_all_ok:
            passed_files += 1
            passed_trajectories += len(runs)
        elif errors_only:
            print(f"  ^^^ Plik z błędem: {file_path.name}\n")

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