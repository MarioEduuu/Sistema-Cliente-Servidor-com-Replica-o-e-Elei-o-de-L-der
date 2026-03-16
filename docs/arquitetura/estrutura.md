## Arquitetura final

ClientApp -> Gateway -> Lider ativo
                        |       \
                        |        \
                     Replica1   Replica2

## Componentes

- `client-app`: envia `POST /gateway/forward` com o payload da mensagem.
- `gateway-service`: monitora saude dos nos e decide para qual lider encaminhar.
- `server-primaria`: inicia como `PRIMARY` e replica para os outros dois nos.
- `server-replica1`: inicia como `REPLICA`, prioridade 1 de eleicao.
- `server-replica2`: inicia como `REPLICA`, prioridade 2 de eleicao.

## Endpoints padronizados (todos os nos)

- `POST /save`
- `POST /replicate`
- `GET /messages`
- `POST /sync`
- `GET /health`
- `GET /role`
- `POST /role`
