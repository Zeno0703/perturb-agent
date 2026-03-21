# Perturbation Framework

This tool is a Dynamic Data-State Perturbation Framework. It uses a Java agent to dynamically perturb state during test execution to identify weak test assertions, missing tests, and brittle code. The entire process (discovery, targeted evaluation, and dashboard/database generation) is automated via a unified Python CLI.

## Project Structure

* **`agent/`**: The ByteBuddy-powered Java Agent that performs the bytecode manipulation.
* **`cli/`**: The Python orchestrator and dashboard generator.
* **`research/`**: Configuration files, generated databases, and post-processing analysis scripts.

---

## 1. Build the Agent

Before running the analysis, you need to compile and package the Java agent into a `.jar` file.

```bash
cd agent
mvn clean package
```

*This will generate `perturb-agent-1.0-SNAPSHOT.jar` in your `agent/target/` directory.*

## 2. Run the Analysis

The main entry point for the tool is the `main.py` script inside the `cli/` folder. It automatically orchestrates the Maven test runs, collects the outcomes, and exports the data exactly how you need it.

### Single Project Mode

```bash
cd cli
python main.py <path_to_project_dir> <path_to_agent_jar> <target_package> [options]
```

**Arguments:**
1. `<path_to_project_dir>`: The absolute path to the target Maven project you want to test.
2. `<path_to_agent_jar>`: The absolute path to your built perturbation agent `.jar`.
3. `<target_package>`: The base Java package to instrument (e.g., `org.example`).

### Batch Mode

To evaluate multiple projects automatically (and safely skip any that have already been recorded in your database):

```bash
cd cli
python main.py --batch ../research/data/projects.json <path_to_agent_jar> [options]
```

## 3. Options & Flags

You can mix and match output formats using the `--format` flag:

* `--format stdout` *(default)*: Prints the final analytics summary directly to the terminal.
* `--format html`: Generates the interactive HTML dashboard and opens it in your default web browser.
* `--format json`: Appends the full evaluation results to the central JSON database.

**Additional Flags:**
* `--discovery-only`: Executes the initial probe discovery run (activating probe `-1`) and exits. It writes the `probes` and `hits` artifacts but completely skips the lengthy evaluation phase.
* `--no-browser`: Generates the HTML dashboard but prevents it from automatically popping open.
* `--output <path>`: Overrides the default JSON database destination (defaults to `../research/data/database.json`).

## 4. Output Artifacts

**Per-Project Artifacts**
Regardless of the mode used, all raw artifacts for a specific run are saved inside the target project directory under `target/perturb/`. These include:
* `execution.log`: The raw terminal output and diagnostic warnings from the analysis run.
* `probes.txt`: Discovered probes and their descriptions (JSONL format).
* `hits.txt`: Mapping of which tests hit which probes during discovery (JSONL format).
* `test-outcomes.txt` & `perturbations.txt`: Per-test pass/fail states and execution traces (JSONL format).
* `dashboard.html` & `source_cache.js`: The interactive UI and its source code cache (if `--format html` was used).

**Global Artifacts**
If you run the pipeline with `--format json`, all project results are aggregated and appended to the central database located at:
`research/data/database.json`