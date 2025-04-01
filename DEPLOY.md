# Guia de Implantação (Deploy) do MCP Calendar Server

Este documento contém as instruções para implantar o MCP Calendar Server no Portainer com Traefik.

## Pré-requisitos

1. Docker e Docker Compose instalados no servidor
2. Portainer configurado
3. Traefik configurado com Let's Encrypt para SSL
4. Rede Docker externa "ChatgmNET" já criada

## Passos para Implantação

### 1. Construir o JAR do aplicativo

```bash
# Na máquina de desenvolvimento
mvn clean package -DskipTests
```

Este comando criará um arquivo JAR em `target/mcp-server-0.1.0.jar`

### 2. Construir a imagem Docker

```bash
# Na máquina de desenvolvimento
docker build -t gg3t/mcp-calendar-service:0.1.0 .
```

### 3. Enviar a imagem para o Docker Hub

```bash
# Na máquina de desenvolvimento
docker push gg3t/mcp-calendar-service:0.1.0
```

### 4. Implantar no Portainer

1. Abra a interface web do Portainer
2. Navegue até "Stacks" (Pilhas)
3. Clique em "Add Stack" (Adicionar Pilha)
4. Dê um nome à pilha (ex: "mcp-calendar")
5. Cole o conteúdo do arquivo `docker-compose.yml` no editor
6. Clique em "Deploy the stack" (Implantar a pilha)

## Verificação

Após a implantação, você pode verificar se o serviço está funcionando corretamente:

1. Abra um navegador e acesse: `https://mcp-calendar.chatgmia.org/static/sse-test.html`
2. Insira um email válido do seu banco de dados
3. Clique em "Conectar"
4. Verifique se a conexão SSE é estabelecida e os dados de calendário são exibidos

## Solução de Problemas

Se você encontrar problemas durante a implantação:

1. **Verifique os logs do contêiner**:
   No Portainer, navegue até a pilha "mcp-calendar", clique no contêiner e veja os logs.

2. **Verifique as configurações de rede**:
   Certifique-se de que a rede "ChatgmNET" existe e que o Traefik está corretamente configurado.

3. **Verifique o registro DNS**:
   Confirme que o registro DNS para `mcp-calendar.chatgmia.org` está apontando para o endereço IP correto.

4. **Verifique as configurações do SSL**:
   Certifique-se de que o Traefik está configurado corretamente para obter certificados SSL do Let's Encrypt.

5. **Consulte os logs do Traefik**:
   Os logs do Traefik podem conter informações úteis sobre problemas de roteamento ou SSL.

## Atualização

Para atualizar o serviço para uma nova versão:

1. Construa um novo JAR e uma nova imagem Docker
2. Atualize a tag da versão no `docker-compose.yml`
3. Reimplante a pilha no Portainer
