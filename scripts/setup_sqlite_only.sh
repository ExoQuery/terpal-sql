#!/usr/bin/env bash

# Convenience script to just setup/bounce the sqlite database

set -e

# import setup functions
. scripts/setup_db_scripts.sh

time setup_sqlite

echo "Sqlite is Ready!"
