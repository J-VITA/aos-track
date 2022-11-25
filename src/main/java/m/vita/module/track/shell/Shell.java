package m.vita.module.track.shell;

import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Shell {
    protected static String[] availableTestCommands = new String[]{"echo -BOC-", "id"};

    public Shell() {
    }

    /** @deprecated */
    @Deprecated
    public static List<String> run(String shell, String[] commands, boolean wantSTDERR) {
        return run(shell, commands, (String[])null, wantSTDERR);
    }

    public static List<String> run(String shell, String[] commands, String[] environment, boolean wantSTDERR) {
        String shellUpper = shell.toUpperCase(Locale.ENGLISH);
        if (Debug.getSanityChecksEnabledEffective() && Debug.onMainThread()) {
            Debug.log("Application attempted to run a shell command from the main thread");
            throw new ShellOnMainThreadException("Application attempted to run a shell command from the main thread");
        } else {
            Debug.logCommand(String.format("[%s%%] START", shellUpper));
            List res = Collections.synchronizedList(new ArrayList());

            try {
                if (environment != null) {
                    Map<String, String> newEnvironment = new HashMap();
                    newEnvironment.putAll(System.getenv());
                    String[] var8 = environment;
                    int var9 = environment.length;

                    for(int var10 = 0; var10 < var9; ++var10) {
                        String entry = var8[var10];
                        int split;
                        if ((split = entry.indexOf("=")) >= 0) {
                            newEnvironment.put(entry.substring(0, split), entry.substring(split + 1));
                        }
                    }

                    int i = 0;
                    environment = new String[newEnvironment.size()];

                    for(Iterator var22 = newEnvironment.entrySet().iterator(); var22.hasNext(); ++i) {
                        Entry<String, String> entry = (Entry)var22.next();
                        environment[i] = (String)entry.getKey() + "=" + (String)entry.getValue();
                    }
                }

                Process process = Runtime.getRuntime().exec(shell, environment);
                DataOutputStream STDIN = new DataOutputStream(process.getOutputStream());
                StreamGobbler STDOUT = new StreamGobbler(shellUpper + "-", process.getInputStream(), res);
                StreamGobbler STDERR = new StreamGobbler(shellUpper + "*", process.getErrorStream(), wantSTDERR ? res : null);
                STDOUT.start();
                STDERR.start();

                try {
                    String[] var25 = commands;
                    int var26 = commands.length;

                    for(int var12 = 0; var12 < var26; ++var12) {
                        String write = var25[var12];
                        Debug.logCommand(String.format("[%s+] %s", shellUpper, write));
                        STDIN.write((write + "\n").getBytes("UTF-8"));
                        STDIN.flush();
                    }

                    STDIN.write("exit\n".getBytes("UTF-8"));
                    STDIN.flush();
                } catch (IOException var15) {
                    if (!var15.getMessage().contains("EPIPE")) {
                        throw var15;
                    }
                }

                process.waitFor();

                try {
                    STDIN.close();
                } catch (IOException var14) {
                }

                STDOUT.join();
                STDERR.join();
                process.destroy();
                if (SU.isSU(shell) && process.exitValue() == 255) {
                    res = null;
                }
            } catch (IOException var16) {
                res = null;
            } catch (InterruptedException var17) {
                res = null;
            }

            Debug.logCommand(String.format("[%s%%] END", shell.toUpperCase(Locale.ENGLISH)));
            return res;
        }
    }

    protected static boolean parseAvailableResult(List<String> ret, boolean checkForRoot) {
        if (ret == null) {
            return false;
        } else {
            boolean echo_seen = false;
            Iterator var3 = ret.iterator();

            while(var3.hasNext()) {
                String line = (String)var3.next();
                if (line.contains("uid=")) {
                    return !checkForRoot || line.contains("uid=0");
                }

                if (line.contains("-BOC-")) {
                    echo_seen = true;
                }
            }

            return echo_seen;
        }
    }

    public static class Interactive {
        private final Handler handler;
        private final boolean autoHandler;
        private final String shell;
        private final boolean wantSTDERR;
        private final List<Command> commands;
        private final Map<String, String> environment;
        private final StreamGobbler.OnLineListener onSTDOUTLineListener;
        private final StreamGobbler.OnLineListener onSTDERRLineListener;
        private int watchdogTimeout;
        private Process process;
        private DataOutputStream STDIN;
        private StreamGobbler STDOUT;
        private StreamGobbler STDERR;
        private ScheduledThreadPoolExecutor watchdog;
        private volatile boolean running;
        private volatile boolean idle;
        private volatile boolean closed;
        private volatile int callbacks;
        private volatile int watchdogCount;
        private final Object idleSync;
        private final Object callbackSync;
        private volatile int lastExitCode;
        private volatile String lastMarkerSTDOUT;
        private volatile String lastMarkerSTDERR;
        private volatile Command command;
        private volatile List<String> buffer;

        private Interactive(final Builder builder, final OnCommandResultListener onCommandResultListener) {
            this.process = null;
            this.STDIN = null;
            this.STDOUT = null;
            this.STDERR = null;
            this.watchdog = null;
            this.running = false;
            this.idle = true;
            this.closed = true;
            this.callbacks = 0;
            this.idleSync = new Object();
            this.callbackSync = new Object();
            this.lastExitCode = 0;
            this.lastMarkerSTDOUT = null;
            this.lastMarkerSTDERR = null;
            this.command = null;
            this.buffer = null;
            this.autoHandler = builder.autoHandler;
            this.shell = builder.shell;
            this.wantSTDERR = builder.wantSTDERR;
            this.commands = builder.commands;
            this.environment = builder.environment;
            this.onSTDOUTLineListener = builder.onSTDOUTLineListener;
            this.onSTDERRLineListener = builder.onSTDERRLineListener;
            this.watchdogTimeout = builder.watchdogTimeout;
            if (Looper.myLooper() != null && builder.handler == null && this.autoHandler) {
                this.handler = new Handler();
            } else {
                this.handler = builder.handler;
            }

            if (onCommandResultListener != null) {
                this.watchdogTimeout = 60;
                this.commands.add(0, new Command(Shell.availableTestCommands, 0, new OnCommandResultListener() {
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                        if (exitCode == 0 && !Shell.parseAvailableResult(output, SU.isSU(Interactive.this.shell))) {
                            exitCode = -4;
                        }

                        Interactive.this.watchdogTimeout = builder.watchdogTimeout;
                        onCommandResultListener.onCommandResult(0, exitCode, output);
                    }
                }, (OnCommandLineListener)null));
            }

            if (!this.open() && onCommandResultListener != null) {
                onCommandResultListener.onCommandResult(0, -3, (List)null);
            }

        }

        protected void finalize() throws Throwable {
            if (!this.closed && Debug.getSanityChecksEnabledEffective()) {
                Debug.log("Application did not close() interactive shell");
                throw new ShellNotClosedException();
            } else {
                super.finalize();
            }
        }

        public void addCommand(String command) {
            this.addCommand((String)command, 0, (OnCommandResultListener)((OnCommandResultListener)null));
        }

        public void addCommand(String command, int code, OnCommandResultListener onCommandResultListener) {
            this.addCommand(new String[]{command}, code, onCommandResultListener);
        }

        public void addCommand(String command, int code, OnCommandLineListener onCommandLineListener) {
            this.addCommand(new String[]{command}, code, onCommandLineListener);
        }

        public void addCommand(List<String> commands) {
            this.addCommand((List)commands, 0, (OnCommandResultListener)((OnCommandResultListener)null));
        }

        public void addCommand(List<String> commands, int code, OnCommandResultListener onCommandResultListener) {
            this.addCommand((String[])commands.toArray(new String[commands.size()]), code, onCommandResultListener);
        }

        public void addCommand(List<String> commands, int code, OnCommandLineListener onCommandLineListener) {
            this.addCommand((String[])commands.toArray(new String[commands.size()]), code, onCommandLineListener);
        }

        public void addCommand(String[] commands) {
            this.addCommand((String[])commands, 0, (OnCommandResultListener)((OnCommandResultListener)null));
        }

        public synchronized void addCommand(String[] commands, int code, OnCommandResultListener onCommandResultListener) {
            this.commands.add(new Command(commands, code, onCommandResultListener, (OnCommandLineListener)null));
            this.runNextCommand();
        }

        public synchronized void addCommand(String[] commands, int code, OnCommandLineListener onCommandLineListener) {
            this.commands.add(new Command(commands, code, (OnCommandResultListener)null, onCommandLineListener));
            this.runNextCommand();
        }

        private void runNextCommand() {
            this.runNextCommand(true);
        }

        private synchronized void handleWatchdog() {
            if (this.watchdog != null) {
                if (this.watchdogTimeout != 0) {
                    byte exitCode;
                    if (!this.isRunning()) {
                        exitCode = -2;
                        Debug.log(String.format("[%s%%] SHELL_DIED", this.shell.toUpperCase(Locale.ENGLISH)));
                    } else {
                        if (this.watchdogCount++ < this.watchdogTimeout) {
                            return;
                        }

                        exitCode = -1;
                        Debug.log(String.format("[%s%%] WATCHDOG_EXIT", this.shell.toUpperCase(Locale.ENGLISH)));
                    }

                    this.postCallback(this.command, exitCode, this.buffer);
                    this.command = null;
                    this.buffer = null;
                    this.idle = true;
                    this.watchdog.shutdown();
                    this.watchdog = null;
                    this.kill();
                }
            }
        }

        private void startWatchdog() {
            if (this.watchdogTimeout != 0) {
                this.watchdogCount = 0;
                this.watchdog = new ScheduledThreadPoolExecutor(1);
                this.watchdog.scheduleAtFixedRate(new Runnable() {
                    public void run() {
                        Interactive.this.handleWatchdog();
                    }
                }, 1L, 1L, TimeUnit.SECONDS);
            }
        }

        private void stopWatchdog() {
            if (this.watchdog != null) {
                this.watchdog.shutdownNow();
                this.watchdog = null;
            }

        }

        private void runNextCommand(boolean notifyIdle) {
            boolean running = this.isRunning();
            if (!running) {
                this.idle = true;
            }

            if (running && this.idle && this.commands.size() > 0) {
                Command command = (Command)this.commands.get(0);
                this.commands.remove(0);
                this.buffer = null;
                this.lastExitCode = 0;
                this.lastMarkerSTDOUT = null;
                this.lastMarkerSTDERR = null;
                if (command.commands.length > 0) {
                    try {
                        if (command.onCommandResultListener != null) {
                            this.buffer = Collections.synchronizedList(new ArrayList());
                        }

                        this.idle = false;
                        this.command = command;
                        this.startWatchdog();
                        String[] var4 = command.commands;
                        int var5 = var4.length;

                        for(int var6 = 0; var6 < var5; ++var6) {
                            String write = var4[var6];
                            Debug.logCommand(String.format("[%s+] %s", this.shell.toUpperCase(Locale.ENGLISH), write));
                            this.STDIN.write((write + "\n").getBytes("UTF-8"));
                        }

                        this.STDIN.write(("echo " + command.marker + " $?\n").getBytes("UTF-8"));
                        this.STDIN.write(("echo " + command.marker + " >&2\n").getBytes("UTF-8"));
                        this.STDIN.flush();
                    } catch (IOException var10) {
                    }
                } else {
                    this.runNextCommand(false);
                }
            } else if (!running) {
                while(this.commands.size() > 0) {
                    this.postCallback((Command)this.commands.remove(0), -2, (List)null);
                }
            }

            if (this.idle && notifyIdle) {
                synchronized(this.idleSync) {
                    this.idleSync.notifyAll();
                }
            }

        }

        private synchronized void processMarker() {
            if (this.command.marker.equals(this.lastMarkerSTDOUT) && this.command.marker.equals(this.lastMarkerSTDERR)) {
                this.postCallback(this.command, this.lastExitCode, this.buffer);
                this.stopWatchdog();
                this.command = null;
                this.buffer = null;
                this.idle = true;
                this.runNextCommand();
            }

        }

        private synchronized void processLine(final String line, final StreamGobbler.OnLineListener listener) {
            if (listener != null) {
                if (this.handler != null) {
                    this.startCallback();
                    this.handler.post(new Runnable() {
                        public void run() {
                            try {
                                listener.onLine(line);
                            } finally {
                                Interactive.this.endCallback();
                            }

                        }
                    });
                } else {
                    listener.onLine(line);
                }
            }

        }

        private synchronized void addBuffer(String line) {
            if (this.buffer != null) {
                this.buffer.add(line);
            }

        }

        private void startCallback() {
            synchronized(this.callbackSync) {
                ++this.callbacks;
            }
        }

        private void postCallback(final Command fCommand, final int fExitCode, final List<String> fOutput) {
            if (fCommand.onCommandResultListener != null || fCommand.onCommandLineListener != null) {
                if (this.handler == null) {
                    if (fCommand.onCommandResultListener != null) {
                        fCommand.onCommandResultListener.onCommandResult(fCommand.code, fExitCode, fOutput);
                    }

                    if (fCommand.onCommandLineListener != null) {
                        fCommand.onCommandLineListener.onCommandResult(fCommand.code, fExitCode);
                    }

                } else {
                    this.startCallback();
                    this.handler.post(new Runnable() {
                        public void run() {
                            try {
                                if (fCommand.onCommandResultListener != null) {
                                    fCommand.onCommandResultListener.onCommandResult(fCommand.code, fExitCode, fOutput);
                                }

                                if (fCommand.onCommandLineListener != null) {
                                    fCommand.onCommandLineListener.onCommandResult(fCommand.code, fExitCode);
                                }
                            } finally {
                                Interactive.this.endCallback();
                            }

                        }
                    });
                }
            }
        }

        private void endCallback() {
            synchronized(this.callbackSync) {
                --this.callbacks;
                if (this.callbacks == 0) {
                    this.callbackSync.notifyAll();
                }

            }
        }

        private synchronized boolean open() {
            Debug.log(String.format("[%s%%] START", this.shell.toUpperCase(Locale.ENGLISH)));

            try {
                if (this.environment.size() == 0) {
                    this.process = Runtime.getRuntime().exec(this.shell);
                } else {
                    Map<String, String> newEnvironment = new HashMap();
                    newEnvironment.putAll(System.getenv());
                    newEnvironment.putAll(this.environment);
                    int i = 0;
                    String[] env = new String[newEnvironment.size()];

                    for(Iterator var4 = newEnvironment.entrySet().iterator(); var4.hasNext(); ++i) {
                        Entry<String, String> entry = (Entry)var4.next();
                        env[i] = (String)entry.getKey() + "=" + (String)entry.getValue();
                    }

                    this.process = Runtime.getRuntime().exec(this.shell, env);
                }

                this.STDIN = new DataOutputStream(this.process.getOutputStream());
                this.STDOUT = new StreamGobbler(this.shell.toUpperCase(Locale.ENGLISH) + "-", this.process.getInputStream(), new StreamGobbler.OnLineListener() {
                    public void onLine(String line) {
                        synchronized(Interactive.this) {
                            if (Interactive.this.command != null) {
                                String contentPart = line;
                                String markerPart = null;
                                int markerIndex = line.indexOf(Interactive.this.command.marker);
                                if (markerIndex == 0) {
                                    contentPart = null;
                                    markerPart = line;
                                } else if (markerIndex > 0) {
                                    contentPart = line.substring(0, markerIndex);
                                    markerPart = line.substring(markerIndex);
                                }

                                if (contentPart != null) {
                                    Interactive.this.addBuffer(contentPart);
                                    Interactive.this.processLine(contentPart, Interactive.this.onSTDOUTLineListener);
                                    Interactive.this.processLine(contentPart, Interactive.this.command.onCommandLineListener);
                                }

                                if (markerPart != null) {
                                    try {
                                        Interactive.this.lastExitCode = Integer.valueOf(markerPart.substring(Interactive.this.command.marker.length() + 1), 10);
                                    } catch (Exception var8) {
                                        var8.printStackTrace();
                                    }

                                    Interactive.this.lastMarkerSTDOUT = Interactive.this.command.marker;
                                    Interactive.this.processMarker();
                                }

                            }
                        }
                    }
                });
                this.STDERR = new StreamGobbler(this.shell.toUpperCase(Locale.ENGLISH) + "*", this.process.getErrorStream(), new StreamGobbler.OnLineListener() {
                    public void onLine(String line) {
                        synchronized(Interactive.this) {
                            if (Interactive.this.command != null) {
                                String contentPart = line;
                                int markerIndex = line.indexOf(Interactive.this.command.marker);
                                if (markerIndex == 0) {
                                    contentPart = null;
                                } else if (markerIndex > 0) {
                                    contentPart = line.substring(0, markerIndex);
                                }

                                if (contentPart != null) {
                                    if (Interactive.this.wantSTDERR) {
                                        Interactive.this.addBuffer(contentPart);
                                    }

                                    Interactive.this.processLine(contentPart, Interactive.this.onSTDERRLineListener);
                                }

                                if (markerIndex >= 0) {
                                    Interactive.this.lastMarkerSTDERR = Interactive.this.command.marker;
                                    Interactive.this.processMarker();
                                }

                            }
                        }
                    }
                });
                this.STDOUT.start();
                this.STDERR.start();
                this.running = true;
                this.closed = false;
                this.runNextCommand();
                return true;
            } catch (IOException var6) {
                return false;
            }
        }

        public void close() {
            boolean _idle = this.isIdle();
            synchronized(this) {
                if (!this.running) {
                    return;
                }

                this.running = false;
                this.closed = true;
            }

            if (!_idle && Debug.getSanityChecksEnabledEffective() && Debug.onMainThread()) {
                Debug.log("Application attempted to wait for a non-idle shell to close on the main thread");
                throw new ShellOnMainThreadException("Application attempted to wait for a non-idle shell to close on the main thread");
            } else {
                if (!_idle) {
                    this.waitForIdle();
                }

                try {
                    try {
                        this.STDIN.write("exit\n".getBytes("UTF-8"));
                        this.STDIN.flush();
                    } catch (IOException var5) {
                        if (!var5.getMessage().contains("EPIPE")) {
                            throw var5;
                        }
                    }

                    this.process.waitFor();

                    try {
                        this.STDIN.close();
                    } catch (IOException var4) {
                    }

                    this.STDOUT.join();
                    this.STDERR.join();
                    this.stopWatchdog();
                    this.process.destroy();
                } catch (IOException var6) {
                } catch (InterruptedException var7) {
                }

                Debug.log(String.format("[%s%%] END", this.shell.toUpperCase(Locale.ENGLISH)));
            }
        }

        public synchronized void kill() {
            this.running = false;
            this.closed = true;

            try {
                this.STDIN.close();
            } catch (IOException var5) {
            }

            try {
                this.process.destroy();
            } catch (Exception var4) {
            }

            this.idle = true;
            synchronized(this.idleSync) {
                this.idleSync.notifyAll();
            }
        }

        public boolean isRunning() {
            if (this.process == null) {
                return false;
            } else {
                try {
                    this.process.exitValue();
                    return false;
                } catch (IllegalThreadStateException var2) {
                    return true;
                }
            }
        }

        public synchronized boolean isIdle() {
            if (!this.isRunning()) {
                this.idle = true;
                synchronized(this.idleSync) {
                    this.idleSync.notifyAll();
                }
            }

            return this.idle;
        }

        public boolean waitForIdle() {
            if (Debug.getSanityChecksEnabledEffective() && Debug.onMainThread()) {
                Debug.log("Application attempted to wait for a shell to become idle on the main thread");
                throw new ShellOnMainThreadException("Application attempted to wait for a shell to become idle on the main thread");
            } else {
                if (this.isRunning()) {
                    synchronized(this.idleSync) {
                        while(!this.idle) {
                            try {
                                this.idleSync.wait();
                            } catch (InterruptedException var6) {
                                return false;
                            }
                        }
                    }

                    if (this.handler != null && this.handler.getLooper() != null && this.handler.getLooper() != Looper.myLooper()) {
                        synchronized(this.callbackSync) {
                            while(this.callbacks > 0) {
                                try {
                                    this.callbackSync.wait();
                                } catch (InterruptedException var5) {
                                    return false;
                                }
                            }
                        }
                    }
                }

                return true;
            }
        }

        public boolean hasHandler() {
            return this.handler != null;
        }
    }

    public static class Builder {
        private Handler handler = null;
        private boolean autoHandler = true;
        private String shell = "sh";
        private boolean wantSTDERR = false;
        private List<Command> commands = new LinkedList();
        private Map<String, String> environment = new HashMap();
        private StreamGobbler.OnLineListener onSTDOUTLineListener = null;
        private StreamGobbler.OnLineListener onSTDERRLineListener = null;
        private int watchdogTimeout = 0;

        public Builder() {
        }

        public Builder setHandler(Handler handler) {
            this.handler = handler;
            return this;
        }

        public Builder setAutoHandler(boolean autoHandler) {
            this.autoHandler = autoHandler;
            return this;
        }

        public Builder setShell(String shell) {
            this.shell = shell;
            return this;
        }

        public Builder useSH() {
            return this.setShell("sh");
        }

        public Builder useSU() {
            return this.setShell("su");
        }

        public Builder setWantSTDERR(boolean wantSTDERR) {
            this.wantSTDERR = wantSTDERR;
            return this;
        }

        public Builder addEnvironment(String key, String value) {
            this.environment.put(key, value);
            return this;
        }

        public Builder addEnvironment(Map<String, String> addEnvironment) {
            this.environment.putAll(addEnvironment);
            return this;
        }

        public Builder addCommand(String command) {
            return this.addCommand((String)command, 0, (OnCommandResultListener)null);
        }

        public Builder addCommand(String command, int code, OnCommandResultListener onCommandResultListener) {
            return this.addCommand(new String[]{command}, code, onCommandResultListener);
        }

        public Builder addCommand(List<String> commands) {
            return this.addCommand((List)commands, 0, (OnCommandResultListener)null);
        }

        public Builder addCommand(List<String> commands, int code, OnCommandResultListener onCommandResultListener) {
            return this.addCommand((String[])commands.toArray(new String[commands.size()]), code, onCommandResultListener);
        }

        public Builder addCommand(String[] commands) {
            return this.addCommand((String[])commands, 0, (OnCommandResultListener)null);
        }

        public Builder addCommand(String[] commands, int code, OnCommandResultListener onCommandResultListener) {
            this.commands.add(new Command(commands, code, onCommandResultListener, (OnCommandLineListener)null));
            return this;
        }

        public Builder setOnSTDOUTLineListener(StreamGobbler.OnLineListener onLineListener) {
            this.onSTDOUTLineListener = onLineListener;
            return this;
        }

        public Builder setOnSTDERRLineListener(StreamGobbler.OnLineListener onLineListener) {
            this.onSTDERRLineListener = onLineListener;
            return this;
        }

        public Builder setWatchdogTimeout(int watchdogTimeout) {
            this.watchdogTimeout = watchdogTimeout;
            return this;
        }

        public Builder setMinimalLogging(boolean useMinimal) {
            Debug.setLogTypeEnabled(6, !useMinimal);
            return this;
        }

        public Interactive open() {
            return new Interactive(this, (OnCommandResultListener)null);
        }

        public Interactive open(OnCommandResultListener onCommandResultListener) {
            return new Interactive(this, onCommandResultListener);
        }
    }

    private static class Command {
        private static int commandCounter = 0;
        private final String[] commands;
        private final int code;
        private final OnCommandResultListener onCommandResultListener;
        private final OnCommandLineListener onCommandLineListener;
        private final String marker;

        public Command(String[] commands, int code, OnCommandResultListener onCommandResultListener, OnCommandLineListener onCommandLineListener) {
            this.commands = commands;
            this.code = code;
            this.onCommandResultListener = onCommandResultListener;
            this.onCommandLineListener = onCommandLineListener;
            this.marker = UUID.randomUUID().toString() + String.format("-%08x", ++commandCounter);
        }
    }

    public interface OnCommandLineListener extends OnResult, StreamGobbler.OnLineListener {
        void onCommandResult(int var1, int var2);
    }

    public interface OnCommandResultListener extends OnResult {
        void onCommandResult(int var1, int var2, List<String> var3);
    }

    private interface OnResult {
        int WATCHDOG_EXIT = -1;
        int SHELL_DIED = -2;
        int SHELL_EXEC_FAILED = -3;
        int SHELL_WRONG_UID = -4;
        int SHELL_RUNNING = 0;
    }

    public static class SU {
        private static Boolean isSELinuxEnforcing = null;
        private static String[] suVersion = new String[]{null, null};

        public SU() {
        }

        public static List<String> run(String command) {
            return Shell.run("su", new String[]{command}, (String[])null, false);
        }

        public static List<String> run(List<String> commands) {
            return Shell.run("su", (String[])commands.toArray(new String[commands.size()]), (String[])null, false);
        }

        public static List<String> run(String[] commands) {
            return Shell.run("su", commands, (String[])null, false);
        }

        public static boolean available() {
            List<String> ret = run(Shell.availableTestCommands);
            return Shell.parseAvailableResult(ret, true);
        }

        public static synchronized String version(boolean internal) {
            int idx = internal ? 0 : 1;
            if (suVersion[idx] == null) {
                String version = null;
                List<String> ret = Shell.run(internal ? "su -V" : "su -v", new String[]{"exit"}, (String[])null, false);
                if (ret != null) {
                    Iterator var4 = ret.iterator();

                    while(var4.hasNext()) {
                        String line = (String)var4.next();
                        if (!internal) {
                            if (!line.trim().equals("")) {
                                version = line;
                                break;
                            }
                        } else {
                            try {
                                if (Integer.parseInt(line) > 0) {
                                    version = line;
                                    break;
                                }
                            } catch (NumberFormatException var7) {
                            }
                        }
                    }
                }

                suVersion[idx] = version;
            }

            return suVersion[idx];
        }

        public static boolean isSU(String shell) {
            int pos = shell.indexOf(32);
            if (pos >= 0) {
                shell = shell.substring(0, pos);
            }

            pos = shell.lastIndexOf(47);
            if (pos >= 0) {
                shell = shell.substring(pos + 1);
            }

            return shell.equals("su");
        }

        public static String shell(int uid, String context) {
            String shell = "su";
            if (context != null && isSELinuxEnforcing()) {
                String display = version(false);
                String internal = version(true);
                if (display != null && internal != null && display.endsWith("SUPERSU") && Integer.valueOf(internal) >= 190) {
                    shell = String.format(Locale.ENGLISH, "%s --context %s", shell, context);
                }
            }

            if (uid > 0) {
                shell = String.format(Locale.ENGLISH, "%s %d", shell, uid);
            }

            return shell;
        }

        public static String shellMountMaster() {
            return VERSION.SDK_INT >= 17 ? "su --mount-master" : "su";
        }

        public static synchronized boolean isSELinuxEnforcing() {
            if (isSELinuxEnforcing == null) {
                Boolean enforcing = null;
                if (VERSION.SDK_INT >= 17) {
                    File f = new File("/sys/fs/selinux/enforce");
                    if (f.exists()) {
                        try {
                            FileInputStream is = new FileInputStream("/sys/fs/selinux/enforce");

                            try {
                                enforcing = is.read() == 49;
                            } finally {
                                is.close();
                            }
                        } catch (Exception var8) {
                        }
                    }

                    if (enforcing == null) {
                        try {
                            Class seLinux = Class.forName("android.os.SELinux");
                            Method isSELinuxEnforced = seLinux.getMethod("isSELinuxEnforced");
                            enforcing = (Boolean)isSELinuxEnforced.invoke(seLinux.newInstance());
                        } catch (Exception var9) {
                            enforcing = VERSION.SDK_INT >= 19;
                        }
                    }
                }

                if (enforcing == null) {
                    enforcing = false;
                }

                isSELinuxEnforcing = enforcing;
            }

            return isSELinuxEnforcing;
        }

        public static synchronized void clearCachedResults() {
            isSELinuxEnforcing = null;
            suVersion[0] = null;
            suVersion[1] = null;
        }
    }

    public static class SH {
        public SH() {
        }

        public static List<String> run(String command) {
            return Shell.run("sh", new String[]{command}, (String[])null, false);
        }

        public static List<String> run(List<String> commands) {
            return Shell.run("sh", (String[])commands.toArray(new String[commands.size()]), (String[])null, false);
        }

        public static List<String> run(String[] commands) {
            return Shell.run("sh", commands, (String[])null, false);
        }
    }
}
