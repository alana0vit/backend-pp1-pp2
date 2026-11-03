# Chatbot (RAG) — ConectaPro

`POST /api/chat` — público (sem login), limitado por IP.

**Request**
```json
{ "message": "meu chuveiro queimou", "history": [ { "role": "user", "content": "..." }, { "role": "assistant", "content": "..." } ] }
```
`message`: obrigatório, até 500 caracteres. `history`: opcional, até 6 mensagens (role `user` ou `assistant`); o servidor não guarda conversa, o front reenvia as últimas trocas.

**Response 200**
```json
{ "answer": "...", "suggestedCategory": "Eletricista", "sources": ["Categoria Eletricista (...)"], "found": true }
```
- `suggestedCategory`: nome da categoria (use para um botão "Ver Eletricistas") ou `null`.
- `found=false`: nada na base cobre a pergunta (resposta padrão "não encontrei").
- **429**: limite de uso (`{status, message}`) — mostrar a mensagem ao usuário.

## Como funciona
1. A pergunta é buscada (BM25, sem acento/plural) nos guias de `src/main/resources/kb/*.md`.
2. Os 3 trechos mais relevantes vão como contexto para o LLM (Cloudflare Workers AI).
3. Abaixo da nota mínima (`chat.min-score`, 3.0) o LLM nem é chamado: responde "não encontrei" (+ categoria mais próxima, se houver).
4. Sem token configurado, ou se a Cloudflare falhar, responde com o texto do guia (sem LLM).

## Configuração (.env — nunca commitar)
```
CLOUDFLARE_AI_API_TOKEN=...      # token com permissão Workers AI (não é o token do R2)
CLOUDFLARE_AI_ACCOUNT_ID=...     # opcional; por padrão usa R2_ACCOUNT_ID
```
Criar o token: Cloudflare Dashboard → AI → Workers AI → "Use REST API" → "Create a Workers AI API Token".
Opcionais: `CLOUDFLARE_AI_MODEL` (padrão `@cf/meta/llama-3.1-8b-instruct`), `CHAT_RATE_LIMIT_PER_CLIENT` (20/hora), `CHAT_RATE_LIMIT_PER_DAY` (2000), `CHAT_TRUST_FORWARDED_HEADER=true` (só atrás de proxy confiável).

## Editar o que o bot sabe
Cada guia é um `.md` em `src/main/resources/kb/`:
```
titulo: Título do guia
categoria: Eletricista        (opcional — só nas ffichas de categoria; precisa ser o nome exato)
palavras: sinônimos e jeitos de o usuário falar o assunto

Texto do guia (100–200 palavras).
```
O campo `palavras:` é o que mais melhora a busca. Se o bot errar uma pergunta, acrescente as palavras que faltaram.
Se mudar preço/taxa de plano, atualize `planos-assinatura.md`, `pagamento-taxa.md` e `sou-profissional.md`.
