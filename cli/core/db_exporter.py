import json
import os

try:
    from probe_analyser import parse_probe
except ModuleNotFoundError:
    from .probe_analyser import parse_probe


def append_to_database(project_name, master_probes, hit_counts, db_path):
    """
    Serialise *master_probes* for *project_name* and append the rows to the
    JSON database at *db_path*. Creates the file if it does not exist yet;
    merges into it if it does.
    """
    probes_rows = []
    executions_rows = []

    for pid, mp in master_probes.items():
        desc = mp['desc']
        modifier, fqcn, method, location, operator = parse_probe(desc)

        tests_hitting = sorted(mp['test_outcomes'].keys()) if mp['test_outcomes'] else []
        total_hits = sum(hit_counts[pid].get(t, 1) for t in tests_hitting)
        probe_outcome = mp['status']

        probes_rows.append({
            "project":          project_name,
            "probe_id":         pid,
            "probe_desc":       desc,
            "asmDescriptor":    mp.get('asmDescriptor', ''),
            "line":             mp.get('line', -1),
            "operator":         operator,
            "location":         location,
            "modifier":         modifier,
            "fqcn":             fqcn,
            "method":           method,
            "total_hits":       total_hits,
            "unique_tests_hit": len(tests_hitting),
            "probe_outcome":    probe_outcome,
            "timed_out":        probe_outcome == "TIMEOUT",
        })

        for t_name, t_data in mp['test_outcomes'].items():
            outcome = t_data['outcome']
            exception = t_data['exception'] or 'none'

            if outcome == 'clean':
                t_outcome, exc_family = "FAIL by Assert", "none"
                exception = "none"
            elif outcome == 'dirty':
                t_outcome, exc_family = "FAIL by Exception", "JVM-Generic"
            elif outcome == 'timeout':
                t_outcome, exc_family = "FAIL by Exception", "JVM-Timeout"
            else:
                t_outcome, exc_family = "PASS", "none"
                exception = "none"

            executions_rows.append({
                "project":              project_name,
                "probe_id":             pid,
                "test":                 t_name,
                "hits_in_test":         hit_counts[pid].get(t_name, 1),
                "test_outcome":         t_outcome,
                "exception":            exception,
                "exception_family":     exc_family,
                "perturbation_actions": [],
            })

    if os.path.isfile(db_path):
        try:
            with open(db_path, encoding="utf-8") as f:
                db = json.load(f)
        except json.JSONDecodeError:
            db = {"probes": [], "test_executions": []}
    else:
        db = {"probes": [], "test_executions": []}

    db["probes"].extend(probes_rows)
    db["test_executions"].extend(executions_rows)

    with open(db_path, "w", encoding="utf-8") as f:
        json.dump(db, f, indent=2)

    print(
        f"JSON database updated : {db_path} "
        f"({len(db['probes'])} total probe rows)"
    )


def get_recorded_probes(db_path, project_name):
    """
    Return the set of probe IDs already recorded for the given project.
    Returns an empty set if the file does not exist.
    """
    if not os.path.isfile(db_path):
        return set()
    try:
        with open(db_path, encoding="utf-8") as f:
            db = json.load(f)
        return {p["probe_id"] for p in db.get("probes", []) if p.get("project") == project_name}
    except (json.JSONDecodeError, KeyError):
        return set()