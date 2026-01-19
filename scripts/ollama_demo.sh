#!/bin/bash
# Day 25: Ollama Local LLM Demo

set -e

echo "╔════════════════════════════════════════════════════════╗"
echo "║       🤖 Ollama Local LLM Demo (Day 25)                 ║"
echo "╚════════════════════════════════════════════════════════╝"

# Check if Ollama is installed
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama not installed. Install with: brew install ollama"
    exit 1
fi

# Check if Ollama is running
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "⏳ Starting Ollama service..."
    brew services start ollama 2>/dev/null || ollama serve &
    sleep 3
fi

echo ""
echo "📦 Installed models:"
ollama list
echo ""

# Check if model exists
MODEL="qwen2.5:0.5b"
if ! ollama list | grep -q "$MODEL"; then
    echo "⏳ Downloading model $MODEL..."
    ollama pull $MODEL
fi

echo "═══════════════════════════════════════════════════════"
echo "🔤 Test 1: CLI Interaction"
echo "═══════════════════════════════════════════════════════"
echo "Question: Скажи привет на русском"
echo "Answer:"
ollama run $MODEL "Скажи привет на русском. Ответь одним предложением." 2>/dev/null | head -5

echo ""
echo "═══════════════════════════════════════════════════════"
echo "🌐 Test 2: API - Generate Endpoint"
echo "═══════════════════════════════════════════════════════"
echo "Prompt: The sky is"
RESPONSE=$(curl -s http://localhost:11434/api/generate -d "{
  \"model\": \"$MODEL\",
  \"prompt\": \"The sky is\",
  \"stream\": false
}" | jq -r '.response' 2>/dev/null || echo "Error")
echo "Response: $RESPONSE"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "💬 Test 3: API - Chat Endpoint"
echo "═══════════════════════════════════════════════════════"
echo "Message: What is the capital of Japan?"
RESPONSE=$(curl -s http://localhost:11434/api/chat -d "{
  \"model\": \"$MODEL\",
  \"messages\": [{\"role\": \"user\", \"content\": \"What is the capital of Japan? Answer in one word.\"}],
  \"stream\": false
}" | jq -r '.message.content' 2>/dev/null || echo "Error")
echo "Response: $RESPONSE"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "📊 Test 4: Model Info"
echo "═══════════════════════════════════════════════════════"
curl -s http://localhost:11434/api/show -d "{\"name\": \"$MODEL\"}" | jq '{
  modelfile: .modelfile[0:100],
  parameters: .parameters,
  template: .template[0:50]
}' 2>/dev/null || echo "Model info not available"

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║  ✅ Demo Complete!                                      ║"
echo "║                                                         ║"
echo "║  Ollama API: http://localhost:11434                     ║"
echo "║  Model: $MODEL                                  ║"
echo "╚════════════════════════════════════════════════════════╝"
