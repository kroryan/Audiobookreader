/* Wine Mono drops WiX's FileVersionInfo in the help banner. jpackage requires
 * that banner to discover WiX. Restore the verified tool version for /? only;
 * all compilation/linking calls are passed to the original WiX executable.
 * Wine cannot run Windows Installer ICE validation (LGHT0216); disable that
 * validation only for this Wine packaging path. Native Windows builds keep it. */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <stdio.h>
#include <wchar.h>
#ifndef WIX_BUILD_VERSION
#error WIX_BUILD_VERSION must match the downloaded WiX distribution
#endif
int wmain(int argc, wchar_t **argv) {
    wchar_t self[MAX_PATH], real_dir[32768], command[32768];
    GetModuleFileNameW(NULL, self, MAX_PATH);
    wchar_t *name = wcsrchr(self, L'\\');
    name = name ? name + 1 : self;
    if (argc == 2 && (!wcscmp(argv[1], L"/?") || !wcscmp(argv[1], L"-?"))) {
        printf("Windows Installer XML Toolset version %s\n", WIX_BUILD_VERSION);
        return 0;
    }
    if (!GetEnvironmentVariableW(L"BOOKREADER_WIX_REAL_DIR", real_dir, 32768)) return 2;
    const wchar_t *args = GetCommandLineW();
    if (*args == L'"') { args++; while (*args && *args != L'"') args++; if (*args) args++; }
    else { while (*args && *args != L' ' && *args != L'\t') args++; }
    const wchar_t *options = !_wcsicmp(name, L"light.exe") ? L"-sval" : L"";
    if (swprintf(command, 32768, L"\"%ls\\%ls\" %ls %ls", real_dir, name, options, args) < 0) return 2;
    STARTUPINFOW si = {0}; si.cb = sizeof(si);
    PROCESS_INFORMATION pi = {0};
    if (!CreateProcessW(NULL, command, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        fprintf(stderr, "Cannot start WiX: %lu\n", GetLastError()); return 2;
    }
    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD status = 1; GetExitCodeProcess(pi.hProcess, &status);
    CloseHandle(pi.hProcess); CloseHandle(pi.hThread);
    return (int)status;
}
