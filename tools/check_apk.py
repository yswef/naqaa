#!/usr/bin/env python3
"""Describes what is inside a built application file.

A downloaded file is judged by its contents, not by its size: an application without images,
advertising or network libraries is small, and a file that lost half of itself looks the
same in a file manager. This reads the archive, prints its entries grouped by kind, names the
payload files it carries, counts the classes in the compiled code, and fails when a component
named in the manifest is absent.

The names of the application's own classes are not listed, because the build renames and
merges them; the manifest keeps the names of the components, and those are the ones checked.
"""
import struct
import sys
import zipfile

# Kept by the build tools because the manifest names them. If one of these is missing the
# file cannot work, whatever the rest of it looks like.
COMPONENTS = (
    'Lcom/naqaa/app/NaqaaApplication;',
    'Lcom/naqaa/app/ui/MainActivity;',
    'Lcom/naqaa/app/ui/AppLockActivity;',
    'Lcom/naqaa/app/ui/DelayActivity;',
    'Lcom/naqaa/app/vpn/DnsVpnService;',
    'Lcom/naqaa/app/accessibility/GuardAccessibilityService;',
    'Lcom/naqaa/app/widget/ProgressWidget;',
    'Lcom/naqaa/app/guard/BootReceiver;',
)

CODE = 'code'
TABLE = 'resource table'
ASSETS = 'assets'
SOUND = 'sound'
NATIVE = 'native libraries'
OTHER = 'other resources'

LISTED = 12


def group_of(name):
    if name.endswith('.dex'):
        return CODE
    if name.endswith('.arsc'):
        return TABLE
    if name.startswith('assets/'):
        return ASSETS
    if name.startswith('lib/'):
        return NATIVE
    if name.startswith('res/raw/'):
        return SOUND
    return OTHER


def human(size):
    if size >= 1024 * 1024:
        return f'{size / 1048576:.2f} MiB'
    return f'{size / 1024:.0f} KiB'


def main():
    if len(sys.argv) != 2:
        print('usage: check_apk.py <file.apk>')
        return 2
    path = sys.argv[1]
    sizes, counts, payload = {}, {}, []
    classes = 0
    code = b''
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        for entry in entries:
            group = group_of(entry.filename)
            sizes[group] = sizes.get(group, 0) + entry.file_size
            counts[group] = counts.get(group, 0) + 1
            if group == CODE:
                data = archive.read(entry.filename)
                code += data
                classes += struct.unpack_from('<I', data, 96)[0]
            elif group in (ASSETS, SOUND):
                payload.append((entry.filename, entry.file_size))

    missing = [name for name in COMPONENTS if name.encode() not in code]
    print(f'  contents: {len(entries)} entries, {human(sum(sizes.values()))} unpacked')
    for group, size in sorted(sizes.items(), key=lambda item: -item[1]):
        detail = f', {classes} classes' if group == CODE else ''
        print(f'    {group}: {human(size)}, {counts[group]} file(s){detail}')
    for name, size in sorted(payload)[:LISTED]:
        print(f'      {name}: {human(size)}')
    if len(payload) > LISTED:
        print(f'      and {len(payload) - LISTED} more file(s)')
    if missing:
        print(f'  components missing: {", ".join(missing)}')
        return 1
    print('  components: present')
    return 0


if __name__ == '__main__':
    sys.exit(main())
