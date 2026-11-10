# HU-22: desplegar en el VPS nuevo

No es un caso de uso: publica el sistema completo. Decisiones en `docs/decisiones.md` →
«Proveedor de pago obligatorio en Docker» y «Límites de nginx y HTTPS con Caddy en el servidor».

## Tareas

### Preparación, en local (hecha)

1. **Proveedor de pago obligatorio** en `docker-compose.yml`: sin `EVENTOS_PAGO_PROVEEDOR`, el compose
   no arranca.
2. **Límites en nginx** para `/api/visitas/` y `/api/pagos/aviso`, con la IP real detrás de Caddy
   (`real_ip` solo desde la red de Docker).
3. **`docker-compose.nube.yml` y `nube/Caddyfile`**: Caddy publica 80 y 443, con HTTPS para el sitio
   y para `correo.SARAO_DOMINIO` (webmail), y fija `EVENTOS_SITIO_URL`.

4. **Despliegue de un comando**, porque el VPS es de prueba, se cobra por hora y cambia de IP:
   - `.env.servidor` (git lo ignora): lo único que no se genera, cargado por el usuario. Son el
     correo y la contraseña del Administrador, la appkey y el correo de Libélula, y opcionalmente el
     dominio de los buzones y su lista.
   - `scripts/instalar-servidor.sh`, adaptado del sistema base, corre en el VPS. Arma el `.env` con
     claves nuevas al azar, `libelula`, `smtp` y el dominio de la IP en sslip.io. Después pone swap,
     Docker y firewall (22, 80 y 443), levanta todo con Caddy, espera al Administrador y al
     certificado, y crea los buzones.
   - `scripts/subir-al-vps.sh IP [rama]` lo lanza desde la PC. Por SSH como root, clona el
     repositorio público en `/opt/sarao` y copia `.env.servidor`. La IP no queda en ningún archivo.
   - El buzón de correo es `mail.com` por defecto: el correo nunca sale del servidor
     (`correo/postfix-main.cf`).

### En el VPS (falta: lo crea el usuario)

5. **Datos del usuario:** `.env.servidor` lleno, y un VPS Ubuntu con su llave SSH.
6. **Desplegar:** `bash scripts/subir-al-vps.sh IP`.
7. **Si algo falla:** `docker compose -f docker-compose.yml -f docker-compose.nube.yml --profile
   correo logs` en `/opt/sarao`.
8. **Probar en el servidor:** el recorrido de `scripts/prueba-completa.sh` a mano por HTTPS, una
   compra real de Bs 0,10 con Libélula (el aviso llega a `/api/pagos/aviso`), la cámara del celular
   en la puerta y los correos en los buzones.
9. **Medir** el costo de la conciliación de cada minuto con Libélula.

## Resultado de la preparación

- **Prueba local de `docker-compose.nube.yml`** (Caddy con certificado interno para `localhost`):
  sin proveedor de pago el compose no arranca; solo Caddy publica puertos; el catálogo y el webmail
  responden por HTTPS y HTTP redirige a HTTPS. 40 visitas seguidas dieron 36 atendidas y 4 con 429,
  igual cambiando `X-Forwarded-For` en cada una; 20 avisos de pago, 14 y 6. Sin errores en los logs.
