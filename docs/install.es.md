# Guía de instalación

[English](install.md) | **Español**

Esta guía es para ti si nunca has usado Docker, git ni un terminal. Te lleva paso a paso desde
cero hasta tener poker-bankroll abierto en el navegador, en Windows, macOS o Linux. En cada paso
se dice qué deberías ver si ha ido bien. La primera vez calcula entre 30 y 60 minutos, casi todo
esperando a que terminen las descargas.

**Qué necesitas**

- Un ordenador con Windows 10 u 11 (de 64 bits), un macOS reciente (una de las tres últimas
  versiones) o Linux, con al menos 4 GB de memoria y 3 GB libres en el disco.
- Conexión a internet, para descargar Docker y la aplicación.
- Permiso para instalar programas en ese ordenador (una cuenta de administrador).

**Cómo funciona, en dos frases.** poker-bankroll se ejecuta dentro de **Docker**, un programa
gratuito que ejecuta otros programas en cajas cerradas llamadas *contenedores*, así que no tienes
que instalar nada más. Tus datos los guarda Docker, aparte de los ficheros que descargas:
actualizar la aplicación o reiniciar el ordenador no los toca.

**Contenido**

1. [Instala Docker](#1-instala-docker)
2. [Descarga poker-bankroll](#2-descarga-poker-bankroll)
3. [Abre un terminal en la carpeta](#3-abre-un-terminal-en-la-carpeta)
4. [Elige la contraseña de la base de datos](#4-elige-la-contraseña-de-la-base-de-datos)
5. [Arráncala](#5-arráncala)
6. [Pararla, volver a arrancarla y que arranque con el ordenador](#6-pararla-volver-a-arrancarla-y-que-arranque-con-el-ordenador)
7. [Actualizar a una versión nueva](#7-actualizar-a-una-versión-nueva)
8. [Dónde están tus datos y tus copias de seguridad](#8-dónde-están-tus-datos-y-tus-copias-de-seguridad)
9. [Úsala desde el móvil o desde otro ordenador](#9-úsala-desde-el-móvil-o-desde-otro-ordenador)
10. [A qué se conecta en internet](#10-a-qué-se-conecta-en-internet)
11. [Problemas frecuentes](#11-problemas-frecuentes)
12. [Desinstalarla](#12-desinstalarla)

## 1. Instala Docker

Sigue la parte de tu sistema y luego pasa al paso 2.

### Windows

1. Entra en [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
   y pulsa **Download for Windows – AMD64**. (Elige ARM64 solo si tu ordenador tiene un
   procesador ARM, como un Snapdragon; casi todos los PC son AMD64.)
2. Abre el fichero descargado, `Docker Desktop Installer.exe`. Windows pregunta si permites que
   haga cambios: pulsa **Sí**.
3. En la pantalla de configuración, deja marcada la opción **Use WSL 2 instead of Hyper-V** y
   pulsa **OK**.
   *Deberías ver:* la instalación durante unos minutos y después *Installation succeeded*.
4. Pulsa **Close and restart** (o reinicia tú el ordenador si el botón solo dice *Close*).
5. Después de reiniciar, abre **Docker Desktop** desde el menú Inicio si no se ha abierto solo.
   Acepta el acuerdo de servicio (es gratuito para uso personal). Si te pide iniciar sesión o
   rellenar una encuesta, puedes saltarte las dos cosas.
6. Si aparece una ventana diciendo que hay que instalar o actualizar **WSL**, deja que lo haga
   (o mira [Windows: mensajes de WSL y de virtualización](#windows-mensajes-de-wsl-y-de-virtualización))
   y vuelve a reiniciar.

*Deberías ver:* la ventana de Docker Desktop con **Engine running** abajo a la izquierda, y el
icono de una ballena junto al reloj (puede estar escondido tras la flecha **^**).

### macOS

1. Averigua qué tipo de Mac tienes: menú Apple  → **Acerca de este Mac**. Si pone *Chip: Apple
   M1* (M2, M3...) es **Apple silicon**; si pone *Procesador: ... Intel* es **Intel**.
2. Entra en [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
   y descarga Docker Desktop para **Mac with Apple silicon** o **Mac with Intel chip**, según lo
   que hayas visto.
3. Abre el `Docker.dmg` descargado y arrastra el icono de Docker a la carpeta **Aplicaciones**.
4. Abre **Docker** desde Aplicaciones (o desde el Launchpad). Si macOS pregunta si seguro que
   quieres abrir una aplicación descargada de internet, pulsa **Abrir**.
5. Acepta el acuerdo de servicio (es gratuito para uso personal), elige **Use recommended
   settings** y escribe la contraseña de tu Mac cuando te la pida. Si te pide iniciar sesión o
   rellenar una encuesta, puedes saltarte las dos cosas.

*Deberías ver:* la ventana de Docker Desktop con **Engine running** abajo a la izquierda, y el
icono de una ballena en la barra de menús, arriba en la pantalla.

### Linux

En Linux se instala **Docker Engine**, la versión sin ventanas (si lo prefieres, Docker Desktop
para Linux también sirve). Vas a escribir unos pocos comandos: abre la aplicación **Terminal** de
tu sistema y escribe o pega cada línea, y pulsa Intro (en un terminal de Linux, pegar es
**Ctrl+Mayús+V**).

1. Instala Docker con su script oficial (te pide tu contraseña; mientras la escribes no aparece
   nada, es normal):

   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   ```

   *Deberías ver:* muchas líneas pasando durante uno o dos minutos, sin la palabra `ERROR` al
   final. Si el script no es compatible con tu distribución, sigue la página de la tuya en las
   [instrucciones oficiales](https://docs.docker.com/engine/install/).

2. Permite que tu usuario use Docker sin `sudo` y después **cierra la sesión y vuelve a
   entrar** (o reinicia):

   ```bash
   sudo usermod -aG docker $USER
   ```

3. Haz que Docker arranque con el ordenador:

   ```bash
   sudo systemctl enable --now docker
   ```

4. Comprueba que funciona:

   ```bash
   docker run --rm hello-world
   ```

   *Deberías ver:* un mensaje que empieza por **Hello from Docker!**

## 2. Descarga poker-bankroll

Necesitas dos ficheros: `docker-compose.yml` (qué hay que ejecutar) y `.env` (tu configuración).
Los dos vienen en un zip con cada versión.

1. Abre la [última versión](https://github.com/kete1987/poker-bankroll/releases/latest) de
   poker-bankroll.
2. En **Assets**, abajo del todo (pulsa encima si está plegado), pulsa
   **poker-bankroll-X.Y.Z.zip**, donde `X.Y.Z` es la versión, por ejemplo `0.2.0`. No cojas los
   ficheros *Source code*: son para programadores.
3. Descomprímelo en tu carpeta **Documentos**:
   - **Windows:** en *Descargas*, botón derecho sobre el zip → **Extraer todo...** → en la
     casilla, pon tu carpeta Documentos (por ejemplo `C:\Users\Ana\Documents`) → **Extraer**.
   - **macOS:** haz doble clic en el zip, en *Descargas* (puede que Safari ya lo haya
     descomprimido), y arrastra la carpeta `poker-bankroll` a *Documentos* en la barra lateral
     del Finder.
   - **Linux:** botón derecho sobre el zip → **Extraer aquí**, y mueve la carpeta
     `poker-bankroll` a *Documentos*.

*Deberías ver:* una carpeta **Documentos/poker-bankroll** con `docker-compose.yml` y `.env`
dentro. En macOS y en Linux solo se ve `docker-compose.yml`: los ficheros cuyo nombre empieza
por un punto están ocultos, pero `.env` está ahí (**Cmd+Mayús+.** en el Finder, o **Ctrl+H** en
el gestor de archivos de Linux, los muestra).

Esa carpeta es ahora tu instalación: usa siempre la misma. No ejecutes la aplicación desde
dentro del zip ni muevas la carpeta mientras la aplicación está en marcha.

## 3. Abre un terminal en la carpeta

El terminal es una ventana en la que se escriben comandos. Todos los comandos de esta guía se
escriben en un terminal **abierto en la carpeta poker-bankroll**: escríbelo (o pégalo) y pulsa
**Intro**.

- **Windows 11:** abre la carpeta `poker-bankroll` en el Explorador de archivos, botón derecho
  sobre un espacio vacío → **Abrir en Terminal**.
  **Windows 10:** abre la carpeta, pulsa en la barra de direcciones de arriba, escribe
  `powershell` y pulsa Intro.
  *Deberías ver:* una ventana con una línea como `PS C:\Users\Ana\Documents\poker-bankroll>`.
  Para pegar, **Ctrl+V**.
- **macOS:** abre **Terminal** (pulsa **Cmd+Espacio**, escribe `Terminal` y pulsa Intro).
  Escribe `cd` seguido de un espacio, **arrastra la carpeta poker-bankroll** desde el Finder a la
  ventana del Terminal y pulsa Intro.
  *Deberías ver:* una línea que termina en `poker-bankroll %`. Para pegar, **Cmd+V**.
- **Linux:** abre la carpeta en el gestor de archivos, botón derecho sobre un espacio vacío →
  **Abrir en un terminal**.
  *Deberías ver:* una línea que termina en `poker-bankroll$`. Para pegar, **Ctrl+Mayús+V**.

Para comprobar que estás en el sitio correcto, escribe `ls` y pulsa Intro: en la lista tiene que
salir `docker-compose.yml`.

## 4. Elige la contraseña de la base de datos

La aplicación guarda tus datos en una base de datos protegida con una contraseña. La eliges una
vez, aquí, y no tendrás que escribirla en ningún otro sitio: **no** es una contraseña para entrar
en la aplicación (la aplicación no tiene).

1. Abre el fichero `.env` con un editor de texto plano:
   - **Windows:** escribe `notepad .env` en el terminal. Se abre el Bloc de notas.
   - **macOS:** escribe `open -e .env` en el terminal. Se abre TextEdit.
   - **Linux:** en el gestor de archivos, pulsa **Ctrl+H** para ver los ficheros ocultos, botón
     derecho sobre `.env` → **Abrir con** → **Editor de texto**.
2. Busca la línea `POSTGRES_PASSWORD=change-me`.
3. Cambia `change-me` por una contraseña tuya: **solo letras y números**, al menos 16, sin
   espacios (por ejemplo `Rk7mQ2vX9pLw4TzA`). Deja delante `POSTGRES_PASSWORD=`, sin espacios
   alrededor del `=`.
4. Guarda (**Ctrl+S**, o **Cmd+S** en un Mac) y cierra el editor.

*Deberías ver:* la línea dice ahora `POSTGRES_PASSWORD=` seguido de tu contraseña.

Deja el resto de líneas como están. No pierdas este fichero: guarda una copia junto a tus copias
de seguridad ([paso 8](#8-dónde-están-tus-datos-y-tus-copias-de-seguridad)). No cambies la
contraseña después de haber arrancado la aplicación la primera vez: la base de datos se queda con
la primera (si te pasa, mira [La contraseña de la base de datos](#la-contraseña-de-la-base-de-datos)).

## 5. Arráncala

1. En el terminal, escribe:

   ```
   docker compose up -d
   ```

   *Deberías ver:* la primera vez, líneas que dicen `Pulling` y `Pulled` mientras se descarga la
   aplicación (unos cientos de MB; puede tardar unos minutos). Después, líneas como
   `Container poker-bankroll-db-1 Started` y `... Healthy` y, en más o menos un minuto, la
   última, `Container poker-bankroll-web-1 Started`. El terminal queda listo para otro comando.

2. Abre el navegador en **<http://localhost:8080>**.

   *Deberías ver:* poker-bankroll, con el panel vacío. Empieza por **Ajustes**, donde das de
   alta tus salas de póker, y luego apunta tus partidas en **Partidas**. Guarda la página en
   favoritos.

Para comprobar más adelante que todo está en marcha, escribe `docker compose ps`: salen cuatro
líneas (`db`, `api`, `web` y `backup`), cada una con `Up` y `(healthy)`.

Si algo ha salido mal, mira [Problemas frecuentes](#11-problemas-frecuentes).

## 6. Pararla, volver a arrancarla y que arranque con el ordenador

La aplicación sigue funcionando en segundo plano aunque cierres el navegador, el terminal o la
ventana de Docker Desktop. Gasta poca memoria, así que puedes dejarla encendida.

- **Pararla:** `docker compose stop` en un terminal abierto en la carpeta. *Deberías ver:*
  una línea terminada en `Stopped` por cada una de las cuatro partes de la aplicación. Tus datos
  se quedan.
- **Volver a arrancarla:** `docker compose up -d`.
- **Sin terminal (Windows y macOS):** en Docker Desktop, abre **Containers**: hay un grupo
  llamado **poker-bankroll** con un botón de parar (■) y otro de arrancar (▶).

**Que arranque con el ordenador.** Una vez arrancada, la aplicación vuelve a arrancar sola
siempre que arranca Docker, salvo que la hayas parado tú (entonces sigue parada hasta
`docker compose up -d`). Así que solo hace falta que Docker arranque con el ordenador:

- **Windows y macOS:** en Docker Desktop, abre **Settings** (el engranaje de arriba) →
  **General**, marca **Start Docker Desktop when you sign in to your computer** y pulsa
  **Apply**. La aplicación está lista uno o dos minutos después de entrar en tu sesión.
- **Linux:** ya está hecho en el paso 1 (`systemctl enable`).

Si cierras Docker Desktop del todo (icono de la ballena → **Quit Docker Desktop**), la aplicación
también se para.

## 7. Actualizar a una versión nueva

Las versiones nuevas aparecen en la página de
[versiones](https://github.com/kete1987/poker-bankroll/releases), con lo que ha cambiado.
Actualizar conserva todos tus datos: están en Docker, no en la carpeta.

1. **Haz antes una copia de seguridad**: en la aplicación, **Importar / Exportar** →
   **Descargar copia**. Guarda el fichero.
2. Descarga el zip de la versión nueva (como en el [paso 2](#2-descarga-poker-bankroll)) y
   descomprímelo **en otro sitio**, por ejemplo en *Descargas*. No lo descomprimas encima de tu
   instalación: su `.env` sustituiría al tuyo, y con él tu contraseña.
3. Copia el `docker-compose.yml` nuevo a tu carpeta `Documentos/poker-bankroll`, reemplazando el
   antiguo.
4. Abre **tu** `.env` (como en el [paso 4](#4-elige-la-contraseña-de-la-base-de-datos)), cambia
   la línea `POKER_BANKROLL_VERSION=` a la versión nueva, por ejemplo
   `POKER_BANKROLL_VERSION=0.3.0`, y guarda. Deja el resto, contraseña incluida, como está.
5. En un terminal abierto en tu carpeta:

   ```
   docker compose pull
   docker compose up -d
   ```

   *Deberías ver:* cómo se descarga la versión nueva (`Pulled`) y cómo se vuelven a crear y
   arrancar los contenedores, como en el paso 5.
6. Abre la aplicación y recarga la página. *Deberías ver:* el número de la versión nueva en la
   cabecera.

Si las notas de la versión piden añadir algo a `.env`, dicen qué. Después puedes borrar los
ficheros descomprimidos en *Descargas*.

Volver a una versión anterior no siempre es posible una vez arrancada la nueva (puede haber
cambiado cómo se guardan los datos). Si la versión nueva te da problemas, abre una
[issue](https://github.com/kete1987/poker-bankroll/issues); mientras tanto, la copia del paso 1
mantiene tus datos a salvo.

## 8. Dónde están tus datos y tus copias de seguridad

**Tus datos** están en un *volumen* de Docker llamado `poker-bankroll_db-data`: un espacio de
almacenamiento que gestiona Docker, no una carpeta normal (Docker Desktop lo enseña en
**Volumes**). Se conserva al parar la aplicación, al actualizarla, al reiniciar el ordenador y
aunque borres la carpeta poker-bankroll. Se **borra** con `docker compose down -v`, al borrar el
volumen en Docker Desktop, al **desinstalar Docker Desktop** o con su *Reset to factory
defaults*. Antes de cualquiera de esas cosas, haz una copia de seguridad.

Hay dos tipos de copia de seguridad:

- **La copia desde la aplicación**, la más fácil: **Importar / Exportar** → **Descargar copia**
  guarda un fichero `poker-bankroll-backup-<fecha>.json` en tu carpeta *Descargas*, con todo.
  Para recuperarla, en cualquier instalación (esta u otra nueva en otro ordenador):
  **Importar / Exportar** → **Elegir fichero de copia** → **Restaurar esta copia**. Hazla
  siempre que quieras tener una copia, y siempre antes de actualizar.
- **Las copias automáticas**: cada día a medianoche (hora UTC), y cada vez que arranca la
  aplicación, se guarda una copia de la base de datos en la carpeta **backups**, dentro de tu
  carpeta poker-bankroll. Se conservan las de los últimos 7 días (`daily`), 4 semanas (`weekly`)
  y 6 meses (`monthly`), con su fecha en el nombre, como `pokerbankroll-20261004.sql.gz`.
  Restaurar una de ellas requiere comandos: mira [Backups](backups.md#restore) (en inglés). Para
  que se hagan a medianoche de tu zona horaria, pon `BACKUP_TZ` en `.env` (por ejemplo
  `BACKUP_TZ=Europe/Madrid`) y ejecuta `docker compose up -d`.

**Guarda tus copias en otro sitio.** Los dos tipos están en el mismo disco que la aplicación: si
el disco se estropea o pierdes el ordenador, se van con él. De vez en cuando (una vez a la
semana, o después de una sesión larga), copia el último fichero descargado desde la aplicación
y, si quieres, el más reciente de `backups/daily` a un pendrive o a una carpeta que se sincronice
con la nube (OneDrive, iCloud Drive, Google Drive, Dropbox...). Guarda allí también una copia de
tu `.env`.

- Copia los ficheros que llevan fecha en el nombre. Los `pokerbankroll-latest.sql.gz` solo son
  accesos directos al más reciente (en Windows parecen vacíos).
- No pongas en `BACKUP_DIR` un pendrive ni una carpeta de la nube: las copias automáticas
  necesitan un disco normal. Copia allí los ficheros.
- Las copias no están cifradas y tienen todos tus resultados de póker: guárdalas donde solo tú
  puedas leerlas.

## 9. Úsala desde el móvil o desde otro ordenador

La aplicación se puede abrir desde cualquier dispositivo de la misma red (el wifi de tu casa)
mientras el ordenador donde se ejecuta esté encendido y sin suspender.

1. Busca la dirección local (IP) del ordenador donde se ejecuta la aplicación:
   - **Windows:** escribe `ipconfig` en un terminal. Busca **Dirección IPv4** debajo de tu
     adaptador wifi o Ethernet (no los que se llaman *vEthernet*), como `192.168.1.20`.
   - **macOS:** Ajustes del Sistema → **Wi‑Fi** → **Detalles...** junto a tu red (o **Red** →
     **Ethernet**): la **Dirección IP**, como `192.168.1.20`.
   - **Linux:** escribe `hostname -I` en un terminal: el primer número, como `192.168.1.20`.
2. En el móvil (conectado al mismo wifi), abre el navegador en `http://192.168.1.20:8080`, con tu
   dirección.

*Deberías ver:* la misma aplicación, con los mismos datos.

Si no se abre:

- **Windows:** puede que el Firewall de Windows la esté bloqueando. Si Windows preguntó si
  permitir *Docker Desktop Backend*, tiene que estar **Permitir** en redes privadas. Comprueba
  también que tu red sea privada: Configuración → **Red e Internet** → **Wi-Fi** (o
  **Ethernet**) → tu red → **Tipo de perfil de red: Red privada**.
- **macOS:** si el firewall está activado y pregunta si aceptar conexiones entrantes para Docker
  (`com.docker.backend`), pulsa **Permitir**.
- La dirección puede cambiar cuando se reinicia el router: vuelve a buscarla, o pide al router
  que dé siempre la misma dirección a este ordenador (*reserva DHCP*).

> **La aplicación no tiene login.** Cualquiera que llegue a ella desde tu red puede ver y cambiar
> tus datos. Úsala solo en una red de confianza, como la de tu casa, y **no la abras nunca a
> internet** (nada de abrir puertos en el router).

## 10. A qué se conecta en internet

Tus datos se quedan en tu ordenador. Aparte de descargar Docker y la aplicación (al instalar o
actualizar), la aplicación solo se conecta a internet para descargar los tipos de cambio del
Banco Central Europeo desde [Frankfurter](https://frankfurter.dev), que se usan para mostrar
juntos importes en monedas distintas ([detalles](currencies.md), en inglés). Para desactivarlo,
cambia la línea de `.env` a `EXCHANGE_RATES_ENABLED=false` y ejecuta `docker compose up -d`;
entonces puedes escribir tú los tipos de cambio en **Ajustes** → **Divisas**.

## 11. Problemas frecuentes

Ejecuta los comandos en un terminal abierto en la carpeta poker-bankroll.

### "docker" no se reconoce, o command not found

Docker no está instalado, o el terminal se abrió antes de instalarlo. Cierra el terminal, abre
otro y vuelve a probar. En Windows, si sigue fallando, reinicia el ordenador. Si Docker no está
instalado, vuelve al [paso 1](#1-instala-docker).

### Docker no está en marcha

El mensaje habla de conectar con Docker, por ejemplo:

- Windows: `error during connect: ... dockerDesktopLinuxEngine: The system cannot find the file specified`
- macOS y Linux: `Cannot connect to the Docker daemon at unix:///... Is the docker daemon running?`

Abre Docker Desktop, espera a que diga **Engine running** y vuelve a ejecutar el comando. En
Linux, ejecuta `sudo systemctl start docker`. Si Linux dice `permission denied while trying to
connect to the Docker daemon`, haz el paso 2 de [Linux](#linux) y cierra la sesión y vuelve a
entrar.

### "no configuration file provided: not found"

El terminal no está en la carpeta poker-bankroll. Ábrelo ahí, como en el
[paso 3](#3-abre-un-terminal-en-la-carpeta).

### "required variable POSTGRES_PASSWORD is missing a value"

Docker no encuentra el fichero `.env` o la contraseña dentro de él. Comprueba que el fichero
esté en la carpeta, junto a `docker-compose.yml`, y que se llame exactamente `.env`: puede que un
editor lo haya guardado como `.env.txt` (Windows) o `.env.rtf` (macOS). Cámbiale el nombre, o
descarga otra vez el zip y coge su `.env`.

### El puerto 8080 ya está en uso

`docker compose up -d` se detiene con un mensaje como `ports are not available`, `port is already
allocated` o `address already in use`: otro programa usa el puerto 8080. Abre `.env`, cambia la
línea `WEB_PORT=8080` por otro número, como `WEB_PORT=8081`, guarda y vuelve a ejecutar
`docker compose up -d`. La aplicación estará entonces en <http://localhost:8081> (y en
`http://<dirección>:8081` desde otros dispositivos).

### La página no se abre o muestra un error

Justo después de arrancar, o después de encender el ordenador, la aplicación necesita uno o dos
minutos. Espera y recarga la página. Luego comprueba con `docker compose ps` que las cuatro
líneas dicen `(healthy)`.

Si `api` sigue `unhealthy` o `Restarting`, o `docker compose up -d` terminó con
`dependency failed to start: container poker-bankroll-api-1 is unhealthy`, mira qué dice con
`docker compose logs api`. Si en las últimas líneas aparece `password authentication failed`,
mira el apartado siguiente.

### La contraseña de la base de datos

La base de datos toma su contraseña **la primera vez** que arranca la aplicación y se queda con
ella, aunque después cambies `.env`. Cuando `.env` y la base de datos no coinciden, la aplicación
no arranca (`password authentication failed` en `docker compose logs api`).

- **Has cambiado la contraseña en `.env`:** vuelve a poner la anterior y ejecuta
  `docker compose up -d`.
- **No la recuerdas:** está escrita en tu fichero `.env` (ábrelo como en el
  [paso 4](#4-elige-la-contraseña-de-la-base-de-datos)).
- **Has perdido `.env`** (pero los datos siguen en Docker): coge el `.env` del zip, ponle una
  contraseña nueva como en el paso 4 y dásela a la base de datos:

  1. Arranca solo la base de datos: `docker compose up -d db`
  2. Abre la consola de la base de datos:
     `docker compose exec db psql -U pokerbankroll -d pokerbankroll` (no pide contraseña).
     *Deberías ver:* una línea `pokerbankroll=#`.
  3. Escribe `\password` y pulsa Intro. Cuando te lo pida, escribe la contraseña de tu `.env` y
     pulsa Intro, dos veces. Mientras escribes no aparece nada: es normal.
  4. Escribe `\q` y pulsa Intro para salir de la consola.
  5. Arranca todo: `docker compose up -d`

- **Los datos ya no están** (se desinstaló o se reinició Docker): instala de nuevo la aplicación
  y restaura el fichero de copia de la aplicación (**Importar / Exportar** → **Elegir fichero de
  copia**), o una copia automática como se explica en
  [Backups](backups.md#restore-on-a-new-machine) (en inglés).

### Windows: mensajes de WSL y de virtualización

- *WSL needs updating*, *WSL 2 installation is incomplete* o parecido: abre un terminal, escribe
  `wsl --update`, acepta si Windows pide permiso y reinicia el ordenador.
- *Virtualization support not detected* o parecido: la virtualización está desactivada en el
  firmware del ordenador (BIOS/UEFI). Se activa ahí, con un nombre como *Intel Virtualization
  Technology*, *VT-x*, *SVM* o *AMD-V*; la web del fabricante del ordenador explica cómo llegar.

### Otra cosa

Abre una [issue](https://github.com/kete1987/poker-bankroll/issues) contando qué has hecho y qué
has visto. Ayuda la salida de `docker compose ps` y de `docker compose logs api`; antes de
compartirla, comprueba que no tenga datos personales.

## 12. Desinstalarla

- `docker compose down` quita la aplicación pero **conserva tus datos** (y la carpeta de copias):
  `docker compose up -d` la devuelve tal como estaba.
- Para quitarlo todo, **datos incluidos**: descarga antes una copia desde la aplicación por si
  algún día la quieres, ejecuta `docker compose down -v` y borra la carpeta poker-bankroll. Después
  puedes desinstalar Docker Desktop como cualquier otro programa.
