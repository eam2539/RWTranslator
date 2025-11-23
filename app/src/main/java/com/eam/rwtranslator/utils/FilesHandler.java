package com.eam.rwtranslator.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.eam.rwtranslator.AppConfig;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

public class FilesHandler {

    public static String getBaseName(String name) {
        if (name.isEmpty()) return name;
        return name.substring(0, name.lastIndexOf("."));
    }

    public static String getExtension(String s) {

        int dotIndex = s.lastIndexOf(".");
        if (dotIndex != -1 && dotIndex < s.length() - 1) {
            return s.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    public static ArrayList<File> LeachFilename(File directory) {
        ArrayList<File> files = new ArrayList<>();

        if (directory.exists() && directory.isDirectory()) {
            Queue<File> queue = new LinkedList<>();
            queue.add(directory);

            while (!queue.isEmpty()) {
                File currentDirectory = queue.poll();
                File[] subFiles = currentDirectory.listFiles();

                if (subFiles != null) {
                    Arrays.sort(subFiles, Comparator.comparing(File::getName));
                    for (File subFile : subFiles) {
                        if (subFile.isDirectory()) {
                            queue.add(subFile);
                        } else {
                            String fileName = subFile.getName();
                            if (fileName.endsWith(".ini") || fileName.endsWith(".template")) {
                                files.add(subFile.getAbsoluteFile());
                            }
                        }
                    }
                }
            }
        }

        return files;
    }

    public static Integer[] indexofByArray(ArrayList<String> list, String dst) {
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String str = list.get(i);
            if (str.equals(dst)) {
                indices.add(i);
            }
        }
        return indices.toArray(new Integer[0]);
    }

    public String changeToUri(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String path2 = path.replace("/storage/emulated/0/", "").replace("/", "%2F");
        return "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3A"
                + path2;
    }

    // 计算文件的MD5哈希值
    private static String calculateFileHash(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (DigestInputStream dis = new DigestInputStream(new FileInputStream(file), md)) {
            while (dis.read() != -1)
                ; // 读取文件以计算哈希值
            md = dis.getMessageDigest();
        }
        byte[] hashBytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(Integer.toString((b & 0xFF) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static String getFileSizeString(File file) {
        long fileSize = file.length(); // 文件大小，单位为字节

        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            double kbSize = (double) fileSize / 1024;
            return String.format(Locale.getDefault(), "%.2f", kbSize) + " KB";
        } else if (fileSize < 1024 * 1024 * 1024) {
            double mbSize = (double) fileSize / (1024 * 1024);
            return String.format(Locale.getDefault(), "%.2f", mbSize) + " MB";
        } else {
            double gbSize = (double) fileSize / (1024 * 1024 * 1024);
            return String.format(Locale.getDefault(), "%.2f", gbSize) + " GB";
        }
    }

    public static void unzip(File source, File destination) throws IOException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(source));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            String fileName = zipEntry.getName();
            File newFile = new File(destination, fileName);
            if (zipEntry.isDirectory()) {
                newFile.mkdirs();
            } else {
                String parentPath = newFile.getParent();
                if (parentPath != null) {
                    new File(parentPath).mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(newFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
                bos.flush();
                bos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    public static String getStringBuilder(String str) {
        StringBuilder sb = null;
        try {
            BufferedReader br = new BufferedReader(new StringReader(str));

            sb = new StringBuilder();
            sb.append(br.readLine());
            String line;
            while ((line = br.readLine()) != null) sb.append("\n" + line);
            br.close();
        } catch (IOException e) {
            Timber.e(e);
        }
        return sb.toString();
    }

    /**
     * 将Uri转换为临时文件，适配content/file协议。
     * 异常时返回null，调用方需判空。
     */
    public static File uriToFile(Uri uri, Context context) {
        if ("content".equals(uri.getScheme())) {
            File tempFile;
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                String fileName = getFileName(uri, context); // 获取原文件名
                if (fileName == null) fileName = "tempFile.tmp";
                tempFile = new File(AppConfig.externalCacheTmpDir, getBaseName(fileName) + ".tmp");
                tempFile.createNewFile();
                tempFile.deleteOnExit();
                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }
            } catch (IOException e) {
                Timber.e(e);
                return null;
            }
            return tempFile;
        } else if ("file".equals(uri.getScheme())) {
            return new File(uri.getPath());
        } else {
            return null;
        }
    }

    private static String getFileName(Uri uri, Context context) {
        String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
            }
        } catch (Exception e) {
            timber.log.Timber.e(e);
        }
        return null;
    }

    public static void delDir(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    delDir(subFile);
                }
            }
        }
        if (!file.delete()) {
            timber.log.Timber.w("Delete failed: %s", file.getAbsolutePath());
        }
    }

    public static void compressFolder(
            String sourceFolderPath, OutputStream zipFileStream) throws IOException {

        File sourceFolder = new File(sourceFolderPath);
        ZipOutputStream zipOut = new ZipOutputStream(zipFileStream);
        compressFolder(sourceFolder, sourceFolder.getName(), zipOut);
        zipOut.close();
    }

    private static void compressFolder(
            File sourceFolder, String baseName, ZipOutputStream zipOut) {
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String folderName = baseName + File.separator + file.getName();
                    compressFolder(file, folderName, zipOut);
                } else {
                    try {
                        FileInputStream inputStream = new FileInputStream(file);
                        String entryName = baseName + File.separator + file.getName();
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zipOut.putNextEntry(zipEntry);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) >= 0) {
                            zipOut.write(buffer, 0, length);
                        }
                        zipOut.closeEntry();
                        inputStream.close();
                    } catch (IOException e) {
                        Timber.e(e, "Failed to compress file: %s", file.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * 从URI获取文件夹名称
     */
    public static String getFolderNameFromUri(Uri uri, Context context) {
        try {
            // 使用DocumentFile API来获取文件夹名称
            androidx.documentfile.provider.DocumentFile documentFile =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri);

            if (documentFile != null && documentFile.getName() != null) {
                String folderName = documentFile.getName();
                // 确保文件夹名称是有效的
                if (!folderName.isEmpty()) {
                    return folderName;
                }
            }

            // 如果DocumentFile方法失败，尝试从URI字符串解析
            String uriString = uri.toString();
            if (uriString.contains("/tree/")) {
                // 处理文档树URI
                String[] parts = uriString.split("/");
                if (parts.length > 0) {
                    String lastPart = parts[parts.length - 1];
                    if (lastPart.contains(":")) {
                        String[] colonParts = lastPart.split(":");
                        if (colonParts.length > 1) {
                            String path = colonParts[1];
                            String folderName = path.substring(path.lastIndexOf("/") + 1);
                            if (!folderName.isEmpty()) {
                                return folderName;
                            }
                        }
                    }
                }
            }

            // 如果无法解析，返回默认名称
            return "ImportedFolder_" + System.currentTimeMillis();
        } catch (Exception e) {
            timber.log.Timber.e(e, "Failed to get folder name from URI");
            return "ImportedFolder_" + System.currentTimeMillis();
        }
    }

    /**
     * 从URI复制文件夹内容到目标目录
     */
    public static void copyFolderFromUri(Uri uri, File targetDir, Context context) throws IOException {
        try {
            // 使用DocumentFile API来处理文件夹复制
            androidx.documentfile.provider.DocumentFile documentFile =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri);

            if (documentFile != null && documentFile.isDirectory()) {
                copyDocumentFolder(documentFile, targetDir, context);
            } else {
                throw new IOException("Selected URI is not a valid folder");
            }
        } catch (Exception e) {
            timber.log.Timber.e(e, "Failed to copy folder from URI");
            throw new IOException("Failed to copy folder: " + e.getMessage(), e);
        }
    }

    /**
     * 递归复制DocumentFile文件夹
     */
    private static void copyDocumentFolder(androidx.documentfile.provider.DocumentFile sourceFolder,
                                           File targetDir, Context context) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        androidx.documentfile.provider.DocumentFile[] files = sourceFolder.listFiles();
        if (files != null) {
            for (androidx.documentfile.provider.DocumentFile file : files) {
                if (file.isDirectory()) {
                    File newTargetDir = new File(targetDir, file.getName());
                    copyDocumentFolder(file, newTargetDir, context);
                } else {
                    File targetFile = new File(targetDir, file.getName());
                    try (InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
                         FileOutputStream outputStream = new FileOutputStream(targetFile)) {

                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
            }
        }
    }
}
