import os
import sys
import subprocess
import time
import signal
from collections import defaultdict

OUT_DIR = "target/perturb"
ARTIFACTS = ("probes.txt", "hits.txt", "test-outcomes.txt", "perturbations.txt")


def clear_artifacts(project_dir):
    target = os.path.join(project_dir, OUT_DIR)
    os.makedirs(target, exist_ok=True)
    for name in ARTIFACTS:
        path = os.path.join(target, name)
        if os.path.exists(path):
            os.remove(path)


def run_maven(probe_id, project_dir, agent_jar, target_package, timeout_limit=None, targeted_tests=None):
    clear_artifacts(project_dir)

    arg_line = (
        f'-javaagent:"{agent_jar}" '
        f'-Dperturb.package={target_package} '
        f'-Dperturb.outDir={OUT_DIR} '
        f'-Dperturb.activeProbe={probe_id} '
        '-Dorg.agent.hidden.bytebuddy.experimental=true'
    )

    command = [
        "mvn", "test",
        f'-DargLine={arg_line}',
        "-Djunit.jupiter.extensions.autodetection.enabled=true",
        "-Djacoco.skip=true"
    ]

    if targeted_tests:
        command.append(f'-Dtest={",".join(targeted_tests)}')

    try:
        process = subprocess.Popen(
            command, cwd=project_dir,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            preexec_fn=os.setsid
        )
        _, stderr = process.communicate(timeout=timeout_limit)
        return process.returncode, stderr, False

    except subprocess.TimeoutExpired:
        os.killpg(os.getpgid(process.pid), signal.SIGTERM)
        return -1, "PROCESS TIMED OUT", True


def unescape(text):
    return text.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\")


def read_artifact(project_dir, filename):
    path = os.path.join(project_dir, OUT_DIR, filename)
    try:
        with open(path, encoding="utf-8") as f:
            result = []
            for line in f:
                if "\t" not in line:
                    continue

                parts = line.rstrip("\r\n").split("\t", 1)

                if len(parts) == 2:
                    key = unescape(parts[0])
                    val = unescape(parts[1])
                    result.append([key, val])

            return result
    except FileNotFoundError:
        return []


def discovery(project_dir, agent_jar, target_package):
    print("Running Discovery Phase...")
    start_time = time.time()

    code, stderr, _ = run_maven(-1, project_dir, agent_jar, target_package)
    discovery_duration = time.time() - start_time
    print(f"Discovery finished in {discovery_duration:.2f} seconds.")

    if code != 0:
        sys.exit(f"Discovery failed:\n{stderr[-1000:]}")

    probes = {int(k): v for k, v in read_artifact(project_dir, "probes.txt")}
    if not probes:
        sys.exit("No probes found.")

    hits = defaultdict(set)
    for pid, test in read_artifact(project_dir, "hits.txt"):
        hits[int(pid)].add(test)

    return probes, hits, discovery_duration


def evaluate(probe_id, tests, project_dir, agent_jar, target_package, timeout_limit):
    code, stderr, timed_out = run_maven(probe_id, project_dir, agent_jar, target_package, timeout_limit, targeted_tests=tests)

    if timed_out:
        print(f"  - TIMEOUT! Run exceeded {timeout_limit:.2f} seconds.\n  Result: Discarded (Infinite Loop Detected)")
        return ["TIMEOUT"], 0, len(tests), True

    outcomes = {k: v.strip() for k, v in read_artifact(project_dir, "test-outcomes.txt")}
    if not outcomes:
        print(f"  No outcomes produced:\n{stderr[-1000:]}")
        return None, 0, 0, False

    actions_map = {}
    for test_id, action in read_artifact(project_dir, "perturbations.txt"):
        if test_id not in actions_map:
            actions_map[test_id] = []
        if action not in actions_map[test_id]:
            actions_map[test_id].append(action)

    test_results = []
    failed_count = 0
    passed_count = 0

    for test in sorted(tests):
        status = outcomes.get(test, 'MISSING')
        test_results.append(status)

        test_actions = actions_map.get(test, [])
        action_str = f"  ({', '.join(test_actions)})" if test_actions else ""
        print(f"  - {test}: {status}{action_str}")

        if "FAIL" in status.upper():
            failed_count += 1
        elif status.upper() == "PASS":
            passed_count += 1

    total = failed_count + passed_count
    if total > 0:
        print(f"  Tests catching perturbation: {failed_count / total * 100:.2f}% ({failed_count}/{total})")

    return test_results, passed_count, failed_count, False


def main():
    if len(sys.argv) != 4:
        sys.exit("Usage: python3 run-agent.py <project_dir> <agent_jar> <target_package>")

    script_start = time.time()
    project_dir, agent_jar, target_package = sys.argv[1:4]

    probes, hits, discovery_duration = discovery(project_dir, agent_jar, target_package)
    dynamic_timeout = max(discovery_duration * 2.0, 10.0)
    print(f"Set strict timeout limit for evaluations: {dynamic_timeout:.2f} seconds")

    tier1_survived = 0
    tier2_error = 0
    tier3_assert = 0

    timeouts_count = skipped_count = errors_count = unknown_errors = 0
    global_tests_passed = 0
    global_tests_failed = 0

    for pid, probe_desc in sorted(probes.items()):
        print(f"\nProbe {pid}: {probe_desc}")
        tests = hits.get(pid)

        if not tests:
            print("  SKIP: No tests hit this probe")
            skipped_count += 1
            continue

        test_results, p_count, f_count, is_timeout = evaluate(pid, tests, project_dir, agent_jar, target_package, dynamic_timeout)

        if is_timeout:
            timeouts_count += 1
            tier2_error += 1
            global_tests_failed += f_count
            continue

        if test_results is not None:
            global_tests_passed += p_count
            global_tests_failed += f_count

            has_assert = False
            has_exception = False
            has_pass = False

            for status in test_results:
                s_up = status.upper()
                if "FAIL" in s_up:
                    if "ASSERT" in s_up:
                        has_assert = True
                    else:
                        has_exception = True
                elif "PASS" in s_up:
                    has_pass = True

            if has_assert:
                tier3_assert += 1
            elif has_exception:
                tier2_error += 1
            elif has_pass:
                tier1_survived += 1
            else:
                unknown_errors += 1
        else:
            errors_count += 1

    total_duration = time.time() - script_start
    total_scored = tier1_survived + tier2_error + tier3_assert
    total_tests_executed = global_tests_passed + global_tests_failed

    perturbation_score = ((tier2_error + tier3_assert) / total_scored * 100) if total_scored > 0 else 0.0
    unified_test_fail_rate = (global_tests_failed / total_tests_executed * 100) if total_tests_executed > 0 else 0.0

    print(f"""
        {'=' * 60}
                         FINAL ANALYTICS
        {'=' * 60}
        Total Probes Discovered : {len(probes)}
        Probes Evaluated        : {total_scored}
        Probes Skipped (No Hit) : {skipped_count}
        Errors (No Outcomes)    : {errors_count + unknown_errors}
        {'-' * 60}
        PERTURBATION RESOLUTION TIERS:
        Tier 1 (PASS)           : {tier1_survived} (Perturbation Survived)
        Tier 2 (FAIL Exception) : {tier2_error} (Execution Error / Timeout)
        Tier 3 (FAIL Assert)    : {tier3_assert} (Semantic Failure)
        {'-' * 60}
        TEST EXECUTION METRICS:
        Total Tests Executed    : {total_tests_executed}
        Tests Passed            : {global_tests_passed}
        Tests Failed            : {global_tests_failed}
        Unified Test Fail Rate  : {unified_test_fail_rate:.2f}%
        {'-' * 60}
        Discovery Runtime       : {discovery_duration:.2f}s
        Evaluation Runtime      : {total_duration - discovery_duration:.2f}s
        Total Script Runtime    : {total_duration:.2f}s
        {'-' * 60}
        Overall Perturbation Score : {perturbation_score:.2f}% (Tiers 2 & 3)
        {'=' * 60}
        """)


if __name__ == "__main__":
    main()