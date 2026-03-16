# Sistema Distribuido Cliente-Servidor

Projeto academico em Java + Spring Boot para demonstrar:
- replicacao de dados em multiplos nos;
- deteccao de falha da primaria;
- eleicao automatica de lider via gateway;
- reintegracao da primaria antiga com sincronizacao.

## Sumario
- [Arquitetura](#arquitetura)
- [Tecnologias](#tecnologias)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Como Executar](#como-executar)
- [Cliente de Console](#cliente-de-console)
- [Endpoints](#endpoints)
- [Roteiro de Testes](#roteiro-de-testes)
- [Persistencia](#persistencia)
- [Documentacao](#documentacao)

## Arquitetura

Fluxo principal:

`ClientApp -> Gateway -> Lider ativo -> Replicas`

Componentes:
- `client-app`: cliente de console HTTP.
- `gateway-service`: roteamento, health check e eleicao de lider.
- `server-primaria`: no inicial lider (porta 8081).
- `server-replica1`: replica de prioridade 1 (porta 8082).
- `server-replica2`: replica de prioridade 2 (porta 8083).

Politica de eleicao:
- Primaria ativa enquanto estiver saudavel.
- Em falha da primaria: `replica1` vira lider; se indisponivel, `replica2`.
- Quando a primaria antiga retorna: sincroniza dados e volta como `REPLICA`.

## Tecnologias
- Java 21
- Spring Boot
- Gradle Wrapper
- PowerShell (scripts/exemplos de execucao)

## Estrutura do Projeto

```text
distributed-system/
├── client-app/
├── gateway-service/
├── server-primaria/
├── server-replica1/
├── server-replica2/
└── docs/
```

## Como Executar

Prerequisitos:
- Java 21 instalado e no `PATH`.

Opcional (recomendado no Windows para evitar lock no cache global):

```powershell
$env:GRADLE_USER_HOME="C:\Users\Windows\Desktop\distributed-system\.gradle-cache"
```

Suba cada servico em um terminal separado:

```powershell
# Terminal 1 - Primaria (8081)
cd server-primaria
.\gradlew.bat bootRun --console=plain
```

```powershell
# Terminal 2 - Replica1 (8082)
cd server-replica1
.\gradlew.bat bootRun --console=plain
```

```powershell
# Terminal 3 - Replica2 (8083)
cd server-replica2
.\gradlew.bat bootRun --console=plain
```

```powershell
# Terminal 4 - Gateway (8080)
cd gateway-service
.\gradlew.bat bootRun --console=plain
```

Verificacao rapida:

```powershell
Invoke-RestMethod http://localhost:8080/gateway/health | ConvertTo-Json -Depth 10
Invoke-RestMethod http://localhost:8081/health
Invoke-RestMethod http://localhost:8082/health
Invoke-RestMethod http://localhost:8083/health
```

## Cliente de Console

Rodar o cliente com console interativo:

```powershell
cd client-app
.\gradlew.bat bootRun --args="--client.console.enabled=true" --console=plain
```

Comandos no cliente:
- `/health` consulta status do gateway.
- `/exit` encerra o cliente.
- qualquer outro texto envia mensagem para `/gateway/forward`.

## Endpoints

### Gateway (`localhost:8080`)
- `GET /gateway/health`: status do cluster e lider atual.
- `GET /gateway/leader`: lider atual.
- `POST /gateway/forward`: envia payload para o lider ativo.

Exemplo:

```powershell
Invoke-RestMethod -Method POST `
  -Uri http://localhost:8080/gateway/forward `
  -ContentType "application/json" `
  -Body '{"payload":"mensagem de teste"}'
```

### Nos de dados (`8081`, `8082`, `8083`)
- `GET /health`: status + `nodeId` + `role`.
- `GET /role`: papel atual do no.
- `POST /role`: altera papel (`PRIMARY` ou `REPLICA`).
- `POST /save`: grava no lider ativo.
- `POST /replicate`: recebe replicacao.
- `GET /messages`: lista mensagens do no.
- `POST /sync`: substitui snapshot local.

## Roteiro de Testes

### Teste 1 - Sistema normal
1. Com todos os servicos ativos, envie mensagem para o gateway.
2. Verifique a mesma mensagem na primaria e nas duas replicas.

### Teste 2 - Derrubar primaria
1. Descubra PID da porta 8081: `netstat -ano | findstr :8081`
2. Mate o processo: `Stop-Process -Id <PID> -Force`
3. Valide `GET /gateway/leader`: deve eleger `replica1`.

### Teste 3 - Enviar mensagem apos failover
1. Envie nova mensagem em `/gateway/forward`.
2. Sistema deve responder com sucesso, mesmo com a primaria fora.

### Teste 4 - Retorno da primaria
1. Suba novamente `server-primaria`.
2. Gere novo ciclo no gateway (health/forward).
3. Verifique:
   - `GET /8081/role` -> `REPLICA`
   - `GET /8081/messages` sincronizado com o lider.

## Persistencia

Arquivos de banco local (TXT):
- `server-primaria/data/database.txt`
- `server-replica1/data/database-replica1.txt`
- `server-replica2/data/database-replica2.txt`

Formato salvo por linha:

`id|createdAt|payloadBase64`

## Documentacao
- Arquitetura: `docs/arquitetura/estrutura.md`
- Relatorio de eleicao: `docs/relatorio/algoritmo-eleicao.md`
