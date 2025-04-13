package com.jsql.model.injection.strategy.blind;

import com.jsql.model.InjectionModel;
import com.jsql.model.accessible.DataAccess;
import com.jsql.model.bean.util.Interaction;
import com.jsql.model.bean.util.Request;
import com.jsql.model.exception.InjectionFailureException;
import com.jsql.model.exception.StoppedByUserSlidingException;
import com.jsql.model.suspendable.AbstractSuspendable;
import com.jsql.util.LogLevelUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractInjectionBinary<T extends AbstractCallableBinary<T>> {
    
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = LogManager.getRootLogger();

    // Every FALSE SQL statements will be checked,
    // more statements means a more robust application
    protected List<String> falsy;
    protected List<String> falsyBinary;

    // Every TRUE SQL statements will be checked,
    // more statements means a more robust application
    protected List<String> truthy;
    protected List<String> truthyBinary;

    public enum BinaryMode {
        AND, OR, STACK, NO_MODE
    }
    
    protected final InjectionModel injectionModel;
    
    protected final BinaryMode binaryMode;
    
    protected AbstractInjectionBinary(InjectionModel injectionModel, BinaryMode binaryMode) {
        this.injectionModel = injectionModel;
        this.binaryMode = binaryMode;
        this.falsy = this.injectionModel.getMediatorVendor().getVendor().instance().getFalsy();
        this.truthy = this.injectionModel.getMediatorVendor().getVendor().instance().getTruthy();
        this.falsyBinary = this.injectionModel.getMediatorVendor().getVendor().instance().getFalsyBinary();
        this.truthyBinary = this.injectionModel.getMediatorVendor().getVendor().instance().getTruthyBinary();
    }

    /**
     * Start one test to verify if boolean works.
     * @return true if boolean method is confirmed
     */
    public abstract boolean isInjectable() throws StoppedByUserSlidingException;

    public abstract void initNextChars(
        String sqlQuery,
        List<char[]> bytes,
        AtomicInteger indexCharacter,
        CompletionService<T> taskCompletionService,
        AtomicInteger countTasksSubmitted,
        T currentCallable  // required by sequential calls like binary search
    );

    public abstract char[] initBinaryMask(List<char[]> bytes, T currentCallable);

    /**
     * Display a message to explain how is blind/time working.
     */
    public abstract String getInfoMessage();

    /**
     * Process the whole boolean injection, character by character, bit by bit.
     * @param sqlQuery SQL query
     * @param suspendable Action a user can stop
     * @return Final string: SQLiABCDEF...
     */
    public String inject(String sqlQuery, AbstractSuspendable suspendable) throws StoppedByUserSlidingException {
        // List of the characters, each one represented by an array of 8 bits
        // e.g. SQLi: bytes[0] => 01010011:S, bytes[1] => 01010001:Q ...
        List<char[]> bytes = new ArrayList<>();
        var indexCharacter = new AtomicInteger(0);  // current char position

        // Concurrent URL requests
        ExecutorService taskExecutor = this.injectionModel.getMediatorUtils().getThreadUtil().getExecutor("CallableAbstractBoolean");
        CompletionService<T> taskCompletionService = new ExecutorCompletionService<>(taskExecutor);

        var countTasksSubmitted = new AtomicInteger(0);
        var countBadAsciiCode = new AtomicInteger(0);

        this.initNextChars(sqlQuery, bytes, indexCharacter, taskCompletionService, countTasksSubmitted, null);

        // Process the job until there is no more active task,
        // in other word until all HTTP requests are done
        while (countTasksSubmitted.get() > 0) {
            if (suspendable.isSuspended()) {
                String result = this.stop(bytes, taskExecutor);
                throw new StoppedByUserSlidingException(result);
            }
            
            try {
                var currentCallable = taskCompletionService.take().get();  // URL call done
                countTasksSubmitted.decrementAndGet();  // one task just ended
                
                // If SQL result is not empty, then add a new unknown character and define a new array of 7 undefined bit.
                // Then add 7 bits requests for that new character.
                var isComplete = this.injectCharacter(bytes, countTasksSubmitted, countBadAsciiCode, currentCallable);
                if (isComplete || currentCallable.isBinary()) {  // prevents bitwise overload new char init on each bit
                    this.initNextChars(sqlQuery, bytes, indexCharacter, taskCompletionService, countTasksSubmitted, currentCallable);
                }

                String result = AbstractInjectionBinary.convert(bytes);
                if (result.matches("(?s).*"+ DataAccess.TRAIL_RGX +".*")) {
                    countTasksSubmitted.set(0);
                    break;
                }
            } catch (InterruptedException e) {
                LOGGER.log(LogLevelUtil.IGNORE, e, e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
            } catch (InjectionFailureException e) {
                LOGGER.log(LogLevelUtil.CONSOLE_ERROR, e.getMessage());
                break;
            }
        }
        return this.stop(bytes, taskExecutor);
    }

    private static String convert(List<char[]> bytes) {
        var result = new StringBuilder();
        for (char[] c: bytes) {
            try {
                var charCode = Integer.parseInt(new String(c), 2);
                var str = Character.toString((char) charCode);
                result.append(str);
            } catch (NumberFormatException err) {
                // Byte string not fully constructed : 0x1x010x
                // Ignore
            }
        }
        return result.toString();
    }

    protected boolean injectCharacter(List<char[]> bytes, AtomicInteger countTasksSubmitted, AtomicInteger countBadAsciiCode, T currentCallable) throws InjectionFailureException {
        // Process url that has just checked one bit, convert bits to chars,
        // and change current bit from undefined to 0 or 1
        char[] asciiCodeMask = this.initBinaryMask(bytes, currentCallable);
        var asciiCodeBinary = new String(asciiCodeMask);
        var isComplete = false;

        // Inform the View if bits array is complete, else nothing #Need fix
        if (asciiCodeBinary.matches("^[01]{8}$")) {
            var asciiCode = Integer.parseInt(asciiCodeBinary, 2);
            if (asciiCode == 127 || asciiCode == 0) {  // Stop if many 11111111, 01111111 or 00000000
                if (countTasksSubmitted.get() != 0 && countBadAsciiCode.get() > 15) {
                    throw new InjectionFailureException("Boolean false positive, stopping...");
                }
                countBadAsciiCode.incrementAndGet();
            }

            currentCallable.charText = Character.toString((char) asciiCode);
            
            var interaction = new Request();
            interaction.setMessage(Interaction.MESSAGE_BINARY);
            interaction.setParameters(
                asciiCodeBinary
                + "="
                + currentCallable.charText
                .replace("\\n", "\\\\\\n")
                .replace("\\r", "\\\\\\r")
                .replace("\\t", "\\\\\\t")
            );
            this.injectionModel.sendToViews(interaction);
            isComplete = true;
        }
        return isComplete;
    }

    private String stop(List<char[]> bytes, ExecutorService taskExecutor) {
        this.injectionModel.getMediatorUtils().getThreadUtil().shutdown(taskExecutor);

        // Get current progress and display
        var result = new StringBuilder();
        
        for (char[] c: bytes) {
            try {
                var charCode = Integer.parseInt(new String(c), 2);
                var str = Character.toString((char) charCode);
                result.append(str);
                
            } catch (NumberFormatException err) {
                // Byte string not fully constructed : 0x1x010x
                // Ignore
            }
        }
        return result.toString();
    }

    /**
     * Run a HTTP call via the model.
     * @param urlString URL to inject
     * @return Source code
     */
    public String callUrl(String urlString, String metadataInjectionProcess) {
        return this.injectionModel.injectWithoutIndex(urlString, metadataInjectionProcess);
    }

    public String callUrl(String urlString, String metadataInjectionProcess, AbstractCallableBinary<?> callableBoolean) {
        return this.injectionModel.injectWithoutIndex(urlString, metadataInjectionProcess, callableBoolean);
    }

    public BinaryMode getBooleanMode() {
        return this.binaryMode;
    }
}
