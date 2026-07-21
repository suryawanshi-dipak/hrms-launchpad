@REM Maven Wrapper script for Windows
@echo off
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6
set MAVEN_PROJECTBASEDIR=%~dp0

if exist "%MAVEN_HOME%\bin\mvn.cmd" goto run_maven

echo Downloading Apache Maven 3.9.6...
powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\maven.zip' -UseBasicParsing"
powershell -Command "Expand-Archive '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force"
rename "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6" "apache-maven-3.9.6"

:run_maven
"%MAVEN_HOME%\bin\mvn.cmd" %*
