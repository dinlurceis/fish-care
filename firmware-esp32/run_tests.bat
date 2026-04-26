@echo off
REM ===========================================================
REM FISH-CARE System - Full Test Suite Runner
REM ===========================================================

setlocal enabledelayedexpansion

echo.
echo ╔════════════════════════════════════════════════════╗
echo ║  FISH-CARE SYSTEM - COMPREHENSIVE TEST SUITE      ║
echo ║  Date: 2026-04-14                                  ║
echo ╚════════════════════════════════════════════════════╝
echo.

REM Set paths
set PROJECT_ROOT=%cd%
set TEST_DIR=%PROJECT_ROOT%\test
set REPORT_DIR=%PROJECT_ROOT%\test_reports

REM Create report directory
if not exist "%REPORT_DIR%" mkdir "%REPORT_DIR%"

REM ===== SECTION 1: UNIT TESTS =====
echo [1/3] Running SENSOR UNIT TESTS...
echo ============================================
call pio test -e esp32dev --filter test_sensors > "%REPORT_DIR%\test_sensors_report.txt" 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [✓] SENSOR TESTS PASSED
) else (
    echo [✗] SENSOR TESTS FAILED - See %REPORT_DIR%\test_sensors_report.txt
    pause
    exit /b 1
)
echo.

REM ===== SECTION 2: PERFORMANCE TESTS =====
echo [2/3] Running PERFORMANCE & INTEGRATION TESTS...
echo ============================================
call pio test -e esp32dev --filter test_performance > "%REPORT_DIR%\test_performance_report.txt" 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [✓] PERFORMANCE TESTS PASSED
) else (
    echo [✗] PERFORMANCE TESTS FAILED - See %REPORT_DIR%\test_performance_report.txt
    pause
    exit /b 1
)
echo.

REM ===== SECTION 3: FIREBASE TESTS =====
echo [3/3] Running FIREBASE & SCHEMA TESTS...
echo ============================================
call pio test -e esp32dev --filter test_firebase > "%REPORT_DIR%\test_firebase_report.txt" 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [✓] FIREBASE TESTS PASSED
) else (
    echo [✗] FIREBASE TESTS FAILED - See %REPORT_DIR%\test_firebase_report.txt
    pause
    exit /b 1
)
echo.

REM ===== SUMMARY =====
echo.
echo ╔════════════════════════════════════════════════════╗
echo ║        ✓ ALL TESTS PASSED SUCCESSFULLY             ║
echo ╚════════════════════════════════════════════════════╝
echo.
echo Test Reports Generated:
echo  • %REPORT_DIR%\test_sensors_report.txt
echo  • %REPORT_DIR%\test_performance_report.txt
echo  • %REPORT_DIR%\test_firebase_report.txt
echo.
echo Next Steps:
echo  1. Review TEST_REPORT.md for detailed analysis
echo  2. Fix any identified issues (BUG-001, BUG-002, etc.)
echo  3. Run full integration test on hardware
echo  4. Deploy to production
echo.
pause
