package rt.quickrpc.dgram.test;


import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class Foo {
    public static void main(String[] args) {
        //
        // Obtains Logger instance
        //
      //  Logger logger = Logger.getLogger(Foo.class.getName());
    	Logger logger = Logger.getLogger("");

        //
        // Add ConsoleHandler to Logger.
        //
        ConsoleHandler consoleHandler = new ConsoleHandler();
        logger.addHandler(consoleHandler);

        if (logger.isLoggable(Level.INFO)) {
            logger.info("This is information message for testing ConsoleHandler");
        }
    }
}
