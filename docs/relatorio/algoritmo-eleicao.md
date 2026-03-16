# Algoritmo de eleicao implementado

## Estrategia

Foi implementada uma eleicao por prioridade no gateway:

1. primaria (quando disponivel)
2. replica1 (prioridade 1)
3. replica2 (prioridade 2)

## Processo de decisao

1. O gateway verifica `GET /health` do lider atual.
2. Se o lider estiver indisponivel (timeout ou erro HTTP), inicia eleicao.
3. Durante eleicao:
   - tenta restaurar a primaria se ela estiver saudavel;
   - se a primaria estiver fora, escolhe a primeira replica saudavel por prioridade.
4. O gateway promove o vencedor com `POST /role` (`PRIMARY`) e define os demais como `REPLICA`.

## Sincronizacao da primaria antiga

Quando a primaria antiga volta:

1. O gateway identifica que ela esta novamente saudavel.
2. Busca snapshot no lider atual com `GET /messages`.
3. Envia snapshot para a primaria antiga via `POST /sync`.
4. Define a primaria antiga como `REPLICA` via `POST /role`.

Assim, o no que retornou nao sobrescreve o estado atual do cluster.
