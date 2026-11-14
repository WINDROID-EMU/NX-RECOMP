package org.nx2a;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GameStorage {
    private static final String TAG = "GameStorage";
    public static final int REQUEST_STORAGE_PERMISSION = 2001;
    public static final int REQUEST_PICK_FOLDER = 2002;
    public static final int REQUEST_MANAGE_STORAGE = 2003;

    private final Activity activity;
    private boolean waitingForPermission = false;
    private boolean waitingForPicker = false;

    public static native void nativeSetRomfs(String path);

    public GameStorage(Activity activity) {
        this.activity = activity;
    }

    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        } else {
            return activity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void requestStoragePermission() {
        waitingForPermission = true;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Uri uri = Uri.parse("package:" + activity.getPackageName());
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                } catch (Exception e2) {
                    Log.e(TAG, "Cannot open all files access settings", e2);
                }
            }
        } else {
            activity.requestPermissions(new String[]{
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
            }, REQUEST_STORAGE_PERMISSION);
        }
    }

    public void openFolderPicker() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    waitingForPicker = true;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    );
                    activity.startActivityForResult(intent, REQUEST_PICK_FOLDER);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open folder picker", e);
                    waitingForPicker = false;
                }
            }
        });
    }

    public void ensureRomfs() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!hasStoragePermission()) {
                    Toast.makeText(activity, "Conceda permissão de armazenamento para carregar os dados do jogo", Toast.LENGTH_LONG).show();
                    requestStoragePermission();
                } else {
                    Toast.makeText(activity, "Selecione a pasta dos dados do jogo (RomFS)", Toast.LENGTH_LONG).show();
                    openFolderPicker();
                }
            }
        });
    }

    public void onResume() {
        if (waitingForPermission) {
            waitingForPermission = false;
            if (hasStoragePermission()) {
                Toast.makeText(activity, "Selecione a pasta dos dados do jogo (RomFS)", Toast.LENGTH_LONG).show();
                openFolderPicker();
            } else {
                Toast.makeText(activity, "Permissão de armazenamento não concedida", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            waitingForPermission = false;
            if (hasStoragePermission()) {
                Toast.makeText(activity, "Selecione a pasta dos dados do jogo (RomFS)", Toast.LENGTH_LONG).show();
                openFolderPicker();
            } else {
                Toast.makeText(activity, "Permissão de armazenamento não concedida", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_PICK_FOLDER) {
            waitingForPicker = false;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    activity.getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {}

                String path = resolveTreeUriToPath(activity, uri);
                if (path != null) {
                    File chosen = new File(path);
                    File romfsSub = new File(chosen, "RomFS");
                    File romfsSubLower = new File(chosen, "romfs");
                    if (romfsSub.isDirectory()) {
                        path = romfsSub.getAbsolutePath();
                    } else if (romfsSubLower.isDirectory()) {
                        path = romfsSubLower.getAbsolutePath();
                    }

                    File target = new File(path);
                    File[] files = target.listFiles();
                    if (files == null || files.length == 0) {
                        Toast.makeText(activity, "A pasta selecionada está vazia! Escolha a pasta com os dados do jogo.", Toast.LENGTH_LONG).show();
                        openFolderPicker();
                        return;
                    }

                    saveRomfsToEnv(activity, path);
                    nativeSetRomfs(path);
                    Toast.makeText(activity, "Dados do jogo configurados com sucesso!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Não foi possível resolver o caminho da pasta selecionada", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            waitingForPermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "Selecione a pasta dos dados do jogo (RomFS)", Toast.LENGTH_LONG).show();
                openFolderPicker();
            } else {
                Toast.makeText(activity, "Permissão de armazenamento negada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static String resolveTreeUriToPath(Context context, Uri uri) {
        if (uri == null) return null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            if (docId == null) return null;
            String[] parts = docId.split(":", 2);
            String type = parts[0];
            String subPath = parts.length > 1 ? parts[1] : "";

            if ("primary".equalsIgnoreCase(type)) {
                return "/storage/emulated/0/" + subPath;
            }

            File storage = new File("/storage");
            File[] volumes = storage.listFiles();
            if (volumes != null) {
                for (File vol : volumes) {
                    if (vol.getName().equalsIgnoreCase(type)) {
                        return new File(vol, subPath).getAbsolutePath();
                    }
                }
            }

            File direct = new File("/storage/" + type + "/" + subPath);
            if (direct.exists()) {
                return direct.getAbsolutePath();
            }
            return "/storage/emulated/0/" + subPath;
        } catch (Exception e) {
            Log.e(TAG, "Error resolving tree uri: " + uri, e);
            return null;
        }
    }

    public static void saveRomfsToEnv(Context context, String romfsPath) {
        try {
            File extDir = context.getExternalFilesDir(null);
            if (extDir == null) return;
            File envFile = new File(extDir, "env.txt");
            List<String> lines = new ArrayList<>();
            boolean replaced = false;

            if (envFile.exists()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(envFile), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("DM_ROMFS=")) {
                        lines.add("DM_ROMFS=" + romfsPath);
                        replaced = true;
                    } else {
                        lines.add(line);
                    }
                }
                reader.close();
            }

            if (!replaced) {
                lines.add("# user selected romfs");
                lines.add("DM_ROMFS=" + romfsPath);
            }

            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(envFile), StandardCharsets.UTF_8);
            for (String l : lines) {
                writer.write(l + "\n");
            }
            writer.close();
            Log.i(TAG, "Saved DM_ROMFS to " + envFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save env.txt", e);
        }
    }
}
