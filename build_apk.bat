@echo off
set "GRADLE_USER_HOME=D:\gradle_home"
call gradlew.bat :app:assembleDebug --console=plain
