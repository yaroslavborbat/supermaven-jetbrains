#!/bin/bash

WORK_DIR="$1"
SM_AGENT="$2"
SM_AGENT_ARGS=("${@:3}")
SM_DIR="$HOME/.supermaven"

if [[ -z "$WORK_DIR" ]]; then
    echo "WORK_DIR is not set"
    exit 1
fi
if [[ -z "$SM_AGENT" ]]; then
    echo "SM_AGENT is not set"
    exit 1
fi

landrun --log-level debug       \
        --unrestricted-network  \
        --ro "$WORK_DIR"        \
        --rox "$SM_AGENT"       \
        --rwx "$SM_DIR"         \
        --ro /etc/ssl           \
        --ro /etc/pki           \
        --ro /run               \
        "$SM_AGENT" "${SM_AGENT_ARGS[@]}"
