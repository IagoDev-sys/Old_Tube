# OLD Tube Bridge API (Companheiro para Android 4.4)

Servidor HTTP leve, sem dependências externas (`node >= 18`), para resolver fluxos H.264 MP4 progressivos e servir como ponte de streaming para tablets Android 4.4 KitKat.

## Como rodar

```bash
node bridge-api/server.mjs
```

Por padrão escuta na porta `8787`.

## Endpoints

- `GET /health` e `GET /api/v1/stats` — verificação de disponibilidade
- `GET /api/v1/videos/:videoId` — retorna metadados + `formatStreams` MP4 H.264 compatível com o OLD Tube
- `GET /stream/:videoId` — retransmissão direta de MP4 com suporte a `Range` (seek) para `VideoView` no Android 4.4

## Uso no Android 4.4

1. Em rede Wi-Fi local, habilite **Permitir Ponte na Rede Local** em **Modo Tab E** no app e salve `http://IP_DO_SERVIDOR:8787`.
2. Para acesso remoto pela internet, publique atrás de um túnel HTTPS (Cloudflare Tunnel, Caddy ou Nginx) e informe `https://seu-dominio`.
