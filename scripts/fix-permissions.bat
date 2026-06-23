@echo off
setlocal
rem Explorer-double-click launcher for scripts/fix-permissions.sh
rem (this repo's local entry). The local .sh transitively exec's
rem Common-Automation's canonical fix engine with COMMON_AUTOMATION_TARGET_REPO
rem set, so the chain is local.bat -> local.sh -> shared engine - one
rem entry point per layer, no shortcutting.
rem
rem _find-bash.bat from Common-Automation resolves Git Bash robustly and
rem sets %BASH%; reused rather than duplicated.
rem
rem Common-Automation is expected as a sibling checkout under the same
rem parent directory.

call "%~dp0..\..\Common-Automation\scripts\_find-bash.bat" || exit /b 1

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%~dp0fix-permissions.sh" %*
set rc=%errorlevel%
pause
exit /b %rc%
