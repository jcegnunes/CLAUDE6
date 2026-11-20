@echo off
setlocal
title Compilar APK - APP JVM Ensaios de EPI
cd /d "%~dp0"

echo ==============================================================
echo   Compilar APK - APP JVM Ensaios de EPI
echo   Gradle 9.3.1 ja instalado neste computador (1a vez baixa as
echo   ferramentas de compilacao do Android - precisa de internet).
echo   Mesma chave (debug.keystore) do app atual: instala POR CIMA,
echo   mantendo os dados do celular.
echo ==============================================================
echo.

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERRO] JDK 21 nao encontrado em "%JAVA_HOME%".
  goto :fim
)

REM Pasta temporaria sem acento (o Java falha com "Joao" acentuado no caminho)
for %%I in ("%LOCALAPPDATA%\Temp") do set "SHORTTMP=%%~sI"
set "TMP=%SHORTTMP%"
set "TEMP=%SHORTTMP%"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%SHORTTMP% -Djava.io.tmpdir=%SHORTTMP%"

set "GRADLE="
for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-9.3.1-bin\*") do (
  if exist "%%D\gradle-9.3.1\bin\gradle.bat" set "GRADLE=%%D\gradle-9.3.1\bin\gradle.bat"
)
if not defined GRADLE (
  echo [ERRO] Gradle 9.3.1 nao encontrado em %USERPROFILE%\.gradle\wrapper\dists.
  echo        Abra esta pasta no Android Studio e use Build ^> Build APK.
  goto :fim
)

REM Compila por uma letra de unidade virtual (subst) sem acento no caminho.
set "DRV="
for %%L in (S T U V W X Y Z) do (
  if not defined DRV if not exist %%L:\ set "DRV=%%L:"
)
if not defined DRV (
  echo [ERRO] Nenhuma letra de unidade livre ^(S a Z^) para compilar.
  goto :fim
)
subst %DRV% "%~dp0."
if errorlevel 1 (
  echo [ERRO] Nao foi possivel criar a unidade %DRV%.
  goto :fim
)
echo Compilando pela unidade %DRV% ^(mesma pasta, caminho sem acento^)...
echo.

pushd %DRV%\
REM Gradle 9 no Windows tenta apagar o proprio arquivo de trava quando o caminho tem apelido
REM (unidade S: ou nome curto JOOCAR~1): o cache do projeto fica num caminho sem apelido nem acento.
call "%GRADLE%" :app:assembleDebug --console=plain --project-cache-dir "C:\Users\Public\jvm-ensaios-gradle"
set "RESULT=%ERRORLEVEL%"
call "%GRADLE%" --stop >nul 2>nul
popd
subst %DRV% /d

if not "%RESULT%"=="0" (
  echo.
  echo [ERRO] A compilacao falhou. Veja as mensagens acima.
  goto :fim
)

copy /y "app\build\outputs\apk\debug\app-debug.apk" "JVM-Ensaios-EPI.apk" >nul
rem publica junto com a plataforma web (botao "Baixar o aplicativo")
if not exist "..\public\apps" mkdir "..\public\apps"
copy /y "JVM-Ensaios-EPI.apk" "..\public\apps\JVM-Ensaios-EPI.apk" >nul
echo   Copiado para ..\public\apps\ (vai para o site ao publicar com publicar.bat)
echo.
echo ==============================================================
echo   APK gerado: %~dp0JVM-Ensaios-EPI.apk
echo   Instala por cima do app de Ensaios atual (dados mantidos).
echo ==============================================================

:fim
echo.
pause
