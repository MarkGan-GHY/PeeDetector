@echo off
set "GRADLE_USER_HOME=D:\gradle_home"
echo GRADLE_USER_HOME=%GRADLE_USER_HOME%
call gradlew.bat :app:testDebugUnitTest --console=plain
