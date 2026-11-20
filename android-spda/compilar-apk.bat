@echo off
setlocal
title Compilar APK - APP JVM SPDA (plataforma)
cd /d "%~dp0"

echo ==============================================================
echo   Compilar APK - APP JVM SPDA (plataforma)
echo   Usa o JDK 17 e o Gradle 8.13 ja instalados neste computador.
echo ==============================================================
echo.

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.15.6-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERRO] JDK 17 nao encontrado em "%JAVA_HOME%".
  echo        Ajuste a linha JAVA_HOME deste arquivo para o seu JDK 17.
  goto :fim
)

REM Pasta temporaria sem acento (o Java falha com "Joao" acentuado no caminho)
for %%I in ("%LOCALAPPDATA%\Temp") do set "SHORTTMP=%%~sI"
set "TMP=%SHORTTMP%"
set "TEMP=%SHORTTMP%"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%SHORTTMP% -Djava.io.tmpdir=%SHORTTMP%"

set "GRADLE="
for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\*") do (
  if exist "%%D\gradle-8.13\bin\gradle.bat" set "GRADLE=%%D\gradle-8.13\bin\gradle.bat"
)
if not defined GRADLE (
  echo [ERRO] Gradle 8.13 nao encontrado em %USERPROFILE%\.gradle\wrapper\dists.
  echo        Abra esta pasta no Android Studio e use Build ^> Build APK.
  goto :fim
)

REM ------------------------------------------------------------------
REM O Android/Gradle no Windows falha com acento no caminho do projeto
REM (o nome "Joao" com acento vira outro caractere e ele nao consegue apagar a pasta build).
REM Compila por uma letra de unidade virtual (subst) sem acento.
REM ------------------------------------------------------------------
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

REM limpa restos de compilacoes anteriores que falharam no caminho com acento
if exist "%DRV%\app\build" rmdir /s /q "%DRV%\app\build"

pushd %DRV%\
call "%GRADLE%" assembleDebug --no-daemon --console=plain
set "RESULT=%ERRORLEVEL%"
popd
subst %DRV% /d

if not "%RESULT%"=="0" (
  echo.
  echo [ERRO] A compilacao falhou. Veja as mensagens acima.
  goto :fim
)

copy /y "app\build\outputs\apk\debug\app-debug.apk" "JVM-SPDA.apk" >nul
rem publica junto com a plataforma web (botao "Baixar o aplicativo")
if not exist "..\public\apps" mkdir "..\public\apps"
copy /y "JVM-SPDA.apk" "..\public\apps\JVM-SPDA.apk" >nul
echo   Copiado para ..\public\apps\ (vai para o site ao publicar com publicar.bat)
echo.
echo ==============================================================
echo   APK gerado: %~dp0JVM-SPDA.apk
echo   Copie para o celular e instale (permitir "fontes desconhecidas").
echo ==============================================================

:fim
echo.
pause
