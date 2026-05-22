package ru.hniApplications.testApplication;

import java.io.File;
import java.io.IOException;

/**
 * Утилита для поиска исполняемого файла FFmpeg в системе.
 * Порядок поиска:
 * 1. Системное свойство "ffmpeg.path"
 * 2. Переменная окружения FFMPEG_PATH
 * 3. PATH (проверка запуска ffmpeg -version)
 * 4. Стандартные пути установки
 */
public class FFmpegLocator {

    private static String cachedPath = null;

    /**
     * Возвращает полный путь к исполняемому файлу FFmpeg.
     *
     * @return путь к ffmpeg
     * @throws IllegalStateException если FFmpeg не найден
     */
    public static String getPath() {
        if (cachedPath != null) {
            return cachedPath;
        }

        String path = findFFmpeg();
        if (path == null) {
            throw new IllegalStateException(
                "FFmpeg не найден. Настройте одним из способов:\n" +
                "  1. Системное свойство: -Dffmpeg.path=/path/to/ffmpeg\n" +
                "  2. Переменная окружения: FFMPEG_PATH=/path/to/ffmpeg\n" +
                "  3. Добавьте ffmpeg в PATH\n" +
                "  4. Установите ffmpeg в стандартный путь"
            );
        }

        cachedPath = path;
        return path;
    }

    private static String findFFmpeg() {
        // 1. Системное свойство
        String prop = System.getProperty("ffmpeg.path");
        if (prop != null && !prop.isEmpty()) {
            if (isExecutable(prop)) {
                return prop;
            }
        }

        // 2. Переменная окружения
        String env = System.getenv("FFMPEG_PATH");
        if (env != null && !env.isEmpty()) {
            if (isExecutable(env)) {
                return env;
            }
            // Может быть указан путь до директории
            String withExe = appendExecutableName(env);
            if (isExecutable(withExe)) {
                return withExe;
            }
        }

        // 3. Проверка PATH через запуск ffmpeg -version
        String fromPath = findInPath();
        if (fromPath != null) {
            return fromPath;
        }

        // 4. Стандартные пути
        String[] standardPaths = getStandardPaths();
        for (String standardPath : standardPaths) {
            if (isExecutable(standardPath)) {
                return standardPath;
            }
        }

        return null;
    }

    private static String findInPath() {
        String[] possibleNames = isWindows() ? new String[]{"ffmpeg.exe", "ffmpeg"} : new String[]{"ffmpeg"};
        
        for (String name : possibleNames) {
            try {
                ProcessBuilder pb = new ProcessBuilder(name, "-version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    // ffmpeg найден в PATH
                    return name;
                }
            } catch (IOException | InterruptedException ignored) {
                // Не найден или не запустился
            }
        }
        return null;
    }

    private static String[] getStandardPaths() {
        if (isWindows()) {
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            return new String[]{
                programFiles != null ? programFiles + "\\ffmpeg\\bin\\ffmpeg.exe" : "",
                programFilesX86 != null ? programFilesX86 + "\\ffmpeg\\bin\\ffmpeg.exe" : "",
                programFiles != null ? programFiles + "\\FFmpeg\\bin\\ffmpeg.exe" : "",
                programFilesX86 != null ? programFilesX86 + "\\FFmpeg\\bin\\ffmpeg.exe" : "",
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe"
            };
        } else {
            return new String[]{
                "/usr/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/opt/homebrew/bin/ffmpeg",
                "/snap/bin/ffmpeg"
            };
        }
    }

    private static String appendExecutableName(String path) {
        if (path.toLowerCase().endsWith("ffmpeg") || path.toLowerCase().endsWith("ffmpeg.exe")) {
            return path;
        }
        return isWindows() ? path + "\\ffmpeg.exe" : path + "/ffmpeg";
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile() && file.canExecute();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }
}
