@echo off
chcp 65001 >nul 2>&1
echo ============================================
echo   LANCEMENT — Poker Indien JADE
echo ============================================
echo.

if not exist "lib\jade.jar" (
    echo ERREUR : lib\jade.jar introuvable.
    pause
    exit /b 1
)

if not exist "bin\poker\Main.class" (
    echo ERREUR : Pas encore compile. Lancez compile.bat d'abord.
    pause
    exit /b 1
)

echo Demarrage de la plateforme JADE avec 2 agents...
echo (Ctrl+C pour arreter)
echo.

java -cp "lib\jade.jar;bin" poker.Main

echo.
echo Partie terminee.
pause
