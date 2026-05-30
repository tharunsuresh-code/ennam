#!/bin/bash
# Download Gemma 2B Q4_K_M for Ennam prototype testing
set -e

MODEL_DIR="$(cd "$(dirname "$0")" && pwd)"
MODEL_FILE="$MODEL_DIR/gemma-2-2b-it-Q4_K_M.gguf"
MODEL_URL="https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"

if [ -f "$MODEL_FILE" ]; then
    echo "✓ Model already exists: $MODEL_FILE ($(du -h "$MODEL_FILE" | cut -f1))"
    exit 0
fi

echo "Downloading Gemma 2B Q4_K_M (~1.5 GB)..."
echo "URL: $MODEL_URL"
echo ""
curl -L --progress-bar -o "$MODEL_FILE" "$MODEL_URL"
echo ""
echo "✓ Downloaded to: $MODEL_FILE ($(du -h "$MODEL_FILE" | cut -f1))"
