package net.sf.aria2.util;

import android.annotation.TargetApi;
import android.content.res.AssetManager;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A simple asset extraction routine with versions for pre- and post-Lollipop IO APIs.
 */
public final class WebUiExtractor {
    public static final String ASSET_DIR = "webui";
    public static final String DIR_NAME = ".aria2-webui";
    public static final String VER_FILE = "version";

    private final AssetManager assetManager;
    private final String assetDir;
    private final File baseTargetDir;

    public WebUiExtractor(AssetManager assetManager, String assetDir, File baseTargetDir) {
        this.assetManager = assetManager;
        this.assetDir = assetDir;
        this.baseTargetDir = baseTargetDir;
    }

    public void copyToTarget() throws IOException {
        if (Build.VERSION.SDK_INT >= 21) {
            new LollipopExtractor(assetManager, assetDir, baseTargetDir)
                    .copyDir();
        } else {
            new CompatExtractor(assetManager, assetDir, baseTargetDir)
                    .copyDir();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static class CompatExtractor {
        private final AppendableStringBuilder assetNameBuilder = new AppendableStringBuilder(50);

        private final Thread theThread = Thread.currentThread();

        private final byte[] buffer = new byte[8192];

        private final AppendableStringBuilder targetFileNameBuilder;

        private final AssetManager assetManager;

        public CompatExtractor(AssetManager assetManager, String assetDir, File baseTargetDir) throws IOException {
            if (baseTargetDir.exists()) {
                deleteRecursive(baseTargetDir.listFiles());
                baseTargetDir.delete();
            }

            this.assetManager = assetManager;

            assetNameBuilder.append(assetDir);

            targetFileNameBuilder = new AppendableStringBuilder(assetNameBuilder.capacity()
                    + baseTargetDir.getPath().length());

            targetFileNameBuilder.append(baseTargetDir.getPath());
        }

        private void deleteRecursive(File... files) throws IOException {
            for (File child : files) {
                if (child.isDirectory())
                    deleteRecursive(child.listFiles());

                if (!child.delete()) {
                    throw new IOException("Failed to remove " + child);
                }
            }
        }

        private void copyDir() throws IOException {
            targetFileNameBuilder.append('/');
            
            copyDir(assetManager.list(assetNameBuilder.toString()));
        }

        private void copyDir(String[] assetList) throws IOException {
            final String dirNameStr = targetFileNameBuilder.toString();

            final File dir = new File(dirNameStr);
            if (!dir.exists() && !dir.mkdir() && !dir.exists()) {
                throw new IOException("Failed to create directory " + dir);
            }

            final int assetBaseNameLength = assetNameBuilder.length();

            for (String asset : assetList) {
                if (theThread.isInterrupted()) {
                    throw new IOException("Extraction cancelled");
                }

                assetNameBuilder.setLength(assetBaseNameLength);
                assetNameBuilder.append('/');
                assetNameBuilder.append(asset);

                targetFileNameBuilder.setLength(dirNameStr.length());
                targetFileNameBuilder.append(asset);

                String assets[] = assetManager.list(assetNameBuilder.toString());
                if (assets.length == 0) {
                    copyFile();
                } else {
                    targetFileNameBuilder.append('/');

                    copyDir(assets);
                }
            }
        }

        private void copyFile() throws IOException {
            try (InputStream in = assetManager.open(assetNameBuilder.toString(), AssetManager.ACCESS_BUFFER);
                 OutputStream out = new FileOutputStream(targetFileNameBuilder.toString()))
            {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    @TargetApi(21)
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static class LollipopExtractor {
        private final AppendableStringBuilder assetNameBuilder = new AppendableStringBuilder(50);

        private final Thread theThread = Thread.currentThread();

        private final byte[] buffer = new byte[8192];

        private final AppendableStringBuilder directoryNameBuilder;

        private final AssetManager assetManager;

        public LollipopExtractor(AssetManager assetManager, String assetDir, File baseTargetDir) throws IOException {
            if (baseTargetDir.exists()) {
                deleteRecursive(baseTargetDir.listFiles());
                baseTargetDir.delete();
            }

            this.assetManager = assetManager;

            assetNameBuilder.append(assetDir);

            directoryNameBuilder = new AppendableStringBuilder(assetNameBuilder.capacity()
                    + baseTargetDir.getPath().length());

            directoryNameBuilder.append(baseTargetDir.getPath());
        }

        private void deleteRecursive(File... files) throws IOException {
            for (File child : files) {
                if (child.isDirectory())
                    deleteRecursive(child.listFiles());

                if (!child.delete()) {
                    throw new IOException("Failed to remove " + child);
                }
            }
        }

        private void copyDir() throws IOException {
            try {
                directoryNameBuilder.append('/');

                copyDir(assetManager.list(assetNameBuilder.toString()));
            } catch (ErrnoException e) {
                throw new IOException(e);
            }
        }

        private void copyDir(String[] assetList) throws IOException, ErrnoException {
            final int dirNameLength = directoryNameBuilder.length();

            Os.mkdir(directoryNameBuilder.toString(), OsConstants.S_IRWXU);

            final int assetBaseNameLength = assetNameBuilder.length();

            for (String asset : assetList) {
                if (theThread.isInterrupted()) {
                    throw new IOException("Extraction cancelled");
                }

                assetNameBuilder.setLength(assetBaseNameLength);
                assetNameBuilder.append('/');
                assetNameBuilder.append(asset);

                directoryNameBuilder.setLength(dirNameLength);
                directoryNameBuilder.append(asset);

                String assets[] = assetManager.list(assetNameBuilder.toString());
                if (assets.length == 0) {
                    copyFile();
                } else {
                    directoryNameBuilder.append('/');

                    copyDir(assets);
                }
            }
        }

        private void copyFile() throws IOException, ErrnoException {
            try (InputStream in = assetManager.open(assetNameBuilder.toString(), AssetManager.ACCESS_BUFFER))
            {
                final FileDescriptor fd = Os.open(
                        directoryNameBuilder.toString(),
                        OsConstants.O_CREAT | OsConstants.O_TRUNC | OsConstants.O_WRONLY,
                        OsConstants.S_IROTH | OsConstants.S_IWUSR);

                try (OutputStream out = new FileOutputStream(fd)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                } finally {
                    Os.close(fd);
                }
            }
        }
    }
}
