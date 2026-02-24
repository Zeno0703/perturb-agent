import os
import sys
import subprocess
from collections import defaultdict

OUT_DIR = "target/perturb"
ARTIFACTS = ("probes.txt", "hits.txt", "test-outcomes.txt")


def clear_artifacts(project_dir):
    target = os.path.join(project_dir, OUT_DIR)
    os.makedirs(target, exist_ok=True)
    for name in ARTIFACTS:
        path = os.path.join(target, name)
        if os.path.exists(path):
            os.remove(path)


def run_maven(probe_id, project_dir, agent_jar):
    clear_artifacts(project_dir)
    command = (
        f'mvn test -DargLine="-javaagent:\\"{agent_jar}\\" '
        f'-Djunit.jupiter.extensions.autodetection.enabled=true '
        f'-Dperturb.outDir={OUT_DIR} '
        f'-Dperturb.activeProbe={probe_id}"'
    )
    result = subprocess.run(command, shell=True, cwd=project_dir, capture_output=True, text=True)
    return result.returncode, result.stderr


def read_artifact(project_dir, filename):
    path = os.path.join(project_dir, OUT_DIR, filename)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [line.strip().split("\t", 1)
                for line in f if "\t" in line]


def discovery(project_dir, agent_jar):
    code, stderr = run_maven(-1, project_dir, agent_jar)
    if code != 0:
        sys.exit(f"Discovery failed:\n{stderr[-1000:]}")

    probes = {int(k): v
              for k, v in read_artifact(project_dir, "probes.txt")}
    if not probes:
        sys.exit("No probes found.")

    hits = defaultdict(set)
    for pid, test in read_artifact(project_dir, "hits.txt"):
        hits[int(pid)].add(test)

    return probes, hits


def evaluate(probe_id, tests, project_dir, agent_jar):
    _, stderr = run_maven(probe_id, project_dir, agent_jar)
    outcomes = {k: v.strip().upper()
                for k, v in read_artifact(project_dir, "test-outcomes.txt")}

    if not outcomes:
        print(f"  No outcomes produced:\n{stderr[-1000:]}")
        return None

    failed = passed = 0

    for test in sorted(tests):
        result = outcomes.get(test, "MISSING")
        print(f"  - {test}: {result}")
        if result == "PASS":
            passed += 1
        elif result != "MISSING":
            failed += 1

    total = failed + passed
    if total == 0:
        print("  Score: N/A")
        return None

    score = failed / total
    print(f"  Caught Perturbations: {score * 100:.2f}% ({failed}/{total})")
    return score


def main():
    if len(sys.argv) != 3:
        sys.exit("Usage: python runner.py <project_dir> <agent_jar>")

    project_dir, agent_jar = sys.argv[1], sys.argv[2]
    probes, hits = discovery(project_dir, agent_jar)

    scores = []

    for pid in sorted(probes):
        print(f"\nProbe {pid}: {probes[pid]}")
        tests = hits.get(pid)

        if not tests:
            print("  SKIP: No tests hit this probe")
            continue

        score = evaluate(pid, tests, project_dir, agent_jar)
        if score is not None:
            scores.append(score)

    if scores:
        mean = sum(scores) / len(scores)
        print(f"\nOverall Mean Perturbation Score: {mean * 100:.2f}%")
    else:
        print("\nNo scorable probes.")


if __name__ == "__main__":
    main()