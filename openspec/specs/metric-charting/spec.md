# metric-charting Specification

## Purpose
Defines how a metric's period series is rendered as a chart in the served UI — bar charts
anchored at zero, grouped bars for two overlaid series, full hoverability per period, visual
distinction for the partial current period, and the evolution percentage shown next to the
dashboard hero chart's title. Created by archiving change graficos-linha-para-barra.

## Requirements

### Requirement: Metric series render as bar charts anchored at zero
The system SHALL render a metric's period series as a bar chart with the value axis anchored at
zero — the axis minimum SHALL always be zero, never shifted up toward the data's minimum value, so
the relative height of each bar honestly reflects its magnitude.

#### Scenario: Axis starts at zero regardless of the data range
- **WHEN** a series of values that are all far from zero is rendered (e.g. a metric oscillating between 55 and 60)
- **THEN** the chart's value axis starts at zero, not near the data's minimum

### Requirement: Two overlaid series render as grouped bars
The system SHALL render a chart carrying two series (e.g. an AI-assisted vs. non-AI-assisted
comparison) as two adjacent bars per period, not stacked — so a viewer reads "which of the two is
larger this period", not a combined total.

#### Scenario: Two-series chart shows side-by-side bars per period
- **WHEN** a chart is given two series for the same set of periods
- **THEN** each period shows two separate bars, one per series, side by side — never one bar that sums both

### Requirement: Every period is hoverable with its values
The system SHALL let a viewer see the exact value(s) of any period in a bar chart on hover, covering
the full width allotted to that period (not just the bar itself), so a period with a thin or grouped
bar is just as easy to inspect as a wide one.

#### Scenario: Hovering a period shows its value(s)
- **WHEN** a viewer points at any period's region of the chart, including empty space between grouped bars
- **THEN** the value of every series for that period is shown

### Requirement: The partial current period is visually distinguished
When the most recent period is still in progress (partial), the system SHALL render its bar with a
visually distinct style (e.g. a dashed outline or reduced opacity) from the completed periods, so a
viewer does not read it as a final value.

#### Scenario: Current partial period looks different from completed ones
- **WHEN** the chart's last period has not yet closed
- **THEN** that period's bar is rendered with a visibly different style than the other bars

### Requirement: Evolution percentage is shown alongside the dashboard hero chart
The system SHALL display the period-over-period evolution percentage next to the title of the
dashboard hero chart (the metric's main evolution chart at the top of a DORA/Fluxo/IA dashboard), so
the variation the zero-anchored axis visually compresses is still readable as a number.

#### Scenario: Hero chart shows the evolution chip
- **WHEN** a dashboard's hero chart is rendered
- **THEN** the current period's evolution percentage is shown next to the chart title
