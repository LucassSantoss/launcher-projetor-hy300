@echo off
rem Remove o app Projetor e devolve o projetor ao original. Detalhes no GUIA.md.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ferramentas\kit.ps1" desinstalar
echo.
pause
