#!/usr/bin/env python3
"""Estimate CT1-CT5 distributions from existing SAST Instructions.json files.

The script does not rerun the analyzer. It maps classIfStmtsMap records to the
conditional bytecode instructions already present in the analyzed class tree.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import subprocess
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import DefaultDict, Iterable


BRANCH_RE = re.compile(r"^\s*(\d+):\s+(if\S+)(?:\s+.*)?$")
INSTRUCTION_RE = re.compile(r"^\s*(\d+):\s+(\S+)(?:\s+(.*))?$")
LINE_RE = re.compile(r"^\s*line\s+(-?\d+):\s+(\d+)\s*$")

MEMBERSHIP_METHODS = {
    "contains",
    "containsall",
    "containskey",
    "containsvalue",
    "endswith",
    "find",
    "lookingat",
    "matches",
    "regionmatches",
    "startswith",
}
EMPTY_METHODS = {
    "isempty",
    "isblank",
    "isnull",
    "ispresent",
    "nonnull",
}
RELATION_METHODS = {
    "contentequals",
    "deepequals",
    "equals",
    "equalsignorecase",
}
NUMERIC_METHODS = {
    "compare",
    "compareto",
    "comparetoignorecase",
    "length",
    "size",
}
TYPE_METHODS = {
    "isassignablefrom",
    "isinstance",
}


@dataclass(frozen=True)
class Project:
    name: str
    instructions: Path
    classpath: Path
    prefixes: tuple[str, ...]


@dataclass
class MethodCode:
    name: str
    instructions: list[tuple[int, str, str]]
    lines: list[tuple[int, int]]


def method_name(header: str, class_name: str) -> str:
    header = header.strip()
    if header == "static {};":
        return "<clinit>"
    before_paren = header.split("(", 1)[0].strip()
    candidate = before_paren.rsplit(" ", 1)[-1]
    if candidate in {class_name, class_name.rsplit(".", 1)[-1]}:
        return "<init>"
    return candidate.rsplit(".", 1)[-1]


def parse_javap(text: str, class_name: str) -> list[MethodCode]:
    lines = text.splitlines()
    methods: list[MethodCode] = []
    previous_nonempty = ""
    index = 0
    while index < len(lines):
        line = lines[index]
        if line.strip() != "Code:":
            if line.strip():
                previous_nonempty = line
            index += 1
            continue

        current = MethodCode(method_name(previous_nonempty, class_name), [], [])
        index += 1
        while index < len(lines):
            match = INSTRUCTION_RE.match(lines[index])
            if not match:
                break
            current.instructions.append(
                (int(match.group(1)), match.group(2), match.group(3) or "")
            )
            index += 1

        while index < len(lines):
            stripped = lines[index].strip()
            if stripped == "LineNumberTable:":
                index += 1
                while index < len(lines):
                    match = LINE_RE.match(lines[index])
                    if not match:
                        break
                    current.lines.append((int(match.group(2)), int(match.group(1))))
                    index += 1
                continue
            if stripped == "Code:" or (lines[index].startswith("  ") and stripped.endswith(";") and "(" in stripped):
                break
            index += 1
        methods.append(current)
    return methods


def invoked_method_name(detail: str) -> str | None:
    if "//" not in detail or "Method " not in detail and "InterfaceMethod " not in detail:
        return None
    comment = detail.split("//", 1)[1]
    match = re.search(r"(?:InterfaceMethod|Method)\s+[^:]+\.\"?([^.:\"]+)\"?:", comment)
    if match:
        return match.group(1).lower()
    match = re.search(r"(?:InterfaceMethod|Method)\s+([^:]+):", comment)
    if match:
        return match.group(1).rsplit("/", 1)[-1].lower()
    return None


def classify_predicate_method(invoked_name: str | None) -> str:
    if invoked_name in EMPTY_METHODS:
        return "CT1"
    if invoked_name in MEMBERSHIP_METHODS:
        return "CT3"
    if invoked_name in TYPE_METHODS:
        return "CT4"
    if invoked_name in RELATION_METHODS:
        return "CT5"
    # Generalized value comparison: boolean-returning predicates and numeric
    # properties are treated as comparisons against true/false or a value.
    return "CT2"


def classify_branch(
    instructions: list[tuple[int, str, str]], branch_index: int
) -> str:
    _, opcode, _ = instructions[branch_index]
    if opcode in {"ifnull", "ifnonnull"}:
        return "CT1"
    if opcode.startswith("if_acmp"):
        return "CT5"
    if opcode.startswith("if_icmp"):
        return "CT2"

    previous = instructions[max(0, branch_index - 3) : branch_index]
    previous_opcodes = [item[1] for item in previous]
    if "instanceof" in previous_opcodes:
        return "CT4"

    if previous:
        _, previous_opcode, previous_detail = previous[-1]
        invoked_name = invoked_method_name(previous_detail) if previous_opcode.startswith("invoke") else None
        if invoked_name is not None:
            return classify_predicate_method(invoked_name)
        if previous_opcode in {
            "iload",
            "iload_0",
            "iload_1",
            "iload_2",
            "iload_3",
            "iconst_m1",
            "iconst_0",
            "iconst_1",
            "iconst_2",
            "iconst_3",
            "iconst_4",
            "iconst_5",
            "bipush",
            "sipush",
            "getfield",
            "getstatic",
            "lcmp",
            "fcmpl",
            "fcmpg",
            "dcmpl",
            "dcmpg",
        }:
            return "CT2"
    return "CT2"


def source_line(offset: int, line_table: list[tuple[int, int]]) -> int | None:
    candidates = [item for item in line_table if item[0] <= offset]
    if not candidates:
        return None
    return max(candidates, key=lambda item: item[0])[1]


def bytecode_sites(
    text: str, class_name: str
) -> tuple[
    DefaultDict[tuple[str, int], list[str]],
    set[tuple[str, int]],
    DefaultDict[tuple[str, int], list[str]],
]:
    result: DefaultDict[tuple[str, int], list[str]] = defaultdict(list)
    executable_lines: set[tuple[str, int]] = set()
    line_hints: DefaultDict[tuple[str, int], list[str]] = defaultdict(list)
    for method in parse_javap(text, class_name):
        branch_lines: DefaultDict[int, list[str]] = defaultdict(list)
        semantic_lines: DefaultDict[int, list[str]] = defaultdict(list)
        for index, (offset, opcode, _) in enumerate(method.instructions):
            line = source_line(offset, method.lines)
            if line is None:
                continue
            executable_lines.add((method.name, line))
            if opcode.startswith("if"):
                branch_lines[line].append(classify_branch(method.instructions, index))
            if opcode.startswith("invoke"):
                invoked_name = invoked_method_name(method.instructions[index][2])
                if invoked_name is not None:
                    hint = classify_predicate_method(invoked_name)
                    line_hints[(method.name, line)].append(hint)
                    if invoked_name in MEMBERSHIP_METHODS or invoked_name in RELATION_METHODS:
                        semantic_lines[line].append(hint)

        for line in set(branch_lines) | set(semantic_lines):
            categories = branch_lines[line]
            # contains()/equals() may be represented as a separate condition node.
            # Add semantic sites only when the log has more records than branches.
            result[(method.name, line)].extend(categories)
            result[(method.name, line)].extend(semantic_lines[line])
    return result, executable_lines, line_hints


def load_records(path: Path) -> dict[str, list[dict]]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)["classIfStmtsMap"]


def run_javap(classpath: Path, class_name: str) -> str | None:
    process = subprocess.run(
        ["javap", "-classpath", str(classpath), "-c", "-l", "-p", class_name],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return process.stdout if process.returncode == 0 else None


def analyze(project: Project) -> dict:
    records = {
        class_name: items
        for class_name, items in load_records(project.instructions).items()
        if any(class_name.startswith(prefix) for prefix in project.prefixes)
    }
    counts: Counter[str] = Counter()
    diagnostics: Counter[str] = Counter()

    for class_name, class_records in records.items():
        text = run_javap(project.classpath, class_name)
        if text is None:
            counts["Unresolved"] += len(class_records)
            diagnostics["missing_class"] += len(class_records)
            continue
        conditions, executable_lines, line_hints = bytecode_sites(text, class_name)
        grouped: DefaultDict[tuple[str, int], list[dict]] = defaultdict(list)
        for record in class_records:
            grouped[(record.get("methodName", ""), record.get("lineNumber", -1))].append(record)

        for key, grouped_records in grouped.items():
            categories = conditions.get(key, [])
            if categories:
                for category in categories[: len(grouped_records)]:
                    counts[category] += 1
                if len(grouped_records) > len(categories):
                    difference = len(grouped_records) - len(categories)
                    hints = line_hints.get(key, [])
                    for index in range(difference):
                        hint_index = len(categories) + index
                        counts[hints[hint_index] if hint_index < len(hints) else "CT2"] += 1
                    diagnostics["generalized_extra_record"] += difference
                continue
            if key in executable_lines:
                hints = line_hints.get(key, [])
                for index in range(len(grouped_records)):
                    counts[hints[index] if index < len(hints) else "CT2"] += 1
                diagnostics["generalized_executable_site"] += len(grouped_records)
                continue

            for record in grouped_records:
                if record.get("lineNumber", -1) < 0:
                    counts["CT4"] += 1
                    diagnostics["generalized_exception_edge"] += 1
                    continue
                successor_keys = [
                    (key[0], line)
                    for line in record.get("successor", [])
                    if isinstance(line, int) and line >= 0
                ]
                successor_categories = [
                    category
                    for successor_key in successor_keys
                    for category in conditions.get(successor_key, [])
                ]
                if successor_categories:
                    counts[successor_categories[0]] += 1
                    diagnostics["matched_by_successor"] += 1
                elif any(successor_key in executable_lines for successor_key in successor_keys):
                    successor_hints = [
                        hint
                        for successor_key in successor_keys
                        for hint in line_hints.get(successor_key, [])
                    ]
                    counts[successor_hints[0] if successor_hints else "CT4"] += 1
                    diagnostics["generalized_successor_site"] += 1
                else:
                    counts["Unresolved"] += 1
                    diagnostics["no_line_match"] += 1

    total = sum(len(items) for items in records.values())
    supported = sum(counts[name] for name in ("CT1", "CT2", "CT3", "CT4", "CT5"))
    mapped = total - counts["Unresolved"]
    result = {
        "project": project.name,
        "instructions": str(project.instructions),
        "classpath": str(project.classpath),
        "total": total,
        "mapped": mapped,
        "mapping_rate": mapped / total if total else 0.0,
        "counts": {name: counts[name] for name in ("CT1", "CT2", "CT3", "CT4", "CT5", "Other", "Unresolved")},
        "percentages": {
            name: counts[name] * 100.0 / total if total else 0.0
            for name in ("CT1", "CT2", "CT3", "CT4", "CT5", "Other", "Unresolved")
        },
        "overall_coverage": supported * 100.0 / total if total else 0.0,
        "diagnostics": dict(diagnostics),
    }
    return result


def write_csv(results: Iterable[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "project",
        "total",
        "mapped",
        "mapping_rate",
        "CT1",
        "CT2",
        "CT3",
        "CT4",
        "CT5",
        "Other",
        "Unresolved",
        "overall_coverage",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for result in results:
            writer.writerow(
                {
                    "project": result["project"],
                    "total": result["total"],
                    "mapped": result["mapped"],
                    "mapping_rate": f'{result["mapping_rate"] * 100:.2f}',
                    **{name: result["counts"][name] for name in ("CT1", "CT2", "CT3", "CT4", "CT5", "Other", "Unresolved")},
                    "overall_coverage": f'{result["overall_coverage"]:.2f}',
                }
            )


def parse_project(value: str) -> Project:
    parts = value.split("=", 3)
    if len(parts) != 4:
        raise argparse.ArgumentTypeError(
            "project must be NAME=INSTRUCTIONS_JSON=CLASSPATH=PREFIX1,PREFIX2"
        )
    prefixes = tuple(prefix for prefix in parts[3].split(",") if prefix)
    return Project(
        parts[0],
        Path(parts[1]).expanduser(),
        Path(parts[2]).expanduser(),
        prefixes,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", action="append", type=parse_project, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--csv-output", type=Path, required=True)
    args = parser.parse_args()

    results = [analyze(project) for project in args.project]
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(results, args.csv_output)
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
