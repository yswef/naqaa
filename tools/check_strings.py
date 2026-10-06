#!/usr/bin/env python3
"""Keeps the strings and the calls that format them in step.

A formatted string is resolved while the application runs, so a call site that passes a
value to a string without a placeholder throws and takes the process down, usually inside
a notification or a service where the user cannot see why. This compares every call in
the Kotlin sources with both resource files and fails when they disagree.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res'
SOURCES = ROOT / 'app/src/main/java'
FILES = (RES / 'values/strings.xml', RES / 'values-en/strings.xml')

ENTRY = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
CALL = re.compile(r'\b(getString|stringResource)\s*\(')
PLACEHOLDER = re.compile(r'%(\d+)\$[a-zA-Z]|%[a-zA-Z]')
REFERENCE = re.compile(r'R\.string\.([A-Za-z0-9_]+)')


def resources():
    tables = []
    for path in FILES:
        table = {}
        for name, value in ENTRY.findall(path.read_text()):
            table[name] = len(PLACEHOLDER.findall(value.replace('%%', '')))
        tables.append((path, table))
    return tables


def call_arguments(text, opening):
    """Returns the argument list of the call whose '(' sits at the given index, and its end."""
    depth = 0
    for index in range(opening, len(text)):
        character = text[index]
        if character in '([{':
            depth += 1
        elif character in ')]}':
            depth -= 1
            if depth == 0:
                return text[opening + 1:index], index
    return '', len(text)


def top_level(arguments):
    depth = 0
    parts, current = [], ''
    for character in arguments:
        if character in '([{':
            depth += 1
        elif character in ')]}':
            depth -= 1
        if character == ',' and depth == 0:
            parts.append(current)
            current = ''
        else:
            current += character
    parts.append(current)
    return [part.strip() for part in parts if part.strip()]


def main():
    tables = resources()
    names = [table for _, table in tables]
    problems = []

    for path, table in tables[1:]:
        for name in sorted(set(names[0]) ^ set(table)):
            problems.append(f'{path.name}: "{name}" is not in both files')

    for path in sorted(SOURCES.rglob('*.kt')):
        text = path.read_text()
        for match in REFERENCE.finditer(text):
            name = match.group(1)
            if name not in names[0]:
                problems.append(f'{path.name}: "{name}" is not a string')
        for call in CALL.finditer(text):
            arguments, end = call_arguments(text, call.end() - 1)
            references = [item.group(1) for item in REFERENCE.finditer(arguments)]
            if not references:
                continue
            # Only the last argument is the value being formatted; the rest are ordinary
            # arguments of the same call, so a count is taken from the resource id onwards.
            for name in references:
                if name not in names[0]:
                    continue
                passed = len(top_level(arguments)) - 1
                expected = names[0][name]
                if passed != expected:
                    problems.append(
                        f'{path.name}: "{name}" is passed {passed} value(s) but holds {expected} placeholder(s)'
                    )

    for problem in sorted(set(problems)):
        print(problem)
    print(f'{len(set(problems))} problem(s)')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
