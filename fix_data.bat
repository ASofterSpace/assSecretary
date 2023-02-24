@echo off

cd /D %~dp0

java -classpath "%~dp0\bin" -Xms16m -Xmx1024m com.asofterspace.assSecretary.AssSecretary fix_data

pause
