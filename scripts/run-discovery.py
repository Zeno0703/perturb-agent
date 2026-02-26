import os
import sys
import subprocess
import time

OUT_DIR = "target/perturb"
ARTIFACTS = ("probes.txt", "hits.txt", "test-outcomes.txt")


def clear_artifacts(project_dir):
    target = os.path.join(project_dir, OUT_DIR)
    os.makedirs(target, exist_ok=True)
    for name in ARTIFACTS:
        path = os.path.join(target, name)
        if os.path.exists(path):
            os.remove(path)


def run_discovery(project_dir, agent_jar, target_package):
    clear_artifacts(project_dir)

    command = [
        "mvn", "test",
        f'-DargLine=-javaagent:"{agent_jar}"',
        f"-Dperturb.package={target_package}",
        "-Djunit.jupiter.extensions.autodetection.enabled=true",
        f"-Dperturb.outDir={OUT_DIR}",
        "-Dperturb.activeProbe=-1",
        "-Dorg.agent.hidden.bytebuddy.experimental=true",
        "-Djacoco.skip=true"
    ]

    process = subprocess.Popen(
        command, cwd=project_dir,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
    )

    # Wait for the process to finish
    _, stderr = process.communicate()
    return process.returncode, stderr


def main():
    if len(sys.argv) != 4:
        sys.exit("Usage: python3 run-discovery.py <project_dir> <agent_jar> <target_package>")

    project_dir, agent_jar, target_package = sys.argv[1:4]

    print(f"Running Discovery Phase (Probe -1) on {target_package}...")
    start_time = time.time()

    code, stderr = run_discovery(project_dir, agent_jar, target_package)

    discovery_duration = time.time() - start_time
    print(f"Discovery finished in {discovery_duration:.2f} seconds.")

    if code != 0:
        sys.exit(f"Discovery failed (exit code {code}):\n{stderr[-1000:]}")

    print(
        f"Discovery run completed successfully. Artifacts should be available in {os.path.join(project_dir, OUT_DIR)}.")


if __name__ == "__main__":
    main()