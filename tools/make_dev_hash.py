#!/usr/bin/env python3
"""Prints the three constants of the hidden developer account (see DevAccount in UserStore.kt).

Usage: python3 tools/make_dev_hash.py <username> <password>
The password is never written anywhere; only its salted PBKDF2 hash goes into the source.
"""
import base64
import hashlib
import secrets
import sys

ITERATIONS = 200_000  # must match UserStore.kt

if len(sys.argv) != 3:
    sys.exit(__doc__)
user, password = sys.argv[1], sys.argv[2]
salt = secrets.token_bytes(16)
digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, ITERATIONS, 32)
print('USER_SHA256 =', hashlib.sha256(user.lower().encode()).hexdigest())
print('SALT        =', base64.b64encode(salt).decode())
print('HASH        =', base64.b64encode(digest).decode())
