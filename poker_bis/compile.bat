@echo off
chcp 65001 >nul 2>&1
echo ============================================
echo   COMPILATION — Poker Indien JADE
echo ============================================
echo.

if not exist "lib\jade.jar" (
    echo ERREUR : lib\jade.jar introuvable.
    echo Placez jade.jar dans le dossier lib\ et relancez.
    pause
    exit /b 1
)

if not exist "bin" mkdir bin

echo Compilation en cours...
javac -encoding UTF-8 -cp "lib\jade.jar" -d bin src\poker\GameConfig.java src\poker\DecisionEngine.java src\poker\AiRaisonClient.java src\poker\PokerPlayerAgent.java src\poker\Main.java

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERREUR DE COMPILATION. Verifiez votre JDK (java -version / javac -version).
    pause
    exit /b 1
)

echo.
echo Compilation reussie. Classes dans bin\
echo Lancez run.bat pour demarrer la partie.
echo.
pause
