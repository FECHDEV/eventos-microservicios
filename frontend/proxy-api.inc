# Encabezados de cada location que reenvía /api al gateway (nginx.conf)
proxy_set_header Host $host;
# La IP real del cliente: el gateway la pasa a los límites de intentos
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Real-IP $remote_addr;
