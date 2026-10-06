@echo off
chcp 65001 >nul 2>&1
if not exist "lib\jade.jar" ( echo ERREUR : lib\jade.jar introuvable. & pause & exit /b 1 )
if not exist "bin" mkdir bin
javac -encoding UTF-8 -cp "lib\jade.jar" -d bin src\poker\GameConfig.java src\poker\DecisionEngine.java src\poker\AiRaisonClient.java src\poker\PokerPlayerAgent.java src\poker\Main.java
if %1% neq 0 ( echo ERREUR DE COMPILATION. & pause & exit /b 1 )
echo Compilation reussie.
pause
