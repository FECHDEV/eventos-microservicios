<?php
// Roundcube envía por el puerto 25 del servidor de correo, que acepta envíos sin contraseña desde la red
// interna de Docker (PERMIT_DOCKER) y no ofrece AUTH. Sin esto, Roundcube intenta autenticarse con el buzón
// de quien escribe y el envío falla. Igual que la API; el servidor solo entrega a los buzones del dominio.
$config['smtp_user'] = '';
$config['smtp_pass'] = '';
