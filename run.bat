@echo off

cd /D %~dp0

start "assSecretary" javaw -classpath "%~dp0\bin" -Xms16m -Xmx2048m com.asofterspace.assSecretary.AssSecretary %*

exit
