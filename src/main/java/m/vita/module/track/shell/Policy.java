package m.vita.module.track.shell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class Policy {
    private static final int MAX_POLICY_LENGTH = 4064;
    private static final Object synchronizer = new Object();
    private static volatile Boolean canInject = null;
    private static volatile boolean injected = false;

    public Policy() {
    }

    public static boolean haveInjected() {
        return injected;
    }

    public static void resetInjected() {
        synchronized(synchronizer) {
            injected = false;
        }
    }

    protected abstract String[] getPolicies();

    public static boolean canInject() {
        synchronized(synchronizer) {
            if (canInject != null) {
                return canInject;
            } else {
                canInject = false;
                List<String> result = Shell.run("sh", new String[]{"supolicy"}, (String[])null, false);
                if (result != null) {
                    Iterator var2 = result.iterator();

                    while(var2.hasNext()) {
                        String line = (String)var2.next();
                        if (line.contains("supolicy")) {
                            canInject = true;
                            break;
                        }
                    }
                }

                return canInject;
            }
        }
    }

    public static void resetCanInject() {
        synchronized(synchronizer) {
            canInject = null;
        }
    }

    protected List<String> getInjectCommands() {
        return this.getInjectCommands(true);
    }

    protected List<String> getInjectCommands(boolean allowBlocking) {
        synchronized(synchronizer) {
            if (!Shell.SU.isSELinuxEnforcing()) {
                return null;
            } else if (allowBlocking && !canInject()) {
                return null;
            } else if (injected) {
                return null;
            } else {
                String[] policies = this.getPolicies();
                if (policies != null && policies.length > 0) {
                    List<String> commands = new ArrayList();
                    String command = "";
                    String[] var6 = policies;
                    int var7 = policies.length;

                    for(int var8 = 0; var8 < var7; ++var8) {
                        String policy = var6[var8];
                        if (command.length() != 0 && command.length() + policy.length() + 3 >= 4064) {
                            commands.add("supolicy --live" + command);
                            command = "";
                        } else {
                            command = command + " \"" + policy + "\"";
                        }
                    }

                    if (command.length() > 0) {
                        commands.add("supolicy --live" + command);
                    }

                    return commands;
                } else {
                    return null;
                }
            }
        }
    }

    public void inject() {
        synchronized(synchronizer) {
            List<String> commands = this.getInjectCommands();
            if (commands != null && commands.size() > 0) {
                Shell.SU.run(commands);
            }

            injected = true;
        }
    }

    public void inject(Shell.Interactive shell, boolean waitForIdle) {
        synchronized(synchronizer) {
            List<String> commands = this.getInjectCommands(waitForIdle);
            if (commands != null && commands.size() > 0) {
                shell.addCommand(commands);
                if (waitForIdle) {
                    shell.waitForIdle();
                }
            }

            injected = true;
        }
    }
}
