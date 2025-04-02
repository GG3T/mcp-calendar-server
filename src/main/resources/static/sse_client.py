#!/usr/bin/env python3
"""
Script para testar a conexão SSE com autenticação por parâmetro de URL.
Este script requer a biblioteca 'sseclient-py'.

Instalação:
    pip install sseclient-py

Uso:
    python sse_client.py <email>

Exemplo:
    python sse_client.py chatgmia@gmail.com

"""

import sys
import time
import json
from sseclient import SSEClient

def main():
    if len(sys.argv) != 2:
        print("Uso: python sse_client.py <email>")
        sys.exit(1)
    
    email = sys.argv[1]
    base_url = "http://localhost:3500" # Ajuste conforme necessário
    
    # URL com o parâmetro de email
    url = f"{base_url}/sse?email={email}"
    
    print(f"Conectando ao endpoint SSE: {url}")
    print("Aguardando eventos... (Ctrl+C para sair)")
    print("-" * 60)
    
    try:
        # Estabelece a conexão SSE
        client = SSEClient(url)
        
        # Processa os eventos recebidos
        for event in client:
            if event.event == "connect":
                print(f"Conexão estabelecida!")
            
            try:
                data = json.loads(event.data)
                print(f"Evento: {event.event}")
                print(f"Dados: {json.dumps(data, indent=2, ensure_ascii=False)}")
                print("-" * 60)
            except json.JSONDecodeError:
                print(f"Evento: {event.event}")
                print(f"Dados (não-JSON): {event.data}")
                print("-" * 60)
    
    except KeyboardInterrupt:
        print("\nConexão encerrada pelo usuário.")
    except Exception as e:
        print(f"\nErro: {str(e)}")
    
if __name__ == "__main__":
    main()
