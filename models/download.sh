# Download Qwen2.5-1.5B-Instruct for Ennam prototype testing
set -e

MODEL_DIR="$(cd "$(dirname "$0")" && pwd)"
MODEL_FILE="$MODEL_DIR/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
MODEL_URL="https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"

if [ -f "$MODEL_FILE" ]; then
    echo "✓ Model already exists: $MODEL_FILE ($(du -h "$MODEL_FILE" | cut -f1))"
    exit 0
fi

echo "Downloading Qwen2.5-1.5B-Instruct Q4_K_M (~1 GB)..."
echo "URL: $MODEL_URL"
echo ""
curl -L --progress-bar -o "$MODEL_FILE" "$MODEL_URL"
echo ""
echo "✓ Downloaded to: $MODEL_FILE ($(du -h "$MODEL_FILE" | cut -f1))"
