import glob
import os
from pathlib import Path
import re
import traceback

import matplotlib.pyplot as plt
import matplotlib.ticker as mtick
import numpy as np
import pandas as pd
import seaborn as sns

# Publication-ready plot styling (Paper Context)
sns.set_context("paper", font_scale=1.3)
sns.set_style("whitegrid")
plt.rcParams['font.family'] = 'serif'

OUTPUT_DIR = Path("NNI")


def save_plot(filename: str, output_dir: Path = OUTPUT_DIR, dpi: int = 300):
  """Creates the target directory if it does not exist and saves the plot with uniform settings."""
  output_dir.mkdir(parents=True, exist_ok=True)
  target_path = output_dir / filename
  plt.savefig(target_path, dpi=dpi, bbox_inches='tight')
  plt.close()


def load_data():
  files = glob.glob("../results/NNI/benchmark_results_*.csv")
  if not files:
    print("\nERROR: No files matching 'benchmark_results_*.csv' found!")
    print(
        "Ensure the script is executed from the correct directory or adjust the"
        " glob path."
    )
    return None

  print(f"Loading {len(files)} CSV files...")
  df_list = [pd.read_csv(f) for f in files]
  df = pd.concat(df_list, ignore_index=True)

  # Normalize variant names:
  # 1. Strip leading numeric prefixes (e.g., '3. VND...' -> 'VND...')
  # 2. Canonicalize ECR naming to subtree-ECR (NNI->ECR->SPR -> NNI->2-sECR->3-sECR->SPR)
  if 'Variant' in df.columns:
    df['Variant'] = (
        df['Variant']
        .astype(str)
        .str.replace(r'^\d+\.\s*', '', regex=True)
        .str.strip()
    )
    df['Variant'] = df['Variant'].str.replace(
        'NNI->ECR->SPR', 'NNI->2-sECR->3-sECR->SPR', regex=False
    )

  # Clean infinite and non-positive timing values
  if 'Distance' in df.columns:
    df['Distance'] = df['Distance'].replace([np.inf, -np.inf], np.nan)
  if 'TotalTimeMs' in df.columns:
    df['TotalTimeMs'] = df['TotalTimeMs'].replace([np.inf, -np.inf, 0], np.nan)

  # Convert internal stage timing: nanoseconds -> milliseconds
  for col in ['NniTimeNs', 'Ecr2TimeNs', 'Ecr3TimeNs', 'SprTimeNs']:
    if col in df.columns:
      df[col.replace('Ns', 'Ms')] = df[col] / 1_000_000.0

  return df


def plot_figure_1_performance_and_memory(df):
  print("Generating Figure 1a (Rooted) and 1b (Unrooted)...")

  if 'AllocBytes' not in df.columns:
    print("\nCRITICAL ERROR: 'AllocBytes' column missing from the dataset!")
    print(
        "Ensure all CSV input files were produced by the updated benchmark"
        " suite."
    )
    return

  variants = [
      'VND NNI->2-sECR->3-sECR->SPR (Classic)',
      'VND NNI->2-sECR->3-sECR->SPR (Inc)',
      'VND NNI->SPR (Classic)',
      'VND NNI->SPR (Inc)',
  ]

  metric_groups = {
      'Rooted': ['RFCluster', 'MC', 'MP'],
      'Unrooted': ['RF', 'MS', 'M3'],
  }

  # Color pairing: identical base color per heuristic family
  color_vnd_secr = '#1f77b4'  # Blue
  color_vnd_spr = '#d95f02'  # Vermillion / Orange

  palette = {
      'VND NNI->2-sECR->3-sECR->SPR (Classic)': color_vnd_secr,
      'VND NNI->2-sECR->3-sECR->SPR (Inc)': color_vnd_secr,
      'VND NNI->SPR (Classic)': color_vnd_spr,
      'VND NNI->SPR (Inc)': color_vnd_spr,
  }

  # Line style: dashed (4, 2) for Classic, solid () for Incremental
  dashes = {
      'VND NNI->2-sECR->3-sECR->SPR (Classic)': (4, 2),
      'VND NNI->2-sECR->3-sECR->SPR (Inc)': (),
      'VND NNI->SPR (Classic)': (4, 2),
      'VND NNI->SPR (Inc)': (),
  }

  # Distinct markers per heuristic family
  markers = {
      'VND NNI->2-sECR->3-sECR->SPR (Classic)': 's',
      'VND NNI->2-sECR->3-sECR->SPR (Inc)': 's',
      'VND NNI->SPR (Classic)': 'o',
      'VND NNI->SPR (Inc)': 'o',
  }

  for group_name, metrics_to_plot in metric_groups.items():
    plot_df = df[
        df['Variant'].isin(variants) & df['Metric'].isin(metrics_to_plot)
    ]

    if plot_df.empty:
      print(f"Warning: No data found for group '{group_name}'. Skipping plot.")
      continue

    fig, axes = plt.subplots(2, 3, figsize=(16, 9), sharex=True)
    handles, labels = None, None

    for i, metric in enumerate(metrics_to_plot):
      data_metric = plot_df[plot_df['Metric'] == metric]
      if data_metric.empty:
        continue

      # Top row: Total Execution Time
      ax_time = axes[0, i]
      draw_legend = i == 0

      sns.lineplot(
          data=data_metric,
          x='Size',
          y='TotalTimeMs',
          hue='Variant',
          style='Variant',
          hue_order=variants,
          style_order=variants,
          palette=palette,
          dashes=dashes,
          markers=markers,
          err_style='bars',
          ax=ax_time,
          legend=draw_legend,
      )

      if draw_legend:
        handles, labels = ax_time.get_legend_handles_labels()
        ax_time.get_legend().remove()

      ax_time.set_yscale('log')
      ax_time.set_title(f'Metric: {metric} ({group_name})', fontweight='bold')
      ax_time.set_ylabel('Total Time [ms] (Log)' if i == 0 else '')
      ax_time.set_xlabel('')

      # Bottom row: Allocated Memory
      ax_mem = axes[1, i]
      sns.lineplot(
          data=data_metric,
          x='Size',
          y='AllocBytes',
          hue='Variant',
          style='Variant',
          hue_order=variants,
          style_order=variants,
          palette=palette,
          dashes=dashes,
          markers=markers,
          err_style='bars',
          ax=ax_mem,
          legend=False,
      )
      ax_mem.set_yscale('log')
      ax_mem.set_ylabel('Allocated Memory [Bytes] (Log)' if i == 0 else '')
      ax_mem.set_xlabel('Tree Size ($N$)')

    # Global shared legend
    if handles and labels:
      clean_labels = [re.sub(r'^\d+\.\s*', '', lbl) for lbl in labels]
      fig.legend(
          handles,
          clean_labels,
          loc='lower center',
          ncol=2,
          bbox_to_anchor=(0.5, -0.05),
          frameon=True,
          title='Heuristic Variant',
      )

    plt.tight_layout()
    save_plot(f'Figure_1_{group_name}_Performance_Memory.png')


def plot_figure_2_time_breakdown(df):
  print("Generating Figure 2: Computational Phase Breakdown...")

  target_variant = 'VND NNI->2-sECR->3-sECR->SPR (Inc)'
  df_vnd = df[df['Variant'] == target_variant].copy()

  if df_vnd.empty:
    print(
        f"Warning: No data found for variant '{target_variant}'. Skipping"
        " Figure 2."
    )
    return

  metrics = ['RFCluster', 'MC', 'MP', 'RF', 'MS', 'M3']

  fig, axes = plt.subplots(2, 3, figsize=(16, 9), sharex=True, sharey=True)
  axes = axes.flatten()

  colors = ['#4A90E2', '#F39C12', '#E67E22', '#D0021B']

  for i, metric in enumerate(metrics):
    ax = axes[i]
    data_metric = df_vnd[df_vnd['Metric'] == metric]

    if data_metric.empty:
      continue

    agg = data_metric.groupby('Size')[
        ['NniTimeMs', 'Ecr2TimeMs', 'Ecr3TimeMs', 'SprTimeMs']
    ].mean()

    # Prevent division by zero
    row_sums = agg.sum(axis=1).replace(0, 1)
    agg_perc = agg.div(row_sums, axis=0) * 100

    agg_perc.plot(
        kind='bar', stacked=True, color=colors, ax=ax, legend=False, width=0.8
    )

    ax.set_title(f'{metric}', fontweight='bold')
    ax.set_ylabel('Time Proportion [%]' if i % 3 == 0 else '')
    ax.set_xlabel('Tree Size ($N$)' if i >= 3 else '')
    ax.set_xticklabels(ax.get_xticklabels(), rotation=0)

    # Matplotlib native percentage formatter
    ax.yaxis.set_major_formatter(mtick.PercentFormatter(100.0))

  fig.legend(
      ['NNI Phase', '2-sECR Phase', '3-sECR Phase', 'SPR Phase'],
      loc='lower center',
      ncol=4,
      bbox_to_anchor=(0.5, -0.05),
      frameon=True,
  )

  plt.tight_layout()
  save_plot('Figure_2_Time_Breakdown.png')


if __name__ == '__main__':
  try:
    dataset = load_data()
    if dataset is not None:
      plot_figure_1_performance_and_memory(dataset)
      plot_figure_2_time_breakdown(dataset)
      print('=========================================')
      print('All plots generated successfully!')
      print(f'Artifacts saved to: {OUTPUT_DIR.resolve()}')
      print('=========================================')
  except Exception as e:
    print('\n' + '=' * 50)
    print('!!! SCRIPT EXECUTION FAILED !!!')
    print('=' * 50)
    traceback.print_exc()

  input('\n[Press ENTER to exit...]')