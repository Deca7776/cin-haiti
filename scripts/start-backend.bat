@echo off
REM Demarrage rapide - Microservice CIN (profil local, sans Docker)
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set SPRING_PROFILES_ACTIVE=local
cd /d "%~dp0.."
".tools\apache-maven-3.9.6\bin\mvn.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
