import os
import sys
import subprocess
from collections import defaultdict

ARTIFACT_FILES = ("probes.txt", "hits.txt", "test-outcomes.txt")


def clear_previous_artifacts(project_dir, out_dir):
    target_directory = os.path.join(project_dir, out_dir)
    os.makedirs(target_directory, exist_ok=True)
    for artifact in ARTIFACT_FILES:
        file_path = os.path.join(target_directory, artifact)
        if os.path.exists(file_path):
            os.remove(file_path)


def execute_maven_tests(active_probe, project_dir, agent_jar, out_dir):
    clear_previous_artifacts(project_dir, out_dir)

    maven_command = (
        f'mvn test -DargLine="-javaagent:\\"{agent_jar}\\" '
        f'-Djunit.jupiter.extensions.autodetection.enabled=true '
        f'-Dperturb.outDir={out_dir} '
        f'-Dperturb.activeProbe={active_probe}"'
    )

    process_result = subprocess.run(
        maven_command,
        shell=True,
        cwd=project_dir,
        capture_output=True,
        text=True
    )

    return process_result.returncode, process_result.stdout, process_result.stderr


def parse_tracking_file(file_name, project_dir, out_dir):
    file_path = os.path.join(project_dir, out_dir, file_name)
    if not os.path.exists(file_path):
        return []

    parsed_rows = []
    with open(file_path, encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if "\t" in line:
                parsed_rows.append(line.split("\t", 1))
    return parsed_rows


def main():
    if len(sys.argv) != 3:
        sys.exit("Usage: python runner.py <project_dir> <agent_jar>")

    project_dir, agent_jar = sys.argv[1], sys.argv[2]
    out_dir = "target/perturb"

    return_code, _, stderr_output = execute_maven_tests(-1, project_dir, agent_jar, out_dir)

    if return_code != 0:
        sys.exit(f"Discovery run failed (return code={return_code}).\n{stderr_output[-1500:]}")

    discovered_probes = {
        int(row[0]): row[1]
        for row in parse_tracking_file("probes.txt", project_dir, out_dir)
    }

    if not discovered_probes:
        sys.exit("No probes found (probes.txt is missing or empty)")

    probe_hits_map = defaultdict(set)
    for row in parse_tracking_file("hits.txt", project_dir, out_dir):
        probe_hits_map[int(row[0])].add(row[1])

    individual_probe_scores = []

    for probe_id in sorted(discovered_probes):
        description = discovered_probes[probe_id]
        executing_tests = probe_hits_map.get(probe_id, set())

        print(f"\nProbe {probe_id}: {description}")

        if not executing_tests:
            print("  SKIP: No tests hit this probe")
            continue

        return_code, _, stderr_output = execute_maven_tests(probe_id, project_dir, agent_jar, out_dir)

        test_outcomes = {
            row[0]: row[1].strip().upper()
            for row in parse_tracking_file("test-outcomes.txt", project_dir, out_dir)
        }

        if not test_outcomes:
            print(f"  ERROR: No test-outcomes.txt produced (Maven return code={return_code})")
            if stderr_output:
                print(stderr_output[-1500:])
            continue

        failed_tests_count = 0
        passed_tests_count = 0
        missing_outcomes_count = 0

        for test_id in sorted(executing_tests):
            outcome = test_outcomes.get(test_id, "MISSING")
            print(f"  - {test_id}: {outcome}")

            if outcome == "MISSING":
                missing_outcomes_count += 1
                continue

            if outcome != "PASS":
                failed_tests_count += 1
            else:
                passed_tests_count += 1

        total_valid_tests = failed_tests_count + passed_tests_count

        if total_valid_tests == 0:
            print("  Score: N/A (all hit tests missing outcomes)")
            continue

        probe_hit_score = failed_tests_count / total_valid_tests
        individual_probe_scores.append(probe_hit_score)

        print(f"  Caught Perturbations: {probe_hit_score * 100:.2f}% ({failed_tests_count}/{total_valid_tests})")

        if missing_outcomes_count:
            print(f"  Note: {missing_outcomes_count} hit tests had no outcome recorded")

    if individual_probe_scores:
        mean_hit_score = sum(individual_probe_scores) / len(individual_probe_scores)
        print(f"\nOverall Mean Perturbation Score: {mean_hit_score * 100:.2f}%")
    else:
        print("\nNo scorable probes.")


if __name__ == "__main__":
    main()