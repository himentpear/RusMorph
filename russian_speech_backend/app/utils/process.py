import os
from pathlib import Path


def executable_environment(binary: str) -> dict[str, str]:
    """Return an environment that can launch executables from a Conda env."""
    env = os.environ.copy()
    executable = Path(binary).expanduser()
    if executable.parent.name.lower() != "scripts":
        return env

    conda_env = executable.parent.parent
    candidates = (
        conda_env,
        conda_env / "Scripts",
        conda_env / "Library" / "bin",
        conda_env / "bin",
    )
    existing = [str(path) for path in candidates if path.exists()]
    if existing:
        env["PATH"] = os.pathsep.join([*existing, env.get("PATH", "")])
    return env
