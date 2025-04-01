import requests
import json
import time
import sys
import os
from datetime import datetime

def sse_client(url, email):
    """
    Cliente SSE para teste do endpoint do Google Calendar MCP
    
    Args:
        url: URL do endpoint SSE
        email: Email para usar no cabeçalho da requisição
    """
    headers = {
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'email': email
    }
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Conectando ao SSE em {url}")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Usando email: {email}")
    print("-" * 70)
    
    try:
        # Fazendo a requisição com stream=True para processar a resposta por chunks
        response = requests.get(url, headers=headers, stream=True)
        
        if response.status_code != 200:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] ERRO: Código de status HTTP {response.status_code}")
            print(response.text)
            return
            
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Conexão estabelecida com sucesso!")
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Aguardando eventos...")
        print("-" * 70)
        
        # Variáveis para processamento de eventos SSE
        buffer = ""
        event_type = "message"
        event_data = ""
        in_event = False
        
        # Loop para ler os chunks da resposta
        for chunk in response.iter_content(chunk_size=1):
            if chunk:
                chunk_str = chunk.decode('utf-8')
                buffer += chunk_str
                
                # Processa linhas completas
                if buffer.endswith('\n'):
                    line = buffer.strip()
                    buffer = ""
                    
                    # Pula linhas vazias
                    if not line:
                        if in_event:
                            # Evento completo
                            in_event = False
                            
                            try:
                                # Tenta formatar o JSON para exibição
                                try:
                                    data_json = json.loads(event_data)
                                    formatted_data = json.dumps(data_json, indent=2)
                                except:
                                    formatted_data = event_data
                                
                                print(f"[{datetime.now().strftime('%H:%M:%S')}] EVENTO: {event_type}")
                                print(f"DADOS: {formatted_data}")
                                print("-" * 70)
                            except Exception as e:
                                print(f"[{datetime.now().strftime('%H:%M:%S')}] ERRO ao processar evento: {e}")
                            
                            # Reseta para o próximo evento
                            event_type = "message"
                            event_data = ""
                        continue
                    
                    in_event = True
                    
                    # Processa os campos do evento SSE
                    if line.startswith('event:'):
                        event_type = line[6:].strip()
                    elif line.startswith('data:'):
                        event_data = line[5:].strip()
    
    except KeyboardInterrupt:
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Teste interrompido pelo usuário")
    except Exception as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Erro: {e}")
    finally:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Encerrando conexão SSE")

if __name__ == "__main__":
    # Configurações padrão
    default_url = "http://localhost:3500/sse"
    default_email = "chatgmia@gmail.com"
    
    # Permite sobrescrever via argumentos de linha de comando
    url = sys.argv[1] if len(sys.argv) > 1 else default_url
    email = sys.argv[2] if len(sys.argv) > 2 else default_email
    
    sse_client(url, email)
