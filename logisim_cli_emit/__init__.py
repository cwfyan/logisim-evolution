"""Python wrapper for the Logisim-evolution component emit CLI."""

from __future__ import annotations

import json
import os
import subprocess
from dataclasses import dataclass
from typing import Iterable, Mapping, Sequence


@dataclass(frozen=True)
class EmitResult:
    stdout: str
    data: Mapping[str, object]


def emit_component(
    component: str,
    *,
    attrs: Mapping[str, str] | None = None,
    location: str | None = None,
    xml_out: str | None = None,
    xml_pretty: bool = False,
    pins_out: str | None = None,
    gradle_path: str | None = None,
    extra_args: Sequence[str] | None = None,
    check: bool = True,
) -> EmitResult:
    """Run the emitComponent CLI and return parsed JSON output.

    Args:
        component: Component factory name (e.g., "Pin", "AND Gate").
        attrs: Attribute overrides (e.g., {"facing": "east"}).
        location: Location string (e.g., "40,20" or "(40,20)").
        xml_out: Optional path to write the component XML fragment.
        xml_pretty: Whether to pretty-print XML output.
        pins_out: Optional path to write the pin layout JSON array.
        gradle_path: Path to the Gradle wrapper executable.
        extra_args: Additional CLI args to pass through.
        check: Raise on non-zero exit when True.

    Returns:
        EmitResult with raw stdout and parsed JSON data.
    """
    args: list[str] = ["--component", component]
    if attrs:
        for name, value in attrs.items():
            args.extend(["--attr", f"{name}={value}"])
    if location:
        args.extend(["--loc", location])
    if xml_pretty:
        args.append("--xml-pretty")
    if xml_out:
        args.extend(["--xml-out", xml_out])
    if pins_out:
        args.extend(["--pins-out", pins_out])
    if extra_args:
        args.extend(extra_args)

    if gradle_path is None:
        gradle_path = "gradlew.bat" if os.name == "nt" else "./gradlew"
    args_value = " ".join(args)
    command = [gradle_path, "-q", "emitComponent", f"--args={args_value}"]
    result = subprocess.run(command, check=False, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(
            "emitComponent failed "
            f"(exit={result.returncode}): {result.stderr.strip()}"
        )
    stdout = result.stdout.strip()
    data = json.loads(stdout) if stdout else {}
    return EmitResult(stdout=stdout, data=data)


__all__ = ["EmitResult", "emit_component"]
