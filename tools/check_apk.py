#!/usr/bin/env python3
"""Describes what is inside a built application file.

A downloaded file is judged by its contents, not by its size: an application without images,
advertising or network libraries is small, and a file that lost half of itself looks the
same in a file manager. This reads the archive, prints its entries grouped by kind with the
unpacked size of each group, counts the classes in the compiled code, and fails when a
component named in the manifest is absent.
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

PREFIX = 'Lcom/naqaa/app/'


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


def read_length(data, position):
    """Reads the variable width length that starts a string entry."""
    value, shift = 0, 0
    while True:
        piece = data[position]
        position += 1
        value |= (piece & 0x7F) << shift
        if not piece & 0x80:
            return value, position
        shift += 7


def descriptors(data):
    """Reads the type descriptors out of one dex file's string table."""
    count, offset = struct.unpack_from('<II', data, 56)
    found = set()
    for index in range(count):
        start = struct.unpack_from('<I', data, offset + index * 4)[0]
        length, position = read_length(data, start)
        text = data[position:position + length].decode('utf-8', 'replace')
        if text.startswith('L') and text.endswith(';'):
            found.add(text)
    return found


def main():
    if len(sys.argv) != 2:
        print('usage: check_apk.py <file.apk>')
        return 2
    path = sys.argv[1]
    sizes, counts = {}, {}
    classes, mine, assets, sounds, blocked = 0, 0, 0, 0, False
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
                mine += len([name for name in descriptors(data) if name.startswith(PREFIX)])
            elif group == ASSETS:
                assets += 1
                if entry.filename.endswith('blocked-domains.txt.gz'):
                    blocked = True
            elif group == SOUND:
                sounds += 1

    missing = [name for name in COMPONENTS if name.encode() not in code]
    print(f'  contents: {len(entries)} entries, {human(sum(sizes.values()))} unpacked')
    for group, size in sorted(sizes.items(), key=lambda item: -item[1]):
        detail = ''
        if group == CODE:
            detail = f', {classes} classes, {mine} from this application'
        elif group == ASSETS:
            detail = ', of which the blocked list' if blocked else ''
            detail += f' and {assets - (1 if blocked else 0)} content file(s)'
        elif group == SOUND:
            detail = ', the reminder sound'
        print(f'    {group}: {human(size)}, {counts[group]} file(s){detail}')
    if missing:
        print(f'  components missing: {", ".join(missing)}')
        return 1
    print('  components: present')
    return 0


if __name__ == '__main__':
    sys.exit(main())
