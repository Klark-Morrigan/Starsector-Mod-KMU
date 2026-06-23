@echo off
setlocal
rem Explorer-double-click launcher for scripts/setup-hooks.sh (this repo's
rem local entry). The local .sh exec's Common-Automation's canonical
rem setup-hooks engine with COMMON_AUTOMATION_TARGET_REPO set, so the chain
rem is local.bat -> local.sh -> shared engine - one entry point per layer.
rem
rem _find-bash.bat from Common-Automation resolves Git Bash robustly and
rem sets %BASH%; reused rather than duplicated.
rem
rem Common-Automation is expected as a sibling checkout under the same
rem parent directory.

call "%~dp0..\..\Common-Automation\scripts\_find-bash.bat" || exit /b 1

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%~dp0setup-hooks.sh" %*
set rc=%errorlevel%
pause
exit /b %rc%
