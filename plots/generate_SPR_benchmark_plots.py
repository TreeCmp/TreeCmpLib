import glob
import os
from pathlib import Path
import traceback

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns

# Publication-ready plot styling (Paper Context)
sns.set_context("paper", font_scale=1.3)
sns.set_style("whitegrid")
plt.rcParams['font.family'] = 'serif'

OUTPUT_DIR = Path("SPR")


def save_plot(filename: str, output_dir: Path = OUTPUT_DIR, dpi: int = 300):
  """Creates the target directory if it does not exist and saves the plot with uniform settings."""
  output_dir.mkdir(parents=True, exist_ok=True)
  target_path = output_dir / filename
  plt.savefig(target_path, dpi=dpi, bbox_inches='tight')
  plt.close()


def load_data():
  # Search for raw logs from the SPR Macro Benchmark suite
  files = glob.glob("../results/SPR/benchmark_quality_SPR_*.csv")
  if not files:
    print("\nERROR: No files matching 'benchmark_quality_SPR_*.csv' found!")
    print(
        "Ensure the benchmark results exist in '../results/SPR/' relative to"
        " this script."
    )
    return None

  print(f"Loading {len(files)} CSV files...")
  df_list = [pd.read_csv(f) for f in files]
  df = pd.concat(df_list, ignore_index=True)

  # Clean infinite and non-positive timing values
  if 'Distance' in df.columns:
    df['Distance'] = df['Distance'].replace([np.inf, -np.inf], np.nan)
  if 'TotalTimeMs' in df.columns:
    df['TotalTimeMs'] = df['TotalTimeMs'].replace([np.inf, -np.inf, 0], np.nan)

  # Convert allocated bytes to megabytes for readability
  if 'AllocBytes' in df.columns:
    df['AllocMB'] = df['AllocBytes'] / (1024.0 * 1024.0)

  return df


def plot_performance_and_memory(df):
  print("Generating SPR Performance and Memory plots (Rooted and Unrooted)...")

  metric_groups = {
      'Rooted': ['RFCluster', 'MC', 'MP'],
      'Unrooted': ['RF', 'MS', 'M3'],
  }

  variants_order = [
      'Classic (Pure)',
      'Increm. (Pure)',
      'Classic + RF (Tie)',
      'Increm. + RF (Tie)',
  ]

  # Color pairing: identical base color per strategy family
  color_pure = '#1f77b4'  # Blue
  color_rf_tie = '#d95f02'  # Vermillion / Orange

  palette = {
      'Classic (Pure)': color_pure,
      'Increm. (Pure)': color_pure,
      'Classic + RF (Tie)': color_rf_tie,
      'Increm. + RF (Tie)': color_rf_tie,
  }

  # Line style: dashed (4, 2) for Classic, solid () for Incremental
  dashes = {
      'Classic (Pure)': (4, 2),
      'Increm. (Pure)': (),
      'Classic + RF (Tie)': (4, 2),
      'Increm. + RF (Tie)': (),
  }

  # Distinct markers per strategy family
  markers = {
      'Classic (Pure)': 'o',
      'Increm. (Pure)': 'o',
      'Classic + RF (Tie)': 's',
      'Increm. + RF (Tie)': 's',
  }

  # Aggregate per test scenario before plotting
  agg_df = df.groupby(
      ['Size', 'IsRooted', 'Metric', 'Variant'], as_index=False
  ).mean(numeric_only=True)

  for group_name, metrics in metric_groups.items():
    is_rooted = group_name == 'Rooted'
    plot_df = agg_df[
        (agg_df['IsRooted'] == is_rooted) & (agg_df['Metric'].isin(metrics))
    ]

    if plot_df.empty:
      print(f"Warning: No data found for group '{group_name}'. Skipping plot.")
      continue

    fig, axes = plt.subplots(2, 3, figsize=(16, 9), sharex=True)
    handles, labels = None, None

    for i, metric in enumerate(metrics):
      data_metric = plot_df[plot_df['Metric'] == metric]

      if data_metric.empty:
        continue

      # --- Top row: Execution Time ---
      ax_time = axes[0, i]
      draw_legend = i == 0

      sns.lineplot(
          data=data_metric,
          x='Size',
          y='TotalTimeMs',
          hue='Variant',
          style='Variant',
          hue_order=variants_order,
          style_order=variants_order,
          palette=palette,
          dashes=dashes,
          markers=markers,
          linewidth=2,
          err_style='bars',
          ax=ax_time,
          legend=draw_legend,
      )

      if draw_legend:
        handles, labels = ax_time.get_legend_handles_labels()
        ax_time.get_legend().remove()

      ax_time.set_yscale('log')
      ax_time.set_title(f'Metric: {metric} ({group_name})', fontweight='bold')
      ax_time.set_ylabel(
          'Avg Time per Pair [ms] (Log)' if i == 0 else ''
      )
      ax_time.set_xlabel('')

      # --- Bottom row: Allocated Memory ---
      ax_mem = axes[1, i]
      sns.lineplot(
          data=data_metric,
          x='Size',
          y='AllocMB',
          hue='Variant',
          style='Variant',
          hue_order=variants_order,
          style_order=variants_order,
          palette=palette,
          dashes=dashes,
          markers=markers,
          linewidth=2,
          err_style='bars',
          ax=ax_mem,
          legend=False,
      )

      ax_mem.set_yscale('log')
      ax_mem.set_ylabel(
          'Avg Allocated Memory [MB] (Log)' if i == 0 else ''
      )
      ax_mem.set_xlabel('Tree Size ($N$)')

    # Global shared legend
    if handles and labels:
      fig.legend(
          handles,
          labels,
          loc='lower center',
          ncol=4,
          bbox_to_anchor=(0.5, -0.05),
          frameon=True,
          title='Heuristic Variant',
      )

    plt.tight_layout()
    save_plot(f'Figure_SPR_{group_name}_Performance_Memory.png')
    print(
        f"Saved: {OUTPUT_DIR / f'Figure_SPR_{group_name}_Performance_Memory.png'}"
    )


def generate_quality_table_transposed(df, output_dir: Path = OUTPUT_DIR):
  print("Generating Transposed Quality Table (LaTeX)...")
  output_dir.mkdir(parents=True, exist_ok=True)
  output_filepath = output_dir / 'SPR_Quality_Table.tex'

  df_success = df[df['Success'] == True].copy()
  results = []

  # 1. Identify common evaluated pair subset per tree size and type
  for (size, is_rooted), group in df_success.groupby(['Size', 'IsRooted']):
    combos = group[['Metric', 'Variant']].drop_duplicates()
    common_pairs = set(group['PairIndex'].unique())

    for _, row in combos.iterrows():
      m = row['Metric']
      v = row['Variant']
      solved = set(
          group[(group['Metric'] == m) & (group['Variant'] == v)]['PairIndex']
      )
      common_pairs = common_pairs.intersection(solved)

    num_common = len(common_pairs)

    # 2. Compute paired metrics
    for _, row in combos.iterrows():
      m = row['Metric']
      v = row['Variant']
      subset = group[(group['Metric'] == m) & (group['Variant'] == v)]
      fair_subset = subset[subset['PairIndex'].isin(common_pairs)]
      common_dist = (
          fair_subset['Distance'].mean() if not fair_subset.empty else np.nan
      )
      rem_subset = subset[~subset['PairIndex'].isin(common_pairs)]
      rem_dist = (
          rem_subset['Distance'].mean() if not rem_subset.empty else np.nan
      )
      num_rem = len(rem_subset)

      results.append({
          'Size': size,
          'TreeType': 'Rooted' if is_rooted else 'Unrooted',
          'Metric': m,
          'Variant': v,
          'Common_Dist': common_dist,
          'Rem_Dist': rem_dist,
          'Num_Rem': num_rem,
          'Pairs_Eval': num_common,
      })

  res_df = pd.DataFrame(results)

  # 3. Format cells for publication LaTeX tables
  def format_cell(r):
    if pd.isna(r['Common_Dist']):
      return r'---'
    c_str = f"{r['Common_Dist']:.2f}"
    if r['Num_Rem'] > 0:
      return f"{c_str} \\scriptsize{{({r['Rem_Dist']:.2f}, n={int(r['Num_Rem'])})}}"
    return f'{c_str} \\scriptsize{{(-)}}'

  res_df['Formatted'] = res_df.apply(format_cell, axis=1)

  variant_aliases = {
      'Classic (Pure)': 'Cls',
      'Increm. (Pure)': 'Inc',
      'Classic + RF (Tie)': 'Cls+RF',
      'Increm. + RF (Tie)': 'Inc+RF',
  }
  res_df['Variant'] = res_df['Variant'].replace(variant_aliases)

  # 4. Generate LaTeX tables separately for Rooted and Unrooted sets
  with open(output_filepath, 'w', encoding='utf-8') as text_file:
    text_file.write(
        '% Required in LaTeX preamble: \\usepackage{booktabs}'
        ' \\usepackage{multirow}\n\n'
    )

    for ttype in ['Rooted', 'Unrooted']:
      sub_df = res_df[res_df['TreeType'] == ttype]
      if sub_df.empty:
        continue

      sizes = sorted(sub_df['Size'].unique())
      col_names = []
      for s in sizes:
        n_eval = sub_df[sub_df['Size'] == s]['Pairs_Eval'].iloc[0]
        col_names.append(f'N={s} (n={n_eval})')

      sub_pivot = sub_df.pivot_table(
          index=['Metric', 'Variant'],
          columns=['Size'],
          values='Formatted',
          aggfunc='first',
      )
      sub_pivot.columns = col_names

      var_order = ['Cls', 'Cls+RF', 'Inc', 'Inc+RF']
      metrics_order = (
          ['RFCluster', 'MC', 'MP']
          if ttype == 'Rooted'
          else ['RF', 'MS', 'M3']
      )
      idx = pd.MultiIndex.from_product(
          [metrics_order, var_order], names=['Metric', 'Variant']
      )
      sub_pivot = sub_pivot.reindex(idx).dropna(how='all')

      latex_code = sub_pivot.to_latex(escape=False, multirow=True, na_rep='---')

      text_file.write(f'% TABLE FOR: {ttype}\n')
      text_file.write(latex_code)
      text_file.write('\n\n')

  print(f'Saved transposed LaTeX quality tables to: {output_filepath.resolve()}')


if __name__ == '__main__':
  try:
    dataset = load_data()
    if dataset is not None:
      plot_performance_and_memory(dataset)
      generate_quality_table_transposed(dataset)
      print('=========================================')
      print('All SPR benchmarks processed successfully!')
      print(f'Artifacts saved to: {OUTPUT_DIR.resolve()}')
      print('=========================================')
  except Exception as e:
    print('\n' + '=' * 50)
    print('!!! SCRIPT EXECUTION FAILED !!!')
    print('=' * 50)
    traceback.print_exc()

  input('\n[Press ENTER to exit...]')