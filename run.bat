@echo off
echo ====================================================
echo Starting PDF Book Reader Desktop Application...
echo ====================================================
call .\mvnw.cmd javafx:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo An error occurred while launching the application.
    pause
)
