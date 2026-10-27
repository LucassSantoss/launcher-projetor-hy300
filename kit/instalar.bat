@echo off
rem Instala o app Projetor no projetor pela rede Wi-Fi. Detalhes no GUIA.md.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ferramentas\kit.ps1" instalar
echo.
pause
