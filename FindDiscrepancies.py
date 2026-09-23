#!/usr/bin/env python3
"""Find Discrepancies between Java MacroBenchmark CSV and Trajectory Certificate Logs.

Compatible with both rooted and unrooted benchmark outputs.
"""

import argparse
from pathlib import Path
import re
import sys
from typing import Dict, Optional, Tuple
import pandas as pd


def normalize_variant(variant_name: str) -> str:
  """Converts CSV variant name to the exact tag used by Java's DetailedTrajectoryVndLogger."""
  v = str(variant_name).strip()
  # Match DetailedTrajectoryVndLogger.cleanToken logic
  v = v.replace("+ Tie", "Tie").replace("+Tie", "Tie").replace("+", "Tie")
  v = v.replace("->", "_").replace(">", "_").replace("<", "_")
  v = re.sub(r"^\d+\.\s*", "", v)  # Remove "1. ", "10. "
  v = re.sub(r"[^a-zA-Z0-9]+", "_", v)
  v = re.sub(r"_+", "_", v)
  v = v.strip("_")

  # Ensure consistent prefix
  if not v.startswith("NNI_") and not v.startswith("VND_"):
    v = "VND_" + v
  return v


def parse_log_file(file_path: Path) -> Tuple[Optional[int], Optional[float]]:
  """Extracts step count and final distance from certificate file."""
  try:
    with open(file_path, "r", encoding="utf-8") as f:
      content = f.read()
  except Exception:
    return None, None

  # 1. Check footer [FINAL]
  final_match = re.search(
      r"\[FINAL\]\s+(?:Total Steps|Steps):\s*(\d+)\s*\|\s*Final Distance:"
      r"\s*([0-9,.]+)",
      content,
  )
  if final_match:
    steps = int(final_match.group(1))
    dist = float(final_match.group(2).replace(",", "."))
    return steps, dist

  # 2. Fallback: count STEP / KROK blocks
  steps_matches = re.findall(
      r"(?:KROK|STEP)\s+([0-9]+(?:\.[0-9]+)?)\s+\[(.*?)\]\s+-\s+(?:Dystans|Distance):\s+([0-9,.]+)",
      content,
  )
  if steps_matches:
    return max(0, len(steps_matches) - 1), 0.0

  return None, None


def find_discrepancies(
    csv_path: Path, logs_dir: Path, metric_filter: Optional[str] = None
):
  if not csv_path.exists():
    print(f"Error: CSV file {csv_path} does not exist!")
    sys.exit(1)
  if not logs_dir.exists():
    print(f"Error: Logs directory {logs_dir} does not exist!")
    sys.exit(1)

  df = pd.read_csv(csv_path, comment="#")
  df = df[df["Success"] == True].copy()

  print(f"Scanning log files in: {logs_dir} ...")
  proof_files = list(logs_dir.rglob("proof_*.txt"))
  print(f"Found {len(proof_files)} certificate files.")

  # Indexing: (metric, normalized_heuristic, pair_idx) -> (path, steps)
  pattern = re.compile(r"proof_([^_]+)_(.+)_N(\d+)_pair(\d+)_")
  logs_data: Dict[Tuple[str, str, int], Tuple[Path, int]] = {}

  for pf in proof_files:
    m = pattern.search(pf.name)
    if m:
      m_metric = m.group(1)
      m_heur = m.group(2)
      m_pair = int(m.group(4))
      steps, _ = parse_log_file(pf)
      if steps is not None:
        logs_data[(m_metric, m_heur, m_pair)] = (pf, steps)

  print(f"Indexed {len(logs_data)} successfully parsed certificates.\n")

  discrepancies = []
  matched_count = 0
  missing_count = 0

  for _, row in df.iterrows():
    metric = str(row["Metric"]).strip()
    if metric_filter and metric != metric_filter:
      continue

    variant_raw = str(row["Variant"]).strip()
    heur_tag = normalize_variant(variant_raw)
    pair_csv = int(row["PairIndex"])
    csv_dist = float(row["Distance"])

    # Check 0-based pair indexing in logs vs 1-based in CSV
    log_key = (metric, heur_tag, pair_csv - 1)
    if log_key not in logs_data:
      log_key = (metric, heur_tag, pair_csv)

    if log_key in logs_data:
      matched_count += 1
      pf, log_steps = logs_data[log_key]
      diff = log_steps - csv_dist
      if abs(diff) > 1e-4:
        discrepancies.append({
            "Metric": metric,
            "Variant": variant_raw,
            "PairCSV": pair_csv,
            "PairLog": log_key[2],
            "CSV_Distance": csv_dist,
            "Log_Steps": log_steps,
            "Diff (Log - CSV)": diff,
            "File": pf.name,
        })
    else:
      missing_count += 1

  print("Summary of matching:")
  print(f"  - Matched pairs:      {matched_count}")
  print(f"  - Missing log files:  {missing_count}")
  print(f"  - Discrepancies:      {len(discrepancies)}\n")

  if not discrepancies:
    print(
        "PERFECT 1:1 MATCH! Zero discrepancies between CSV distance and log"
        " steps."
    )
    return

  res_df = pd.DataFrame(discrepancies)
  print("=" * 115)
  print(
      f"{'Metric':<10} | {'Variant':<35} | {'Pair':<6} | {'CSV Dist':<11} |"
      f" {'Log Steps':<10} | {'Diff':<8} | {'File'}"
  )
  print("-" * 115)
  for _, r in res_df.iterrows():
    print(
        f"{r['Metric']:<10} | {r['Variant']:<35} | {r['PairCSV']:<6} |"
        f" {r['CSV_Distance']:<11.4f} | {r['Log_Steps']:<10} |"
        f" {r['Diff (Log - CSV)']:<+8.1f} | {r['File']}"
    )
  print("=" * 115)

  print("\nBREAKDOWN BY VARIANT:")
  summary = (
      res_df.groupby(["Metric", "Variant"])
      .agg(
          Count=("PairCSV", "count"),
          Avg_CSV_Dist=("CSV_Distance", "mean"),
          Avg_Log_Steps=("Log_Steps", "mean"),
          Avg_Diff=("Diff (Log - CSV)", "mean"),
      )
      .reset_index()
  )
  print(summary.to_string(index=False))


def main():
  parser = argparse.ArgumentParser(
      description=(
          "Find discrepancies between Java benchmark CSV and trajectory"
          " certificate logs."
      )
  )
  parser.add_argument(
      "logs",
      nargs="?",
      default="logs/10",
      help="Directory with proof_*.txt files (default: logs/10)",
  )
  parser.add_argument(
      "--csv",
      "-c",
      default=None,
      help="Path to CSV file (default: auto-detect)",
  )
  parser.add_argument(
      "--metric",
      "-m",
      default=None,
      help="Metric filter (e.g., RF, MS, M3, RFCluster, MC, MP)",
  )

  args = parser.parse_args()

  logs_dir = Path(args.logs)
  if not logs_dir.exists() and Path("logs").exists():
    logs_dir = Path("logs")

  csv_files = []
  if args.csv:
    csv_files.append(Path(args.csv))
  else:
    csv_files = sorted(list(Path("results").glob("benchmark_quality_*.csv")))
    if not csv_files:
      csv_files = sorted(list(Path(".").glob("benchmark_quality_*.csv")))

  if not csv_files:
    print("Error: No benchmark_quality_*.csv found. Provide --csv <file>")
    sys.exit(1)

  for c in csv_files:
    print(f"\n{'#' * 80}")
    print(f"ANALYSIS FILE: {c.name}")
    print(f"{'#' * 80}")
    find_discrepancies(c, logs_dir, metric_filter=args.metric)


if __name__ == "__main__":
  main()