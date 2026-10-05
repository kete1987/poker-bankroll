# Installation guide

**English** | [Español](install.es.md)

This guide is for you if you have never used Docker, git or a terminal. It takes you, step by
step, from nothing to poker-bankroll open in your browser, on Windows, macOS or Linux. Each step
says what you should see when it worked. Count on 30 to 60 minutes the first time, most of it
waiting for downloads.

**What you need**

- A computer with Windows 10 or 11 (64-bit), a recent macOS (one of the last three versions) or
  Linux, with at least 4 GB of memory and 3 GB of free disk space.
- An internet connection, to download Docker and the app.
- Permission to install programs on that computer (an administrator account).

**How it works, in two sentences.** poker-bankroll runs inside **Docker**, a free program that
runs other programs in sealed boxes called *containers*, so you do not have to install anything
else. Your data is kept by Docker, apart from the files you download: updating the app or
restarting the computer does not touch it.

**Contents**

1. [Install Docker](#1-install-docker)
2. [Download poker-bankroll](#2-download-poker-bankroll)
3. [Open a terminal in the folder](#3-open-a-terminal-in-the-folder)
4. [Choose the database password](#4-choose-the-database-password)
5. [Start it](#5-start-it)
6. [Stop it, start it again, start it with the computer](#6-stop-it-start-it-again-start-it-with-the-computer)
7. [Update to a new version](#7-update-to-a-new-version)
8. [Where your data and your backups are](#8-where-your-data-and-your-backups-are)
9. [Use it from your phone or another computer](#9-use-it-from-your-phone-or-another-computer)
10. [What it connects to on the internet](#10-what-it-connects-to-on-the-internet)
11. [Common problems](#11-common-problems)
12. [Remove it](#12-remove-it)

## 1. Install Docker

Follow the part for your system, then go on to step 2.

### Windows

1. Open [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
   and click **Download for Windows – AMD64**. (Choose ARM64 only if your computer has an ARM
   processor, such as a Snapdragon; almost all PCs are AMD64.)
2. Open the file you downloaded, `Docker Desktop Installer.exe`. Windows asks whether to allow
   it to make changes: click **Yes**.
3. On the configuration screen, leave **Use WSL 2 instead of Hyper-V** ticked and click **OK**.
   *You should see:* the installation running for a few minutes, then *Installation succeeded*.
4. Click **Close and restart** (or restart the computer yourself if the button only says
   *Close*).
5. After the restart, open **Docker Desktop** from the Start menu if it did not open on its own.
   Accept the service agreement (it is free for personal use). If it asks you to sign in or to
   answer a survey, you can skip both.
6. If a window says that **WSL** must be installed or updated, let it do so (or see
   [Windows: WSL and virtualization messages](#windows-wsl-and-virtualization-messages)) and
   restart again.

*You should see:* the Docker Desktop window with **Engine running** in its bottom left corner,
and a whale icon next to the clock (it may be hidden behind the **^** arrow).

### macOS

1. Find out which kind of Mac you have: Apple menu  → **About This Mac**. If it says *Chip:
   Apple M1* (M2, M3...) it is **Apple silicon**; if it says *Processor: ... Intel* it is
   **Intel**.
2. Open [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/)
   and download Docker Desktop for **Mac with Apple silicon** or **Mac with Intel chip**,
   matching what you found.
3. Open the downloaded `Docker.dmg` and drag the Docker icon onto the **Applications** folder.
4. Open **Docker** from Applications (or Launchpad). If macOS asks whether you are sure you want
   to open an app downloaded from the internet, click **Open**.
5. Accept the service agreement (it is free for personal use), choose **Use recommended
   settings** and type your Mac password when asked. If it asks you to sign in or to answer a
   survey, you can skip both.

*You should see:* the Docker Desktop window with **Engine running** in its bottom left corner,
and a whale icon in the menu bar at the top of the screen.

### Linux

On Linux you install **Docker Engine**, the version without windows (Docker Desktop for Linux
also works if you prefer it). You will type a few commands: open the **Terminal** app of your
system and type or paste each line, then press Enter (in a Linux terminal, paste is
**Ctrl+Shift+V**).

1. Install Docker with its official script (it asks for your password; nothing appears while
   you type it, that is normal):

   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   ```

   *You should see:* many lines scrolling for a minute or two, ending without the word `ERROR`.
   If your distribution is not supported by the script, follow the page for it in the
   [official instructions](https://docs.docker.com/engine/install/) instead.

2. Allow your user to use Docker without `sudo`, then **log out and log in again** (or
   restart):

   ```bash
   sudo usermod -aG docker $USER
   ```

3. Make Docker start with the computer:

   ```bash
   sudo systemctl enable --now docker
   ```

4. Check that it works:

   ```bash
   docker run --rm hello-world
   ```

   *You should see:* a message that starts with **Hello from Docker!**

## 2. Download poker-bankroll

You need two files, `docker-compose.yml` (what to run) and `.env` (your settings). Both come
in one zip file with every version.

1. Open the [latest release](https://github.com/kete1987/poker-bankroll/releases/latest) of
   poker-bankroll.
2. Under **Assets** at the bottom (click it if it is folded), click
   **poker-bankroll-X.Y.Z.zip**, where `X.Y.Z` is the version, such as `0.2.0`. Do not take the
   *Source code* files: they are for programmers.
3. Unzip it into your **Documents** folder:
   - **Windows:** in *Downloads*, right click the zip → **Extract All...** → in the box, put your
     Documents folder (for example `C:\Users\Ana\Documents`) → **Extract**.
   - **macOS:** double click the zip in *Downloads* (Safari may have unzipped it already), then
     drag the `poker-bankroll` folder to *Documents* in the Finder sidebar.
   - **Linux:** right click the zip → **Extract Here**, then move the `poker-bankroll` folder
     to *Documents*.

*You should see:* a folder **Documents/poker-bankroll** with `docker-compose.yml` and `.env`
inside. On macOS and Linux you only see `docker-compose.yml`: files whose name starts with a dot
are hidden, and `.env` is there all the same (**Cmd+Shift+.** in the Finder, **Ctrl+H** in the
Linux file manager shows them).

This folder is now your installation: always use this one. Do not run the app from inside the
zip file, and do not move the folder while the app is running.

## 3. Open a terminal in the folder

The terminal is a window where you type commands. Every command in this guide is typed in a
terminal **opened in the poker-bankroll folder**: type it (or paste it) and press **Enter**.

- **Windows 11:** open the `poker-bankroll` folder in File Explorer, right click an empty space
  in it → **Open in Terminal**.
  **Windows 10:** open the folder, click the address bar at the top, type `powershell` and press
  Enter.
  *You should see:* a window with a line like `PS C:\Users\Ana\Documents\poker-bankroll>`.
  Paste with **Ctrl+V**.
- **macOS:** open **Terminal** (press **Cmd+Space**, type `Terminal`, press Enter). Type `cd`
  followed by a space, **drag the poker-bankroll folder** from the Finder into the Terminal
  window, and press Enter.
  *You should see:* a line ending in `poker-bankroll %`. Paste with **Cmd+V**.
- **Linux:** open the folder in your file manager, right click an empty space in it → **Open in
  Terminal**.
  *You should see:* a line ending in `poker-bankroll$`. Paste with **Ctrl+Shift+V**.

To check that you are in the right place, type `ls` and press Enter: `docker-compose.yml` must
be in the list.

## 4. Choose the database password

The app keeps your data in a database protected by a password. You choose it once, here, and
never have to type it anywhere else: it is **not** a password to open the app (the app has none).

1. Open the `.env` file with a plain text editor:
   - **Windows:** type `notepad .env` in the terminal. Notepad opens.
   - **macOS:** type `open -e .env` in the terminal. TextEdit opens.
   - **Linux:** in the file manager, press **Ctrl+H** to see hidden files, right click `.env` →
     **Open With** → **Text Editor**.
2. Find the line `POSTGRES_PASSWORD=change-me`.
3. Replace `change-me` with a password of your own: **letters and numbers only**, at least 16 of
   them, no spaces (for example `Rk7mQ2vX9pLw4TzA`). Keep `POSTGRES_PASSWORD=` in front, with no
   space around the `=`.
4. Save (**Ctrl+S**, or **Cmd+S** on a Mac) and close the editor.

*You should see:* the line now reads `POSTGRES_PASSWORD=` followed by your password.

Leave the other lines as they are. Do not lose this file: keep a copy with your backups
([step 8](#8-where-your-data-and-your-backups-are)). Do not change the password once the app has
started for the first time: the database keeps the first one (if it happens, see
[The database password](#the-database-password)).

## 5. Start it

1. In the terminal, type:

   ```
   docker compose up -d
   ```

   *You should see:* the first time, lines saying `Pulling` and `Pulled` while the app is
   downloaded (a few hundred MB; it can take some minutes). Then lines such as
   `Container poker-bankroll-db-1 Started` and `... Healthy`, and after about a minute the last
   one, `Container poker-bankroll-web-1 Started`. The terminal is ready for another command.

2. Open your browser at **<http://localhost:8080>**.

   *You should see:* poker-bankroll, with an empty dashboard. Start in **Settings**, where you
   add your poker rooms, then record your games in **Games**. Bookmark the page.

To check later that everything is running, type `docker compose ps`: it lists four lines (`db`,
`api`, `web` and `backup`), each saying `Up` and `(healthy)`.

If something else went wrong, see [Common problems](#11-common-problems).

## 6. Stop it, start it again, start it with the computer

The app keeps running in the background when you close the browser, the terminal or the Docker
Desktop window. It uses little memory, so you can leave it on.

- **Stop it:** `docker compose stop` in a terminal opened in the folder. *You should see:* one
  line ending in `Stopped` for each of the four parts of the app. Your data stays.
- **Start it again:** `docker compose up -d`.
- **Without the terminal (Windows and macOS):** in Docker Desktop, open **Containers**: there is
  a group called **poker-bankroll** with a stop (■) and a start (▶) button.

**Start it with the computer.** Once started, the app starts again on its own whenever Docker
starts, unless you stopped it yourself (then it stays stopped until `docker compose up -d`). So
you only need Docker to start with the computer:

- **Windows and macOS:** in Docker Desktop, open **Settings** (the gear at the top) → **General**,
  tick **Start Docker Desktop when you sign in to your computer** and click **Apply**. The app is
  ready a minute or two after you sign in.
- **Linux:** already done in step 1 (`systemctl enable`).

Quitting Docker Desktop (whale icon → **Quit Docker Desktop**) stops the app as well.

## 7. Update to a new version

New versions are listed in the [releases](https://github.com/kete1987/poker-bankroll/releases)
page, with what changed. Updating keeps all your data: it lives in Docker, not in the folder.

1. **Take a backup first**: in the app, **Import / Export** → **Download backup**. Keep the file.
2. Download the zip of the new version (as in [step 2](#2-download-poker-bankroll)) and unzip
   it **somewhere else**, for example in *Downloads*. Do not unzip it over your installation:
   its `.env` would replace yours, with your password.
3. Copy the new `docker-compose.yml` into your `Documents/poker-bankroll` folder, replacing the
   old one.
4. Open **your** `.env` (as in [step 4](#4-choose-the-database-password)), change the line
   `POKER_BANKROLL_VERSION=` to the new version, for example `POKER_BANKROLL_VERSION=0.3.0`,
   and save. Leave the rest, password included, as it is.
5. In a terminal opened in your folder:

   ```
   docker compose pull
   docker compose up -d
   ```

   *You should see:* the new version being downloaded (`Pulled`), then the containers being
   recreated and started, as in step 5.
6. Open the app and reload the page. *You should see:* the new version number in the header.

If the release notes ask you to add a setting to `.env`, they say which. You can delete the
unzipped files in *Downloads* afterwards.

Going back to an older version is not always possible once the new one has started (it may have
changed how the data is stored). If the new version does not work for you, open an
[issue](https://github.com/kete1987/poker-bankroll/issues); the backup of step 1 keeps your data
safe meanwhile.

## 8. Where your data and your backups are

**Your data** is in a Docker *volume* called `poker-bankroll_db-data`: a storage space that
Docker manages, not a normal folder (Docker Desktop lists it under **Volumes**). It stays when
you stop the app, update it, restart the computer or delete the poker-bankroll folder. It is
**erased** by `docker compose down -v`, by deleting the volume in Docker Desktop, by
**uninstalling Docker Desktop** or by its *Reset to factory defaults*. Before any of those, take
a backup.

There are two kinds of backup:

- **The backup from the app**, the easiest one: **Import / Export** → **Download backup** saves
  a file `poker-bankroll-backup-<date>.json` to your *Downloads* folder, with everything. To get
  it back, on any installation (this one, or a new one on another computer): **Import / Export**
  → **Choose backup file** → **Restore this backup**. Make one whenever you want a copy, and
  always before updating.
- **The automatic backups**: every day at midnight (UTC), and every time the app starts, a copy
  of the database is saved in the **backups** folder inside your poker-bankroll folder. The ones
  of the last 7 days (`daily`), 4 weeks (`weekly`) and 6 months (`monthly`) are kept, named with
  their date, such as `pokerbankroll-20261004.sql.gz`. Restoring one needs commands: see
  [Backups](backups.md#restore). To have them at midnight of your time zone, set `BACKUP_TZ` in
  `.env` (for example `BACKUP_TZ=Europe/Madrid`) and run `docker compose up -d`.

**Copy your backups somewhere else.** Both kinds are on the same disk as the app: if the disk
breaks or the computer is lost, they go with it. Regularly (once a week, or after a long
session), copy the latest backup file from the app, and if you like the newest file of
`backups/daily`, to a USB drive or to a folder that syncs with the cloud (OneDrive, iCloud
Drive, Google Drive, Dropbox...). Keep a copy of your `.env` there too.

- Copy the files named with a date. The `pokerbankroll-latest.sql.gz` files are only shortcuts
  to the newest one (on Windows they look empty).
- Do not set `BACKUP_DIR` to a USB drive or a cloud folder: the automatic backups need a normal
  disk. Copy the files there instead.
- Backups are not encrypted and hold all your poker results: keep them where only you can read
  them.

## 9. Use it from your phone or another computer

The app can be opened from any device on the same network (your home Wi-Fi), while the computer
running it is on and awake.

1. Find the local address (IP) of the computer that runs the app:
   - **Windows:** type `ipconfig` in a terminal. Look for **IPv4 Address** under your Wi-Fi or
     Ethernet adapter (not the ones named *vEthernet*), such as `192.168.1.20`.
   - **macOS:** System Settings → **Wi-Fi** → **Details...** next to your network (or
     **Network** → **Ethernet**): the **IP address**, such as `192.168.1.20`.
   - **Linux:** type `hostname -I` in a terminal: the first number, such as `192.168.1.20`.
2. On the phone (connected to the same Wi-Fi), open the browser at `http://192.168.1.20:8080`,
   with your address.

*You should see:* the same app, with the same data.

If it does not open:

- **Windows:** Windows Firewall may be blocking it. If Windows asked whether to allow *Docker
  Desktop Backend*, it needs **Allow** on private networks. Also check that your network is
  private: Settings → **Network & internet** → **Wi-Fi** (or **Ethernet**) → your network →
  **Network profile type: Private network**.
- **macOS:** if the firewall is on and asks whether to accept incoming connections for Docker
  (`com.docker.backend`), click **Allow**.
- The address can change when the router restarts: look it up again, or ask your router to
  always give the same address to this computer (*DHCP reservation*).

> **The app has no login.** Anyone who can reach it on your network can see and change your
> data. Use it only on a network you trust, such as your home, and **never open it to the
> internet** (no port forwarding on the router).

## 10. What it connects to on the internet

Your data stays on your computer. Besides downloading Docker and the app (when you install or
update), the app only connects to the internet to download the exchange rates of the European
Central Bank from [Frankfurter](https://frankfurter.dev), used to show amounts in different
currencies together ([details](currencies.md)). To turn that off, change the line in `.env` to
`EXCHANGE_RATES_ENABLED=false` and run `docker compose up -d`; you can then type the rates
yourself in **Settings** → **Currencies**.

## 11. Common problems

Run the commands in a terminal opened in the poker-bankroll folder.

### "docker" is not recognized, or command not found

Docker is not installed, or the terminal was opened before installing it. Close the terminal,
open a new one and try again. On Windows, restart the computer if it still fails. If Docker is
not installed, go back to [step 1](#1-install-docker).

### Docker is not running

The message mentions connecting to Docker, for example:

- Windows: `error during connect: ... dockerDesktopLinuxEngine: The system cannot find the file specified`
- macOS and Linux: `Cannot connect to the Docker daemon at unix:///... Is the docker daemon running?`

Open Docker Desktop and wait until it says **Engine running**, then run the command again. On
Linux, run `sudo systemctl start docker`. If Linux says `permission denied while trying to
connect to the Docker daemon`, do step 2 of [Linux](#linux) and log out and in again.

### "no configuration file provided: not found"

The terminal is not in the poker-bankroll folder. Open it there, as in
[step 3](#3-open-a-terminal-in-the-folder).

### "required variable POSTGRES_PASSWORD is missing a value"

Docker does not find the `.env` file or the password in it. Check that the file is in the
folder, next to `docker-compose.yml`, and that its name is exactly `.env`: an editor may have
saved it as `.env.txt` (Windows) or `.env.rtf` (macOS). Rename it, or download the zip again and
take its `.env`.

### The port 8080 is already in use

`docker compose up -d` stops with a message such as `ports are not available`, `port is already
allocated` or `address already in use`: another program uses port 8080. Open `.env`, change the
line `WEB_PORT=8080` to another number, such as `WEB_PORT=8081`, save and run
`docker compose up -d` again. The app is then at <http://localhost:8081> (and at
`http://<address>:8081` from other devices).

### The page does not open, or shows an error

Right after starting, or after turning on the computer, the app needs a minute or two. Wait and
reload the page. Then check with `docker compose ps` that the four lines say `(healthy)`.

If `api` stays `unhealthy` or `Restarting`, or `docker compose up -d` ended with
`dependency failed to start: container poker-bankroll-api-1 is unhealthy`, look at what it says
with `docker compose logs api`. When the last lines contain `password authentication failed`,
see the next section.

### The database password

The database takes its password **the first time** the app starts and keeps it, even if you
change `.env` afterwards. When `.env` and the database do not agree, the app does not start
(`password authentication failed` in `docker compose logs api`).

- **You changed the password in `.env`:** put the previous one back and run
  `docker compose up -d`.
- **You forgot it:** it is written in your `.env` file (open it as in
  [step 4](#4-choose-the-database-password)).
- **You lost `.env`** (but the data is still in Docker): take the `.env` of the zip, set a new
  password in it as in step 4, and give that password to the database:

  1. Start only the database: `docker compose up -d db`
  2. Open the database console: `docker compose exec db psql -U pokerbankroll -d pokerbankroll`
     (no password is asked). *You should see:* a line `pokerbankroll=#`.
  3. Type `\password` and press Enter. When it asks, type the password of your `.env` and press
     Enter, twice. Nothing appears while you type: that is normal.
  4. Type `\q` and press Enter to leave the console.
  5. Start everything: `docker compose up -d`

- **The data itself is gone** (Docker was uninstalled or reset): install the app again and
  restore the backup file from the app (**Import / Export** → **Choose backup file**), or an
  automatic backup as explained in [Backups](backups.md#restore-on-a-new-machine).

### Windows: WSL and virtualization messages

- *WSL needs updating*, *WSL 2 installation is incomplete* or similar: open a terminal, type
  `wsl --update`, accept if Windows asks for permission, and restart the computer.
- *Virtualization support not detected* or similar: virtualization is turned off in the
  computer's firmware (BIOS/UEFI). It is turned on there, under a name like *Intel
  Virtualization Technology*, *VT-x*, *SVM* or *AMD-V*; the website of the computer's
  manufacturer explains how to get there.

### Something else

Open an [issue](https://github.com/kete1987/poker-bankroll/issues) saying what you did and
what you saw. The output of `docker compose ps` and `docker compose logs api` helps; check that
they contain no personal data before you share them.

## 12. Remove it

- `docker compose down` removes the app but **keeps your data** (and the backups folder):
  `docker compose up -d` brings it back as it was.
- To remove everything, **data included**: download a backup from the app first if you may want
  it one day, then run `docker compose down -v` and delete the poker-bankroll folder. Docker
  Desktop can then be uninstalled like any other program.
