#!/usr/bin/env python3
import shlex
import subprocess
import sys


def main() -> int:
    args = shlex.join(sys.argv[1:])
    command = ["./gradlew", "-q", "emitComponent", "--args", args]
    result = subprocess.run(command, check=False)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
