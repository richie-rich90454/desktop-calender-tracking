package app;
/*
 * Application entry point (wrapper around AppLauncher).
 *
 * Responsibilities:
 * - Contain the public static void main(String[] args)
 * - Delegate startup to AppLauncher
 *
 * Java data types used:
 * - String[]
 *
 * Java technologies involved:
 * - JVM entry point
 *
 * Design intent:
 * Keeps startup logic separate from application wiring.
 * Makes future testing and alternate launch modes easier.
 */
public class Main {
    public static void main(String[] args){
        AppLauncher launcher=new AppLauncher();
        if (args.length>0){
            String storagePath=args[0];
            System.out.println("Using custom storage path: " + storagePath);
            launcher.launch(storagePath);
        }
        else{
            launcher.launch();
        }
    }
    public static AppLauncher getLauncherForTesting(){
        return new AppLauncher();
    }
    public static CalendarController launchHeadless(){
        AppLauncher launcher=new AppLauncher();
        return launcher.launchHeadless();
    }
}