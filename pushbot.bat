@echo off

rem set current home path variable
set HOME="C:\develop\BinanceBoosterBot"

rem change into directory at first
cd %HOME%
cls

rem del old target jar file
IF EXIST %HOME%\findbooster.jar del %HOME%\findbooster.jar
IF EXIST %HOME%\target\binance-api-client-1.0.0-jar-with-dependencies.jar del %HOME%\target\binance-api-client-1.0.0-jar-with-dependencies.jar

rem kill bot remotely on pi
plink -ssh dil@raspberrypi -pw dil -v -batch ~/kill.sh

rem wait 3 seconds before starting bot again
timeout /T 3

rem compile bot via maven and START bot
mvn clean compile assembly:single && move %HOME%\target\binance-api-client-1.0.0-jar-with-dependencies.jar %HOME%\findbooster.jar && pscp -pw dil %HOME%\findbooster.jar dil@raspberrypi:/home/dil && plink -ssh dil@raspberrypi -pw dil -v -batch ~/start.sh