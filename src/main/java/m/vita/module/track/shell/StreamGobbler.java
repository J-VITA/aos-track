package m.vita.module.track.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class StreamGobbler extends Thread {
    private String shell = null;
    private BufferedReader reader = null;
    private List<String> writer = null;
    private OnLineListener listener = null;

    public StreamGobbler(String shell, InputStream inputStream, List<String> outputList) {
        this.shell = shell;
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
        this.writer = outputList;
    }

    public StreamGobbler(String shell, InputStream inputStream, OnLineListener onLineListener) {
        this.shell = shell;
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
        this.listener = onLineListener;
    }

    public void run() {
        while(true) {
            try {
                String line;
                if ((line = this.reader.readLine()) != null) {
                    Debug.logOutput(String.format("[%s] %s", this.shell, line));
                    if (this.writer != null) {
                        this.writer.add(line);
                    }

                    if (this.listener != null) {
                        this.listener.onLine(line);
                    }
                    continue;
                }
            } catch (IOException var3) {
            }

            try {
                this.reader.close();
            } catch (IOException var2) {
            }

            return;
        }
    }

    public interface OnLineListener {
        void onLine(String var1);
    }
}
